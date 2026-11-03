package com.example.slotmind.entity;

import com.example.slotmind.exception.InvalidStateException;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Lifecycle: CONFIRMED -> RESCHEDULE_PENDING -> RESCHEDULED -> COMPLETED.
 * A rescheduled appointment can be disrupted again, so RESCHEDULED -> RESCHEDULE_PENDING is allowed too.
 */
@Entity
@Table(name = "appointments")
@Getter
@NoArgsConstructor
public class Appointment extends BaseEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String patientName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AppointmentType type;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_id")
    private Provider provider;

    // unique: one slot can never carry two appointments - the database-level guard against double booking
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "slot_id", unique = true)
    private Slot slot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AppointmentStatus status;

    private String patientPhone;

    @Column(length = 500)
    private String notes;

    @Column(nullable = false)
    private int rescheduleCount;

    @Version
    private Long version;

    public Appointment(String id, String patientName, AppointmentType type, String patientPhone, String notes, Slot slot) {
        if (slot.getStatus() != SlotStatus.BOOKED) {
            throw new InvalidStateException("Slot " + slot.getId() + " must be booked before an appointment uses it");
        }
        this.id = id;
        this.patientName = patientName;
        this.type = type;
        this.patientPhone = patientPhone;
        this.notes = notes;
        this.provider = slot.getProvider();
        this.slot = slot;
        this.status = AppointmentStatus.CONFIRMED;
        this.rescheduleCount = 0;
    }

    public void markReschedulePending() {
        if (status != AppointmentStatus.CONFIRMED && status != AppointmentStatus.RESCHEDULED
                && status != AppointmentStatus.RESCHEDULE_PENDING) {
            throw new InvalidStateException("Appointment " + id + " is " + status + ", cannot be rescheduled");
        }
        this.status = AppointmentStatus.RESCHEDULE_PENDING;
    }

    /** Moves the appointment onto newSlot; the caller frees the old slot. */
    public void moveTo(Slot newSlot) {
        if (status != AppointmentStatus.RESCHEDULE_PENDING) {
            throw new InvalidStateException("Appointment " + id + " is " + status + ", expected RESCHEDULE_PENDING");
        }
        this.slot = newSlot;
        this.provider = newSlot.getProvider();
        this.status = AppointmentStatus.RESCHEDULED;
        this.rescheduleCount++;
    }

    public void complete() {
        if (status != AppointmentStatus.CONFIRMED && status != AppointmentStatus.RESCHEDULED) {
            throw new InvalidStateException("Appointment " + id + " is " + status + ", cannot complete");
        }
        this.status = AppointmentStatus.COMPLETED;
    }
}
