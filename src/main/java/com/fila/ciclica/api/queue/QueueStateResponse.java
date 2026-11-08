package com.fila.ciclica.api.queue;

import com.fila.ciclica.api.ticket.TicketResponse;

public record QueueStateResponse(
        Long queueId,
        String queueName,
        Integer cycleNumber,
        long waiting,
        long called,
        long completed,
        long noShow,
        long cancelled,
        TicketResponse calledTicket) {

    public static QueueStateResponse empty(Long queueId, String queueName) {
        return new QueueStateResponse(queueId, queueName, null, 0, 0, 0, 0, 0, null);
    }
}
