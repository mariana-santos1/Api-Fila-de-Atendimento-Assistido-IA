package com.fila.ciclica.repository;

import com.fila.ciclica.domain.Cycle;
import com.fila.ciclica.domain.CycleStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CycleRepository extends JpaRepository<Cycle, Long> {

    Optional<Cycle> findByActiveQueueId(Long activeQueueId);

    Optional<Cycle> findTopByQueueIdOrderByNumberDesc(Long queueId);

    List<Cycle> findByQueueIdAndStatus(Long queueId, CycleStatus status);
}
