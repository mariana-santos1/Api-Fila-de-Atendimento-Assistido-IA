package com.fila.ciclica.api.ticket;

import com.fila.ciclica.common.ApiException;
import com.fila.ciclica.domain.Cycle;
import com.fila.ciclica.domain.Priority;
import com.fila.ciclica.domain.Queue;
import com.fila.ciclica.domain.Ticket;
import com.fila.ciclica.domain.TicketStatus;
import com.fila.ciclica.repository.CycleRepository;
import com.fila.ciclica.repository.QueueRepository;
import com.fila.ciclica.repository.TicketRepository;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TicketService {

    private final QueueRepository queueRepository;
    private final CycleRepository cycleRepository;
    private final TicketRepository ticketRepository;

    public TicketService(
            QueueRepository queueRepository,
            CycleRepository cycleRepository,
            TicketRepository ticketRepository) {
        this.queueRepository = queueRepository;
        this.cycleRepository = cycleRepository;
        this.ticketRepository = ticketRepository;
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

    ApiException invalidPriority() {
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                "INVALID_PRIORITY",
                "Priority must be NORMAL, PREFERENTIAL or URGENT");
    }
}
