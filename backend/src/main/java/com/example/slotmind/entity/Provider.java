package com.example.slotmind.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "providers")
@Getter
@NoArgsConstructor
public class Provider extends BaseEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Specialty specialty;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProviderStatus status;

    private String statusReason;

    private LocalDateTime statusChangedAt;

    public Provider(String id, String name, Specialty specialty, ProviderStatus status) {
        this.id = id;
        this.name = name;
        this.specialty = specialty;
        this.status = status;
    }

    public void changeStatus(ProviderStatus newStatus, String reason) {
        this.status = newStatus;
        this.statusReason = reason;
        this.statusChangedAt = LocalDateTime.now();
    }

    public boolean isOffline() {
        return status == ProviderStatus.OFFLINE;
    }
}
