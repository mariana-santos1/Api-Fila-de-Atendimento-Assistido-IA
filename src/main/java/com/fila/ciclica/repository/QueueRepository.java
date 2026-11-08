package com.fila.ciclica.repository;

import com.fila.ciclica.domain.Queue;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QueueRepository extends JpaRepository<Queue, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from Queue q where q.id = :id")
    Optional<Queue> findByIdForUpdate(@Param("id") Long id);

    boolean existsByName(String name);

    boolean existsByPrefix(String prefix);

    Page<Queue> findByActive(boolean active, Pageable pageable);
}
