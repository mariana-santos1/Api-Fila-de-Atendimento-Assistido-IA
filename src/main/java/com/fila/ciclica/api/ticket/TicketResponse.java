package com.fila.ciclica.api.ticket;

import com.fila.ciclica.domain.Priority;
import com.fila.ciclica.domain.Ticket;
import com.fila.ciclica.domain.TicketStatus;
import java.time.Instant;

public record TicketResponse(
        String publicId,
        Long queueId,
        String queueName,
        int cycleNumber,
        String code,
        String customerName,
        Priority priority,
        TicketStatus status,
        int callsInCycle,
        int cycleWeight,
        String externalReference,
        Instant calledAt,
        Instant finishedAt,
        Instant createdAt) {

    public static TicketResponse from(Ticket ticket) {
        return new TicketResponse(
                ticket.getPublicId(),
                ticket.getQueue().getId(),
                ticket.getQueue().getName(),
                ticket.getCycle().getNumber(),
                ticket.getCode(),
                ticket.getCustomerName(),
                ticket.getPriority(),
                ticket.getStatus(),
                ticket.getCallsInCycle(),
                ticket.getCycleWeight(),
                ticket.getExternalReference(),
                ticket.getCalledAt(),
                ticket.getFinishedAt(),
                ticket.getCreatedAt());
    }
}
