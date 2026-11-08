package com.fila.ciclica.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

@Entity
@Table(name = "queues")
public class Queue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120, unique = true)
    private String name;

    @Column(nullable = false, length = 8, unique = true)
    private String prefix;

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "weight_normal", nullable = false)
    private int weightNormal;

    @Column(name = "weight_preferential", nullable = false)
    private int weightPreferential;

    @Column(name = "weight_urgent", nullable = false)
    private int weightUrgent;

    @Column(name = "ticket_counter", nullable = false)
    private long ticketCounter;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    public Queue() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public int getWeightNormal() {
        return weightNormal;
    }

    public void setWeightNormal(int weightNormal) {
        this.weightNormal = weightNormal;
    }

    public int getWeightPreferential() {
        return weightPreferential;
    }

    public void setWeightPreferential(int weightPreferential) {
        this.weightPreferential = weightPreferential;
    }

    public int getWeightUrgent() {
        return weightUrgent;
    }

    public void setWeightUrgent(int weightUrgent) {
        this.weightUrgent = weightUrgent;
    }

    public long getTicketCounter() {
        return ticketCounter;
    }

    public void setTicketCounter(long ticketCounter) {
        this.ticketCounter = ticketCounter;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }
}
