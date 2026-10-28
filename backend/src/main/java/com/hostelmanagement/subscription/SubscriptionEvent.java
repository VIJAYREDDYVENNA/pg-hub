package com.hostelmanagement.subscription;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** Append-only record of a subscription change and the administrator responsible for it. */
@Getter
@Setter
@Entity
@Table(name = "subscription_events")
public class SubscriptionEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "event_type", nullable = false, length = 30)
    private String eventType;

    @Column(length = 1000)
    private String details;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
