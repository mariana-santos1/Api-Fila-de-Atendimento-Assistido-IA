package com.fila.ciclica.repository;

import com.fila.ciclica.domain.CycleCall;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CycleCallRepository extends JpaRepository<CycleCall, Long> {

    Optional<CycleCall> findTopByCycleIdOrderBySequenceNumberDesc(Long cycleId);

    long countByCycleId(Long cycleId);
}
