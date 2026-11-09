package com.fila.ciclica.domain;

import com.fila.ciclica.observability.TicketTransitionLogger;
import com.fila.ciclica.repository.CycleRepository;
import com.fila.ciclica.repository.TicketRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the reusable rules around closing a cycle. A normal closure opens the next cycle and requeues
 * every NO_SHOW ticket; queue deactivation closes without opening a successor.
 */
@Service
public class CycleLifecycleService {

    private static final List<TicketStatus> ACTIVE_STATUSES =
            List.of(TicketStatus.WAITING, TicketStatus.CALLED);

    private final CycleRepository cycleRepository;
    private final TicketRepository ticketRepository;
    private final TicketTransitionLogger transitionLogger;

    public CycleLifecycleService(
            CycleRepository cycleRepository,
            TicketRepository ticketRepository,
            TicketTransitionLogger transitionLogger) {
        this.cycleRepository = cycleRepository;
        this.ticketRepository = ticketRepository;
        this.transitionLogger = transitionLogger;
    }

    /**
     * Closes the cycle only when it has no WAITING or CALLED tickets left, then opens the next ACTIVE
     * cycle and moves every NO_SHOW ticket into it as WAITING with a fresh weight snapshot.
     *
     * @return true when the cycle was closed and rolled over, false when active tickets remain
     */
    @Transactional
    public boolean closeIfEmpty(Cycle cycle) {
        if (cycle == null || !hasNoActiveTickets(cycle)) {
            return false;
        }

        Instant now = Instant.now();
        close(cycle, now);
        // Flush the close first: the unique active_queue_id must be released before the successor.
        cycleRepository.saveAndFlush(cycle);

        Cycle next = new Cycle();
        next.setQueue(cycle.getQueue());
        next.setNumber(cycle.getNumber() + 1);
        next.setStatus(CycleStatus.ACTIVE);
        next.setActiveQueueId(cycle.getQueue().getId());
        next.setStartedAt(now);
        next = cycleRepository.saveAndFlush(next);

        requeueNoShowTickets(cycle, next, now);
        return true;
    }

    /**
     * Closes an empty cycle without creating a successor. Used when a queue is deactivated.
     *
     * @return true when the cycle was closed, false when active tickets remain
     */
    @Transactional
    public boolean closeWithoutRollover(Cycle cycle) {
        if (cycle == null || !hasNoActiveTickets(cycle)) {
            return false;
        }
        close(cycle, Instant.now());
        cycleRepository.saveAndFlush(cycle);
        return true;
    }

    private boolean hasNoActiveTickets(Cycle cycle) {
        return ticketRepository.countByCycleIdAndStatusIn(cycle.getId(), ACTIVE_STATUSES) == 0;
    }

    private void close(Cycle cycle, Instant now) {
        cycle.setStatus(CycleStatus.CLOSED);
        cycle.setClosedAt(now);
        cycle.setActiveQueueId(null);
    }

    private void requeueNoShowTickets(Cycle closed, Cycle next, Instant now) {
        for (Ticket ticket : ticketRepository.findByCycleIdOrderByCreatedAtAsc(closed.getId())) {
            if (ticket.getStatus() != TicketStatus.NO_SHOW) {
                continue;
            }
            ticket.setCycle(next);
            ticket.setStatus(TicketStatus.WAITING);
            ticket.setCallsInCycle(0);
            ticket.setCycleWeight(next.getQueue().weightFor(ticket.getPriority()));
            ticket.setCalledAt(null);
            ticket.setFinishedAt(null);
            ticket.setUpdatedAt(now);
            ticketRepository.save(ticket);
            transitionLogger.log(
                    next.getQueue().getId(),
                    ticket.getCode(),
                    next.getNumber(),
                    TicketStatus.NO_SHOW,
                    TicketStatus.WAITING);
        }
        ticketRepository.flush();
    }
}
