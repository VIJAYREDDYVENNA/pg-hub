package com.hostelmanagement.common.files;

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
@Table(name = "file_attachments")
public class FileAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "hostel_id")
    private Long hostelId;

    @Column(name = "entity_type", nullable = false, length = 40)
    private String entityType;

    @Column(name = "entity_id")
    private Long entityId;

    @Column(length = 40)
    private String category;

    @Column(name = "original_name", nullable = false)
    private String originalName;

    @Column(name = "stored_name", nullable = false, length = 100)
    private String storedName;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "uploaded_by")
    private Long uploadedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
