package com.fila.ciclica.api.queue;

import com.fila.ciclica.common.ApiException;
import com.fila.ciclica.common.PageResponse;
import com.fila.ciclica.domain.Cycle;
import com.fila.ciclica.domain.CycleLifecycleService;
import com.fila.ciclica.domain.CycleStatus;
import com.fila.ciclica.domain.Queue;
import com.fila.ciclica.domain.Ticket;
import com.fila.ciclica.domain.TicketStatus;
import com.fila.ciclica.api.ticket.TicketResponse;
import com.fila.ciclica.repository.CycleRepository;
import com.fila.ciclica.repository.QueueRepository;
import com.fila.ciclica.repository.TicketRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QueueService {

    private static final List<TicketStatus> ACTIVE_TICKET_STATUSES =
            List.of(TicketStatus.WAITING, TicketStatus.CALLED);

    private final QueueRepository queueRepository;
    private final CycleRepository cycleRepository;
    private final TicketRepository ticketRepository;
    private final CycleLifecycleService cycleLifecycleService;

    public QueueService(
            QueueRepository queueRepository,
            CycleRepository cycleRepository,
            TicketRepository ticketRepository,
            CycleLifecycleService cycleLifecycleService) {
        this.queueRepository = queueRepository;
        this.cycleRepository = cycleRepository;
        this.ticketRepository = ticketRepository;
        this.cycleLifecycleService = cycleLifecycleService;
    }

    @Transactional
    public QueueResponse create(CreateQueueRequest request) {
        validateWeights(request.weightNormal(), request.weightPreferential(), request.weightUrgent());

        String name = request.name().trim();
        String prefix = normalizePrefix(request.prefix());
        if (queueRepository.existsByName(name) || queueRepository.existsByPrefix(prefix)) {
            throw duplicateQueue();
        }

        Instant now = Instant.now();
        Queue queue = new Queue();
        queue.setName(name);
        queue.setPrefix(prefix);
        queue.setDescription(trimToNull(request.description()));
        queue.setActive(true);
        queue.setWeightNormal(request.weightNormal());
        queue.setWeightPreferential(request.weightPreferential());
        queue.setWeightUrgent(request.weightUrgent());
        queue.setTicketCounter(0);
        queue.setCreatedAt(now);
        queue.setUpdatedAt(now);

        try {
            queue = queueRepository.saveAndFlush(queue);
        } catch (DataIntegrityViolationException exception) {
            throw duplicateQueue();
        }

        Cycle cycle = new Cycle();
        cycle.setQueue(queue);
        cycle.setNumber(1);
        cycle.setStatus(CycleStatus.ACTIVE);
        cycle.setActiveQueueId(queue.getId());
        cycle.setStartedAt(now);
        cycle = cycleRepository.save(cycle);

        return QueueResponse.from(queue, cycle);
    }

    @Transactional
    public QueueResponse update(Long id, UpdateQueueRequest request) {
        Queue queue = findQueue(id);
        validateWeights(request.weightNormal(), request.weightPreferential(), request.weightUrgent());

        String name = request.name().trim();
        if (!name.equals(queue.getName()) && queueRepository.existsByName(name)) {
            throw duplicateQueue();
        }

        queue.setName(name);
        queue.setDescription(trimToNull(request.description()));
        queue.setWeightNormal(request.weightNormal());
        queue.setWeightPreferential(request.weightPreferential());
        queue.setWeightUrgent(request.weightUrgent());
        queue.setUpdatedAt(Instant.now());

        try {
            queue = queueRepository.saveAndFlush(queue);
        } catch (DataIntegrityViolationException exception) {
            throw duplicateQueue();
        }

        return QueueResponse.from(queue, activeCycle(queue.getId()));
    }

    @Transactional
    public void deactivate(Long id) {
        Queue queue = findQueue(id);

        if (ticketRepository.existsByQueueIdAndStatusIn(id, ACTIVE_TICKET_STATUSES)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "QUEUE_HAS_ACTIVE_TICKETS",
                    "Queue has waiting or called tickets and cannot be deactivated");
        }

        queue.setActive(false);
        queue.setUpdatedAt(Instant.now());
        queueRepository.save(queue);

        cycleRepository.findByActiveQueueId(id)
                .ifPresent(cycleLifecycleService::closeWithoutRollover);
    }

    @Transactional(readOnly = true)
    public PageResponse<QueueResponse> list(Boolean active, int page, int size) {
        validatePagination(page, size);

        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "name"));
        Page<Queue> result = active == null
                ? queueRepository.findAll(pageable)
                : queueRepository.findByActive(active, pageable);

        return PageResponse.of(result.map(queue -> QueueResponse.from(queue, activeCycle(queue.getId()))));
    }

    @Transactional(readOnly = true)
    public QueueResponse getById(Long id) {
        Queue queue = findQueue(id);
        return QueueResponse.from(queue, activeCycle(queue.getId()));
    }

    @Transactional(readOnly = true)
    public QueueStateResponse state(Long id) {
        Queue queue = findQueue(id);
        Cycle cycle = activeCycle(id);
        if (cycle == null) {
            return QueueStateResponse.empty(queue.getId(), queue.getName());
        }

        Ticket calledTicket = ticketRepository
                .findFirstByCycleIdAndStatus(cycle.getId(), TicketStatus.CALLED)
                .orElse(null);

        return new QueueStateResponse(
                queue.getId(),
                queue.getName(),
                cycle.getNumber(),
                ticketRepository.countByCycleIdAndStatus(cycle.getId(), TicketStatus.WAITING),
                ticketRepository.countByCycleIdAndStatus(cycle.getId(), TicketStatus.CALLED),
                ticketRepository.countByCycleIdAndStatus(cycle.getId(), TicketStatus.COMPLETED),
                ticketRepository.countByCycleIdAndStatus(cycle.getId(), TicketStatus.NO_SHOW),
                ticketRepository.countByCycleIdAndStatus(cycle.getId(), TicketStatus.CANCELLED),
                calledTicket == null ? null : TicketResponse.from(calledTicket));
    }

    private void validatePagination(int page, int size) {
        if (page < 0) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "INVALID_PAGINATION", "Page must be zero or greater");
        }
        if (size < 1 || size > 100) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "INVALID_PAGE_SIZE", "Size must be between 1 and 100");
        }
    }

    Queue findQueue(Long id) {
        return queueRepository.findById(id)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "QUEUE_NOT_FOUND", "Queue not found"));
    }

    Cycle activeCycle(Long queueId) {
        return cycleRepository.findByActiveQueueId(queueId).orElse(null);
    }

    private void validateWeights(int normal, int preferential, int urgent) {
        Map<String, String> fields = new LinkedHashMap<>();
        if (normal < 1 || normal > 10) {
            fields.put("weightNormal", "weightNormal must be between 1 and 10");
        }
        if (preferential < 1 || preferential > 10) {
            fields.put("weightPreferential", "weightPreferential must be between 1 and 10");
        }
        if (urgent < 1 || urgent > 10) {
            fields.put("weightUrgent", "weightUrgent must be between 1 and 10");
        }
        if (fields.isEmpty()) {
            if (normal > preferential) {
                fields.put("weightNormal",
                        "weightNormal must be less than or equal to weightPreferential");
            }
            if (preferential > urgent) {
                fields.put("weightPreferential",
                        "weightPreferential must be less than or equal to weightUrgent");
            }
        }
        if (!fields.isEmpty()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed", fields);
        }
    }

    private String normalizePrefix(String prefix) {
        return prefix.trim().toUpperCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private ApiException duplicateQueue() {
        return new ApiException(
                HttpStatus.CONFLICT, "DUPLICATE_QUEUE", "A queue with this name or prefix already exists");
    }
}
