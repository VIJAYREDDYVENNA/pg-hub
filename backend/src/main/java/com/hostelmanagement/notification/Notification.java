package com.hostelmanagement.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "hostel_id")
    private Long hostelId;

    @Column(nullable = false, length = 40)
    private String type;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 1000)
    private String message;

    @Column(length = 255)
    private String link;

    @Column(name = "dedupe_key", length = 120)
    private String dedupeKey;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
