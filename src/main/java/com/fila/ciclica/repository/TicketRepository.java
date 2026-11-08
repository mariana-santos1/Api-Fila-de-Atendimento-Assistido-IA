package com.fila.ciclica.repository;

import com.fila.ciclica.domain.Ticket;
import com.fila.ciclica.domain.TicketStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    Optional<Ticket> findByPublicId(String publicId);

    Optional<Ticket> findByQueueIdAndExternalReference(Long queueId, String externalReference);

    boolean existsByCycleIdAndStatus(Long cycleId, TicketStatus status);

    long countByCycleIdAndStatusIn(Long cycleId, Collection<TicketStatus> statuses);

    long countByCycleIdAndStatus(Long cycleId, TicketStatus status);

    Optional<Ticket> findFirstByCycleIdAndStatus(Long cycleId, TicketStatus status);

    boolean existsByQueueIdAndStatusIn(Long queueId, Collection<TicketStatus> statuses);

    List<Ticket> findByCycleIdOrderByCreatedAtAsc(Long cycleId);

    default Optional<Ticket> findNextCandidate(Long cycleId) {
        return findNextCandidates(cycleId, PageRequest.of(0, 1)).stream().findFirst();
    }

    @Query("select t from Ticket t "
            + "where t.cycle.id = :cycleId "
            + "and t.status = com.fila.ciclica.domain.TicketStatus.WAITING "
            + "and t.callsInCycle < t.cycleWeight "
            + "order by t.callsInCycle asc, t.cycleWeight desc, t.createdAt asc, t.id asc")
    List<Ticket> findNextCandidates(@Param("cycleId") Long cycleId, Pageable pageable);
}
