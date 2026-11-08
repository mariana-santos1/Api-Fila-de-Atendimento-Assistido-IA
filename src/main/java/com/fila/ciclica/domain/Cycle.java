package com.fila.ciclica.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "cycles")
public class Cycle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "queue_id", nullable = false)
    private Queue queue;

    @Column(nullable = false)
    private int number;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CycleStatus status;

    @Column(name = "active_queue_id")
    private Long activeQueueId;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    public Cycle() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Queue getQueue() {
        return queue;
    }

    public void setQueue(Queue queue) {
        this.queue = queue;
    }

    public int getNumber() {
        return number;
    }

    public void setNumber(int number) {
        this.number = number;
    }

    public CycleStatus getStatus() {
        return status;
    }

    public void setStatus(CycleStatus status) {
        this.status = status;
    }

    public Long getActiveQueueId() {
        return activeQueueId;
    }

    public void setActiveQueueId(Long activeQueueId) {
        this.activeQueueId = activeQueueId;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(Instant closedAt) {
        this.closedAt = closedAt;
    }
}
