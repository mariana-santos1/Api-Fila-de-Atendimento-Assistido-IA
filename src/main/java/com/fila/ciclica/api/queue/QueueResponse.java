package com.fila.ciclica.api.queue;

import com.fila.ciclica.domain.Cycle;
import com.fila.ciclica.domain.CycleStatus;
import com.fila.ciclica.domain.Queue;
import java.time.Instant;

public record QueueResponse(
        Long id,
        String name,
        String prefix,
        String description,
        boolean active,
        int weightNormal,
        int weightPreferential,
        int weightUrgent,
        Integer activeCycleNumber,
        CycleStatus activeCycleStatus,
        Instant createdAt) {

    public static QueueResponse from(Queue queue, Cycle activeCycle) {
        return new QueueResponse(
                queue.getId(),
                queue.getName(),
                queue.getPrefix(),
                queue.getDescription(),
                queue.isActive(),
                queue.getWeightNormal(),
                queue.getWeightPreferential(),
                queue.getWeightUrgent(),
                activeCycle == null ? null : activeCycle.getNumber(),
                activeCycle == null ? null : activeCycle.getStatus(),
                queue.getCreatedAt());
    }
}
