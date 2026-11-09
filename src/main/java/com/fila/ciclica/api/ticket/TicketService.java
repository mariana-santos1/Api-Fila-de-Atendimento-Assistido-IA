package com.fila.ciclica.api.ticket;

import com.fila.ciclica.common.ApiException;
import com.fila.ciclica.common.PageResponse;
import com.fila.ciclica.domain.Cycle;
import com.fila.ciclica.domain.CycleLifecycleService;
import com.fila.ciclica.domain.Priority;
import com.fila.ciclica.domain.Queue;
import com.fila.ciclica.domain.Ticket;
import com.fila.ciclica.domain.TicketStatus;
import com.fila.ciclica.observability.TicketTransitionLogger;
import com.fila.ciclica.repository.CycleRepository;
import com.fila.ciclica.repository.QueueRepository;
import com.fila.ciclica.repository.TicketRepository;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TicketService {

    private final QueueRepository queueRepository;
    private final CycleRepository cycleRepository;
    private final TicketRepository ticketRepository;
    private final CycleLifecycleService cycleLifecycleService;
    private final TicketTransitionLogger transitionLogger;

    public TicketService(
            QueueRepository queueRepository,
            CycleRepository cycleRepository,
            TicketRepository ticketRepository,
            CycleLifecycleService cycleLifecycleService,
            TicketTransitionLogger transitionLogger) {
        this.queueRepository = queueRepository;
        this.cycleRepository = cycleRepository;
        this.ticketRepository = ticketRepository;
        this.cycleLifecycleService = cycleLifecycleService;
        this.transitionLogger = transitionLogger;
    }

    @Transactional
    public TicketCreationResult create(Long queueId, CreateTicketRequest request) {
        Queue queue = queueRepository.findByIdForUpdate(queueId)
                .orElseThrow(this::queueNotFound);

        String externalReference = trimToNull(request.externalReference());
        if (externalReference != null) {
            Optional<Ticket> existing = ticketRepository
                    .findByQueueIdAndExternalReference(queueId, externalReference);
            if (existing.isPresent()) {
                return new TicketCreationResult(TicketResponse.from(existing.get()), false);
            }
        }

        if (!queue.isActive()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "QUEUE_INACTIVE",
                    "Inactive queues cannot receive tickets");
        }

        Cycle cycle = cycleRepository.findByActiveQueueId(queueId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.CONFLICT,
                        "QUEUE_INACTIVE",
                        "Queue has no active cycle"));

        Priority priority = parsePriority(request.priority(), true);
        Instant now = Instant.now();

        long counter = queue.getTicketCounter() + 1;
        queue.setTicketCounter(counter);
        queue.setUpdatedAt(now);
        queueRepository.save(queue);

        Ticket ticket = new Ticket();
        ticket.setPublicId(UUID.randomUUID().toString());
        ticket.setQueue(queue);
        ticket.setCycle(cycle);
        ticket.setCode(queue.getPrefix() + "-" + String.format(Locale.ROOT, "%04d", counter));
        ticket.setCustomerName(request.customerName().trim());
        ticket.setPriority(priority);
        ticket.setStatus(TicketStatus.WAITING);
        ticket.setCallsInCycle(0);
        ticket.setCycleWeight(queue.weightFor(priority));
        ticket.setExternalReference(externalReference);
        ticket.setCreatedAt(now);
        ticket.setUpdatedAt(now);

        ticket = ticketRepository.saveAndFlush(ticket);
        return new TicketCreationResult(TicketResponse.from(ticket), true);
    }

    @Transactional(readOnly = true)
    public PageResponse<TicketResponse> list(
            Long queueId, String status, String priority, int page, int size) {
        if (!queueRepository.existsById(queueId)) {
            throw queueNotFound();
        }
        validatePagination(page, size);

        TicketStatus statusFilter = parseStatus(status);
        Priority priorityFilter = parsePriorityFilter(priority);

        PageRequest pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.ASC, "createdAt").and(Sort.by(Sort.Direction.ASC, "id")));

        Page<Ticket> result;
        if (statusFilter != null && priorityFilter != null) {
            result = ticketRepository.findByQueueIdAndStatusAndPriority(
                    queueId, statusFilter, priorityFilter, pageable);
        } else if (statusFilter != null) {
            result = ticketRepository.findByQueueIdAndStatus(queueId, statusFilter, pageable);
        } else if (priorityFilter != null) {
            result = ticketRepository.findByQueueIdAndPriority(queueId, priorityFilter, pageable);
        } else {
            result = ticketRepository.findByQueueId(queueId, pageable);
        }

        return PageResponse.of(result.map(TicketResponse::from));
    }

    @Transactional(readOnly = true)
    public TicketResponse getByPublicId(String publicId) {
        return TicketResponse.from(ticketRepository.findByPublicId(publicId)
                .orElseThrow(this::ticketNotFound));
    }

    @Transactional
    public TicketResponse changePriority(String publicId, PriorityChangeRequest request) {
        Ticket ticket = ticketRepository.findByPublicId(publicId)
                .orElseThrow(this::ticketNotFound);

        if (ticket.getStatus() != TicketStatus.WAITING || ticket.getCallsInCycle() > 0) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "PRIORITY_LOCKED",
                    "Priority can only change while the ticket is waiting and was never called");
        }

        Priority priority = parsePriority(request.priority(), false);
        ticket.setPriority(priority);
        ticket.setCycleWeight(ticket.getQueue().weightFor(priority));
        ticket.setUpdatedAt(Instant.now());

        return TicketResponse.from(ticketRepository.saveAndFlush(ticket));
    }

    @Transactional
    public void cancel(String publicId) {        Ticket ticket = ticketRepository.findByPublicId(publicId)
                .orElseThrow(this::ticketNotFound);

        if (ticket.getStatus() != TicketStatus.WAITING
                && ticket.getStatus() != TicketStatus.CALLED) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "INVALID_STATE",
                    "Only waiting or called tickets can be cancelled");
        }

        Instant now = Instant.now();
        TicketStatus previousStatus = ticket.getStatus();
        int cycleNumber = ticket.getCycle().getNumber();
        ticket.setStatus(TicketStatus.CANCELLED);
        ticket.setFinishedAt(now);
        ticket.setUpdatedAt(now);
        ticket = ticketRepository.saveAndFlush(ticket);
        transitionLogger.log(
                ticket.getQueue().getId(),
                ticket.getCode(),
                cycleNumber,
                previousStatus,
                TicketStatus.CANCELLED);

        cycleLifecycleService.closeIfEmpty(ticket.getCycle());
    }

    Priority parsePriority(String raw, boolean defaultNormal) {
        if (raw == null || raw.isBlank()) {
            if (defaultNormal) {
                return Priority.NORMAL;
            }
            throw invalidPriority();
        }
        try {
            return Priority.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw invalidPriority();
        }
    }

    Priority parsePriorityFilter(String raw) {
        return raw == null || raw.isBlank() ? null : parsePriority(raw, false);
    }

    TicketStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return TicketStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_STATUS",
                    "Status must be WAITING, CALLED, COMPLETED, NO_SHOW or CANCELLED");
        }
    }

    void validatePagination(int page, int size) {
        if (page < 0) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "INVALID_PAGINATION", "Page must be zero or greater");
        }
        if (size < 1 || size > 100) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "INVALID_PAGE_SIZE", "Size must be between 1 and 100");
        }
    }

    String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    ApiException queueNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "QUEUE_NOT_FOUND", "Queue not found");
    }

    ApiException ticketNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "TICKET_NOT_FOUND", "Ticket not found");
    }

    ApiException invalidPriority() {
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                "INVALID_PRIORITY",
                "Priority must be NORMAL, PREFERENTIAL or URGENT");
    }
}
