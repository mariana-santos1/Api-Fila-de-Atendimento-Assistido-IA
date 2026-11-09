package com.fila.ciclica.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.fila.ciclica.support.ProofReporter;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProofReporter.class)
class CycleSchedulerTest {

    private final CycleScheduler scheduler = new CycleScheduler();

    @Test
    @DisplayName("C25 next selection respects calls weight and age")
    void nextSelectionRespectsCallsWeightAndAge() {
        Instant base = Instant.parse("2026-01-01T00:00:00Z");

        Ticket fewestCalls = ticket(1L, TicketStatus.WAITING, 1, 3, base.plusSeconds(30));
        Ticket moreCalls = ticket(2L, TicketStatus.WAITING, 2, 3, base);
        assertThat(scheduler.selectNext(List.of(moreCalls, fewestCalls))).contains(fewestCalls);

        Ticket heavierWeight = ticket(3L, TicketStatus.WAITING, 0, 3, base.plusSeconds(30));
        Ticket lighterWeight = ticket(4L, TicketStatus.WAITING, 0, 1, base);
        assertThat(scheduler.selectNext(List.of(lighterWeight, heavierWeight))).contains(heavierWeight);

        Ticket older = ticket(6L, TicketStatus.WAITING, 0, 2, base.plusSeconds(10));
        Ticket newer = ticket(5L, TicketStatus.WAITING, 0, 2, base.plusSeconds(20));
        assertThat(scheduler.selectNext(List.of(newer, older))).contains(older);

        Ticket lowerId = ticket(8L, TicketStatus.WAITING, 0, 2, base);
        Ticket higherId = ticket(9L, TicketStatus.WAITING, 0, 2, base);
        assertThat(scheduler.selectNext(List.of(higherId, lowerId))).contains(lowerId);

        Ticket called = ticket(10L, TicketStatus.CALLED, 0, 2, base);
        Ticket completed = ticket(11L, TicketStatus.COMPLETED, 0, 2, base);
        assertThat(scheduler.selectNext(List.of(called, completed))).isEmpty();
    }

    @Test
    @DisplayName("C33 ticket never exceeds cycle weight")
    void ticketNeverExceedsCycleWeight() {
        Instant base = Instant.parse("2026-01-01T00:00:00Z");

        Ticket exhausted = ticket(1L, TicketStatus.WAITING, 3, 3, base);
        assertThat(scheduler.selectNext(List.of(exhausted))).isEmpty();

        Ticket belowWeight = ticket(2L, TicketStatus.WAITING, 2, 3, base);
        assertThat(scheduler.selectNext(List.of(belowWeight))).contains(belowWeight);

        Ticket atWeight = ticket(3L, TicketStatus.CALLED, 3, 3, base);
        assertThat(scheduler.applyNoAnswer(atWeight)).isEqualTo(TicketStatus.NO_SHOW);

        Ticket stillBelowWeight = ticket(4L, TicketStatus.CALLED, 2, 3, base);
        assertThat(scheduler.applyNoAnswer(stillBelowWeight)).isEqualTo(TicketStatus.WAITING);

        Ticket weightOne = ticket(5L, TicketStatus.CALLED, 1, 1, base);
        assertThat(scheduler.applyNoAnswer(weightOne)).isEqualTo(TicketStatus.NO_SHOW);
    }

    private Ticket ticket(
            Long id, TicketStatus status, int callsInCycle, int cycleWeight, Instant createdAt) {
        Ticket ticket = new Ticket();
        ticket.setId(id);
        ticket.setStatus(status);
        ticket.setCallsInCycle(callsInCycle);
        ticket.setCycleWeight(cycleWeight);
        ticket.setCreatedAt(createdAt);
        return ticket;
    }
}
