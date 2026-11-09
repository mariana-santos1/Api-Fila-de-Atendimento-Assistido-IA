package com.fila.ciclica.api.call;

import com.fila.ciclica.api.ticket.TicketResponse;
import com.fila.ciclica.common.ApiException;
import com.fila.ciclica.domain.Cycle;
import com.fila.ciclica.domain.CycleCall;
import com.fila.ciclica.domain.CycleLifecycleService;
import com.fila.ciclica.domain.CycleScheduler;
import com.fila.ciclica.domain.Queue;
import com.fila.ciclica.domain.Ticket;
import com.fila.ciclica.domain.TicketStatus;
import com.fila.ciclica.domain.User;
import com.fila.ciclica.observability.TicketTransitionLogger;
import com.fila.ciclica.repository.CycleCallRepository;
import com.fila.ciclica.repository.CycleRepository;
import com.fila.ciclica.repository.QueueRepository;
import com.fila.ciclica.repository.TicketRepository;
import com.fila.ciclica.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CallService {

    private final QueueRepository queueRepository;
    private final CycleRepository cycleRepository;
    private final TicketRepository ticketRepository;
    private final CycleCallRepository cycleCallRepository;
    private final UserRepository userRepository;
    private final CycleScheduler cycleScheduler;
    private final CycleLifecycleService cycleLifecycleService;
    private final TicketTransitionLogger transitionLogger;

    public CallService(
            QueueRepository queueRepository,
            CycleRepository cycleRepository,
            TicketRepository ticketRepository,
            CycleCallRepository cycleCallRepository,
            UserRepository userRepository,
            CycleScheduler cycleScheduler,
            CycleLifecycleService cycleLifecycleService,
            TicketTransitionLogger transitionLogger) {
        this.queueRepository = queueRepository;
        this.cycleRepository = cycleRepository;
        this.ticketRepository = ticketRepository;
        this.cycleCallRepository = cycleCallRepository;
        this.userRepository = userRepository;
        this.cycleScheduler = cycleScheduler;
        this.cycleLifecycleService = cycleLifecycleService;
        this.transitionLogger = transitionLogger;
    }

    @Transactional
    public TicketResponse callNext(Long queueId, String attendantEmail) {
        Queue queue = queueRepository.findByIdForUpdate(queueId)
                .orElseThrow(this::queueNotFound);

        if (!queue.isActive()) {
            throw queueInactive();
        }

        Cycle cycle = cycleRepository.findByActiveQueueId(queueId)
                .orElseThrow(this::queueInactive);

        if (ticketRepository.existsByCycleIdAndStatus(cycle.getId(), TicketStatus.CALLED)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "ACTIVE_CALL_EXISTS",
                    "Queue already has an active call");
        }

        List<Ticket> candidates = ticketRepository.findNextCandidates(cycle.getId(), Pageable.unpaged());
        Ticket ticket = cycleScheduler.selectNext(candidates)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.CONFLICT,
                        "NO_TICKET_AVAILABLE",
                        "No waiting ticket is eligible in the active cycle"));

        Instant now = Instant.now();
        TicketStatus previousStatus = ticket.getStatus();
        ticket.setCallsInCycle(ticket.getCallsInCycle() + 1);
        ticket.setStatus(TicketStatus.CALLED);
        ticket.setCalledAt(now);
        ticket.setUpdatedAt(now);
        ticket = ticketRepository.saveAndFlush(ticket);
        transitionLogger.log(
                queue.getId(),
                ticket.getCode(),
                cycle.getNumber(),
                previousStatus,
                TicketStatus.CALLED);

        User attendant = userRepository.findByEmail(attendantEmail)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authenticated user not found"));

        CycleCall cycleCall = new CycleCall();
        cycleCall.setCycle(cycle);
        cycleCall.setTicket(ticket);
        cycleCall.setAttendant(attendant);
        cycleCall.setSequenceNumber(nextSequenceNumber(cycle.getId()));
        cycleCall.setCalledAt(now);
        cycleCallRepository.saveAndFlush(cycleCall);

        return TicketResponse.from(ticket);
    }

    @Transactional
    public TicketResponse complete(String publicId) {
        Ticket ticket = findCalledTicket(publicId);
        Instant now = Instant.now();
        int cycleNumber = ticket.getCycle().getNumber();
        ticket.setStatus(TicketStatus.COMPLETED);
        ticket.setFinishedAt(now);
        ticket.setUpdatedAt(now);
        ticket = ticketRepository.saveAndFlush(ticket);
        transitionLogger.log(
                ticket.getQueue().getId(),
                ticket.getCode(),
                cycleNumber,
                TicketStatus.CALLED,
                TicketStatus.COMPLETED);

        cycleLifecycleService.closeIfEmpty(ticket.getCycle());
        return TicketResponse.from(ticket);
    }

    @Transactional
    public TicketResponse noAnswer(String publicId) {
        Ticket ticket = findCalledTicket(publicId);
        Instant now = Instant.now();
        int cycleNumber = ticket.getCycle().getNumber();
        TicketStatus outcome = cycleScheduler.applyNoAnswer(ticket);
        ticket.setStatus(outcome);
        ticket.setFinishedAt(outcome == TicketStatus.NO_SHOW ? now : null);
        ticket.setUpdatedAt(now);
        ticket = ticketRepository.saveAndFlush(ticket);
        transitionLogger.log(
                ticket.getQueue().getId(),
                ticket.getCode(),
                cycleNumber,
                TicketStatus.CALLED,
                outcome);

        cycleLifecycleService.closeIfEmpty(ticket.getCycle());
        return TicketResponse.from(ticket);
    }

    @Transactional
    public TicketResponse noShow(String publicId) {
        Ticket ticket = findCalledTicket(publicId);
        Instant now = Instant.now();
        int cycleNumber = ticket.getCycle().getNumber();
        ticket.setStatus(TicketStatus.NO_SHOW);
        ticket.setFinishedAt(now);
        ticket.setUpdatedAt(now);
        ticket = ticketRepository.saveAndFlush(ticket);
        transitionLogger.log(
                ticket.getQueue().getId(),
                ticket.getCode(),
                cycleNumber,
                TicketStatus.CALLED,
                TicketStatus.NO_SHOW);

        cycleLifecycleService.closeIfEmpty(ticket.getCycle());
        return TicketResponse.from(ticket);
    }

    private Ticket findCalledTicket(String publicId) {
        Ticket ticket = ticketRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "TICKET_NOT_FOUND", "Ticket not found"));

        if (ticket.getStatus() != TicketStatus.CALLED) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "INVALID_STATE",
                    "Only a called ticket can be concluded or have its outcome recorded");
        }
        return ticket;
    }

    private int nextSequenceNumber(Long cycleId) {
        return cycleCallRepository.findTopByCycleIdOrderBySequenceNumberDesc(cycleId)
                .map(last -> last.getSequenceNumber() + 1)
                .orElse(1);
    }

    private ApiException queueNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "QUEUE_NOT_FOUND", "Queue not found");
    }

    private ApiException queueInactive() {
        return new ApiException(
                HttpStatus.CONFLICT, "QUEUE_INACTIVE", "Queue has no active cycle");
    }
}
