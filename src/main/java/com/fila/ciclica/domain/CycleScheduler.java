package com.fila.ciclica.domain;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Pure cycle rules: chooses the next ticket to call and decides when a cycle can close. No repository
 * access so the rules can be unit tested with in-memory entities.
 */
@Component
public class CycleScheduler {

    private static final Comparator<Ticket> NEXT_TICKET_ORDER =
            Comparator.comparingInt(Ticket::getCallsInCycle)
                    .thenComparing(Ticket::getCycleWeight, Comparator.reverseOrder())
                    .thenComparing(Ticket::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(Ticket::getId, Comparator.nullsLast(Comparator.naturalOrder()));

    /**
     * Selects the next eligible WAITING ticket ordered by calls in cycle ascending, cycle weight
     * descending, creation time ascending and id ascending.
     */
    public Optional<Ticket> selectNext(List<Ticket> tickets) {
        if (tickets == null || tickets.isEmpty()) {
            return Optional.empty();
        }
        return tickets.stream()
                .filter(ticket -> ticket.getStatus() == TicketStatus.WAITING)
                .filter(ticket -> ticket.getCallsInCycle() < ticket.getCycleWeight())
                .min(NEXT_TICKET_ORDER);
    }

    /**
     * A cycle may close only when it has neither WAITING nor CALLED tickets.
     */
    public boolean shouldCloseCycle(List<Ticket> tickets) {
        if (tickets == null || tickets.isEmpty()) {
            return true;
        }
        return tickets.stream().noneMatch(ticket -> ticket.getStatus() == TicketStatus.WAITING
                || ticket.getStatus() == TicketStatus.CALLED);
    }

    /**
     * Applies a "no answer" outcome: the ticket returns to WAITING while it still has attempts left,
     * otherwise it becomes NO_SHOW.
     */
    public TicketStatus applyNoAnswer(Ticket ticket) {
        if (ticket.getCallsInCycle() < ticket.getCycleWeight()) {
            return TicketStatus.WAITING;
        }
        return TicketStatus.NO_SHOW;
    }
}
