package com.hostelmanagement.common.files;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FileAttachmentRepository extends JpaRepository<FileAttachment, Long> {

    List<FileAttachment> findByOwnerIdAndEntityTypeAndEntityIdOrderByCreatedAtDesc(Long ownerId, String entityType, Long entityId);
}
