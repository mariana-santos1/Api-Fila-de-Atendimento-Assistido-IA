package com.fila.ciclica.domain;

import com.fila.ciclica.repository.CycleRepository;
import com.fila.ciclica.repository.TicketRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the reusable rules around closing a cycle. Later cycle rollover tasks extend this class to
 * open the next cycle and requeue NO_SHOW tickets after a successful close.
 */
@Service
public class CycleLifecycleService {

    private static final List<TicketStatus> ACTIVE_STATUSES =
            List.of(TicketStatus.WAITING, TicketStatus.CALLED);

    private final CycleRepository cycleRepository;
    private final TicketRepository ticketRepository;

    public CycleLifecycleService(CycleRepository cycleRepository, TicketRepository ticketRepository) {
        this.cycleRepository = cycleRepository;
        this.ticketRepository = ticketRepository;
    }

    /**
     * Closes the cycle only when it has no WAITING or CALLED tickets left.
     *
     * @return true when the cycle was closed, false when active tickets remain
     */
    @Transactional
    public boolean closeIfEmpty(Cycle cycle) {
        if (cycle == null) {
            return false;
        }
        long remaining = ticketRepository.countByCycleIdAndStatusIn(cycle.getId(), ACTIVE_STATUSES);
        if (remaining > 0) {
            return false;
        }

        cycle.setStatus(CycleStatus.CLOSED);
        cycle.setClosedAt(Instant.now());
        cycle.setActiveQueueId(null);
        cycleRepository.save(cycle);
        return true;
    }
}
