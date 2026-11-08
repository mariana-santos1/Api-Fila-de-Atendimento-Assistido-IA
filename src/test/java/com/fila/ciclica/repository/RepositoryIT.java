package com.fila.ciclica.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fila.ciclica.domain.Cycle;
import com.fila.ciclica.domain.CycleStatus;
import com.fila.ciclica.domain.Priority;
import com.fila.ciclica.domain.Queue;
import com.fila.ciclica.domain.Role;
import com.fila.ciclica.domain.Ticket;
import com.fila.ciclica.domain.TicketStatus;
import com.fila.ciclica.domain.User;
import com.fila.ciclica.support.AbstractIntegrationTest;
import com.fila.ciclica.support.ProofReporter;
import jakarta.persistence.LockModeType;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(ProofReporter.class)
class RepositoryIT extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private QueueRepository queueRepository;

    @Autowired
    private CycleRepository cycleRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private CycleCallRepository cycleCallRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    @DisplayName("queue lock method acquires a pessimistic write lock")
    void queueLockMethodUsesPessimisticWrite() throws Exception {
        Method method = QueueRepository.class.getMethod("findByIdForUpdate", Long.class);
        Lock lock = method.getAnnotation(Lock.class);
        assertThat(lock).isNotNull();
        assertThat(lock.value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);

        Queue queue = queueRepository.save(newQueue("LOCK", "lock"));
        Queue locked = transactionTemplate.execute(status -> queueRepository.findByIdForUpdate(queue.getId()).orElseThrow());
        assertThat(locked.getId()).isEqualTo(queue.getId());
    }

    @Test
    @DisplayName("next candidate query orders by calls, weight, creation and id")
    void nextCandidateOrdering() {
        Queue queue = queueRepository.save(newQueue("ORDER", "order"));
        Cycle cycle = cycleRepository.save(newCycle(queue, 1));

        Ticket lowWeight = ticketRepository.save(
                newTicket(queue, cycle, "ORDER-1", 1, 2, Instant.parse("2026-01-01T00:00:00Z")));
        Ticket olderHigherWeight = ticketRepository.save(
                newTicket(queue, cycle, "ORDER-2", 1, 3, Instant.parse("2026-01-01T00:00:02Z")));
        Ticket newerHigherWeight = ticketRepository.save(
                newTicket(queue, cycle, "ORDER-3", 1, 3, Instant.parse("2026-01-01T00:00:03Z")));
        Ticket twoCalls = ticketRepository.save(
                newTicket(queue, cycle, "ORDER-4", 2, 3, Instant.parse("2026-01-01T00:00:00Z")));
        Ticket exhausted = ticketRepository.save(
                newTicket(queue, cycle, "ORDER-5", 3, 3, Instant.parse("2026-01-01T00:00:00Z")));

        assertThat(ticketRepository.findNextCandidate(cycle.getId()))
                .map(Ticket::getId)
                .contains(olderHigherWeight.getId());

        List<Ticket> ordered = ticketRepository.findNextCandidates(
                cycle.getId(), org.springframework.data.domain.PageRequest.of(0, 10));
        assertThat(ordered)
                .extracting(Ticket::getCode)
                .containsExactly("ORDER-2", "ORDER-3", "ORDER-1", "ORDER-4");
        assertThat(ordered).doesNotContain(exhausted);
        assertThat(lowWeight.getCallsInCycle()).isEqualTo(1);
        assertThat(twoCalls.getCallsInCycle()).isEqualTo(2);
    }

    @Test
    @DisplayName("repository queries find active cycle, external reference and call counts")
    void repositoryQueries() {
        Queue queue = queueRepository.save(newQueue("QUERY", "query"));
        Cycle cycle = cycleRepository.save(newCycle(queue, 1));
        Cycle closed = newCycle(queue, 0);
        closed.setNumber(0);
        closed.setStatus(CycleStatus.CLOSED);
        closed.setActiveQueueId(null);
        closed.setClosedAt(Instant.now());
        cycleRepository.save(closed);

        Ticket ticket = ticketRepository.save(
                newTicket(queue, cycle, "QUERY-1", 0, 2, Instant.now()));
        ticket.setExternalReference("external-1");
        ticketRepository.save(ticket);

        assertThat(cycleRepository.findByActiveQueueId(queue.getId()))
                .map(Cycle::getId)
                .contains(cycle.getId());
        assertThat(cycleRepository.findTopByQueueIdOrderByNumberDesc(queue.getId()))
                .map(Cycle::getId)
                .contains(cycle.getId());
        assertThat(cycleRepository.findByQueueIdAndStatus(queue.getId(), CycleStatus.CLOSED))
                .extracting(Cycle::getId)
                .containsExactly(closed.getId());
        assertThat(ticketRepository.findByPublicId(ticket.getPublicId()))
                .map(Ticket::getId)
                .contains(ticket.getId());
        assertThat(ticketRepository.findByQueueIdAndExternalReference(queue.getId(), "external-1"))
                .map(Ticket::getId)
                .contains(ticket.getId());
        assertThat(ticketRepository.existsByCycleIdAndStatus(cycle.getId(), TicketStatus.WAITING))
                .isTrue();
        assertThat(ticketRepository.countByCycleIdAndStatusIn(
                        cycle.getId(), List.of(TicketStatus.WAITING, TicketStatus.CALLED)))
                .isEqualTo(1);
        assertThat(ticketRepository.findByCycleIdOrderByCreatedAtAsc(cycle.getId()))
                .extracting(Ticket::getId)
                .containsExactly(ticket.getId());
        assertThat(cycleCallRepository.countByCycleId(cycle.getId())).isZero();
        assertThat(cycleCallRepository.findTopByCycleIdOrderBySequenceNumberDesc(cycle.getId()))
                .isEmpty();
        assertThat(queueRepository.existsByName(queue.getName())).isTrue();
        assertThat(queueRepository.existsByPrefix(queue.getPrefix())).isTrue();
    }

    @Test
    @DisplayName("unique constraints reject duplicates for users, queues and tickets")
    void uniqueConstraints() {
        User user = new User();
        user.setName("Duplicate User");
        user.setEmail("duplicate@test.local");
        user.setPasswordHash("$2a$10$abcdefghijklmnopqrstuv");
        user.setRole(Role.ATTENDANT);
        user.setActive(true);
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        userRepository.saveAndFlush(user);

        User duplicateUser = new User();
        duplicateUser.setName("Duplicate User 2");
        duplicateUser.setEmail("duplicate@test.local");
        duplicateUser.setPasswordHash("$2a$10$abcdefghijklmnopqrstuv");
        duplicateUser.setRole(Role.ATTENDANT);
        duplicateUser.setActive(true);
        duplicateUser.setCreatedAt(Instant.now());
        duplicateUser.setUpdatedAt(Instant.now());

        assertThatThrownBy(() -> userRepository.saveAndFlush(duplicateUser))
                .isInstanceOf(DataIntegrityViolationException.class);

        Queue queue = queueRepository.save(newQueue("DUP", "dup"));
        Queue duplicateQueueName = newQueue("DUP-TWO", "dup2");
        duplicateQueueName.setName(queue.getName());
        assertThatThrownBy(() -> queueRepository.saveAndFlush(duplicateQueueName))
                .isInstanceOf(DataIntegrityViolationException.class);

        Queue duplicatePrefix = newQueue("DUP-THREE", "dup3");
        duplicatePrefix.setPrefix(queue.getPrefix());
        assertThatThrownBy(() -> queueRepository.saveAndFlush(duplicatePrefix))
                .isInstanceOf(DataIntegrityViolationException.class);

        Cycle cycle = cycleRepository.save(newCycle(queue, 1));
        Ticket ticket = ticketRepository.save(
                newTicket(queue, cycle, "DUP-1", 0, 1, Instant.now()));

        Ticket duplicatePublicId = newTicket(queue, cycle, "DUP-2", 0, 1, Instant.now());
        duplicatePublicId.setPublicId(ticket.getPublicId());
        assertThatThrownBy(() -> ticketRepository.saveAndFlush(duplicatePublicId))
                .isInstanceOf(DataIntegrityViolationException.class);

        ticket.setExternalReference("dup-external");
        ticketRepository.saveAndFlush(ticket);

        Ticket duplicateExternal = newTicket(queue, cycle, "DUP-3", 0, 1, Instant.now());
        duplicateExternal.setExternalReference("dup-external");
        assertThatThrownBy(() -> ticketRepository.saveAndFlush(duplicateExternal))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Queue newQueue(String name, String prefix) {
        Queue queue = new Queue();
        queue.setName(name);
        queue.setPrefix(prefix.toUpperCase());
        queue.setDescription("test queue");
        queue.setActive(true);
        queue.setWeightNormal(1);
        queue.setWeightPreferential(2);
        queue.setWeightUrgent(3);
        queue.setTicketCounter(0);
        queue.setCreatedAt(Instant.now());
        queue.setUpdatedAt(Instant.now());
        return queue;
    }

    private Cycle newCycle(Queue queue, int number) {
        Cycle cycle = new Cycle();
        cycle.setQueue(queue);
        cycle.setNumber(number);
        cycle.setStatus(CycleStatus.ACTIVE);
        cycle.setActiveQueueId(queue.getId());
        cycle.setStartedAt(Instant.now());
        return cycle;
    }

    private Ticket newTicket(
            Queue queue, Cycle cycle, String code, int callsInCycle, int cycleWeight, Instant createdAt) {
        Ticket ticket = new Ticket();
        ticket.setPublicId(UUID.randomUUID().toString());
        ticket.setQueue(queue);
        ticket.setCycle(cycle);
        ticket.setCode(code);
        ticket.setCustomerName("Customer " + code);
        ticket.setPriority(Priority.NORMAL);
        ticket.setStatus(TicketStatus.WAITING);
        ticket.setCallsInCycle(callsInCycle);
        ticket.setCycleWeight(cycleWeight);
        ticket.setCreatedAt(createdAt);
        ticket.setUpdatedAt(createdAt);
        return ticket;
    }
}
