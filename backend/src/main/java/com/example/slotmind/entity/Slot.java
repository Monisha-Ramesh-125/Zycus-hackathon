package com.example.slotmind.entity;

import com.example.slotmind.exception.InvalidStateException;
import com.example.slotmind.exception.SlotUnavailableException;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Booking rules live on the domain object. The @Version column makes two concurrent
 * changes to the same slot collide instead of silently overwriting each other.
 */
@Entity
@Table(name = "slots")
@Getter
@NoArgsConstructor
public class Slot extends BaseEntity {

    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_id")
    private Provider provider;

    @Column(nullable = false)
    private LocalDateTime startTime;

    @Column(nullable = false)
    private int durationMinutes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SlotStatus status;

    private String statusReason;

    @Version
    private Long version;

    public Slot(String id, Provider provider, LocalDateTime startTime, int durationMinutes, SlotStatus status) {
        this.id = id;
        this.provider = provider;
        this.startTime = startTime;
        this.durationMinutes = durationMinutes;
        this.status = status;
    }

    public void book() {
        if (status != SlotStatus.OPEN) {
            throw new SlotUnavailableException(id, status);
        }
        if (provider.isOffline()) {
            throw new InvalidStateException("Provider " + provider.getId() + " is offline; slot " + id + " cannot be booked");
        }
        this.status = SlotStatus.BOOKED;
    }

    /** Frees a booked slot. A cancelled slot stays cancelled. */
    public void release() {
        if (status == SlotStatus.BOOKED) {
            this.status = SlotStatus.OPEN;
        }
    }

    public void cancel(String reason) {
        this.status = SlotStatus.CANCELLED;
        this.statusReason = reason;
    }

    public boolean isOpen() {
        return status == SlotStatus.OPEN;
    }
}
