package com.hostelmanagement.common.files;

import com.hostelmanagement.common.ApiException;
import com.hostelmanagement.config.AppProperties;
import com.hostelmanagement.security.AuthUser;
import com.hostelmanagement.security.CurrentUser;
import com.hostelmanagement.security.HostelAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Stores uploaded files outside the web root under random names. Only a small whitelist of types is accepted and
 * the declared content type must match the file signature ("magic bytes").
 */
@Service
@RequiredArgsConstructor
public class FileStorageService {

    public static final long MAX_BYTES = 5L * 1024 * 1024;

    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", ".jpg", "image/png", ".png", "image/webp", ".webp", "application/pdf", ".pdf");

    private final FileAttachmentRepository repository;
    private final HostelAccessService hostelAccess;
    private final AppProperties props;

    public record FileDto(Long id, String originalName, String contentType, long sizeBytes, String category,
                          Instant createdAt, String url) {
        public static FileDto from(FileAttachment f) {
            return new FileDto(f.getId(), f.getOriginalName(), f.getContentType(), f.getSizeBytes(), f.getCategory(),
                    f.getCreatedAt(), "/api/files/" + f.getId());
        }
    }

    @Transactional
    public FileAttachment store(MultipartFile file, String entityType, Long entityId, Long hostelId,
                                String category, boolean imagesOnly) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("Please choose a file to upload");
        }
        if (file.getSize() > MAX_BYTES) {
            throw ApiException.badRequest("The file is larger than the 5 MB limit");
        }
        String type = detectType(file);
        if (type == null || (imagesOnly && !type.startsWith("image/"))) {
            throw ApiException.badRequest(imagesOnly ? "Only JPG, PNG or WEBP images are allowed"
                    : "Only JPG, PNG, WEBP images or PDF documents are allowed");
        }
        AuthUser user = CurrentUser.get();
        String storedName = UUID.randomUUID().toString().replace("-", "") + EXTENSIONS.get(type);
        try {
            Path dir = baseDir();
            Files.createDirectories(dir);
            Path target = dir.resolve(storedName).normalize();
            if (!target.startsWith(dir)) {
                throw ApiException.badRequest("Invalid file name");
            }
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not store uploaded file", e);
        }
        FileAttachment f = new FileAttachment();
        f.setOwnerId(user.ownerId());
        f.setHostelId(hostelId);
        f.setEntityType(entityType);
        f.setEntityId(entityId);
        f.setCategory(category);
        f.setOriginalName(sanitizeName(file.getOriginalFilename()));
        f.setStoredName(storedName);
        f.setContentType(type);
        f.setSizeBytes(file.getSize());
        f.setUploadedBy(user.userId());
        f.setCreatedAt(Instant.now());
        return repository.save(f);
    }

    @Transactional(readOnly = true)
    public FileAttachment getAccessible(Long id) {
        AuthUser user = CurrentUser.get();
        FileAttachment f = repository.findById(id)
                .filter(x -> x.getOwnerId().equals(user.ownerId()))
                .orElseThrow(() -> ApiException.notFound("File"));
        if (f.getHostelId() != null) {
            hostelAccess.checkEntity(f.getHostelId(), "File");
        }
        requireViewPermission(user, f.getEntityType());
        return f;
    }

    /**
     * Files inherit the permission that protects the record they belong to, so a user who may only manage
     * maintenance cannot download a resident's ID document just because they can guess the file id.
     */
    static void requireViewPermission(AuthUser user, String entityType) {
        String needed = switch (entityType == null ? "" : entityType) {
            case "RESIDENT" -> "RESIDENT_VIEW";
            case "HOSTEL" -> "HOSTEL_VIEW";
            case "MAINTENANCE" -> "MAINTENANCE_MANAGE";
            case "PAYMENT_QR" -> "PAYMENT_VIEW";
            default -> null;
        };
        boolean allowed = needed == null ? user.isOwner() : user.has(needed);
        if (!allowed) {
            throw ApiException.forbidden("You do not have permission to view this file");
        }
    }

    @Transactional(readOnly = true)
    public List<FileDto> list(String entityType, Long entityId) {
        requireViewPermission(CurrentUser.get(), entityType);
        return repository.findByOwnerIdAndEntityTypeAndEntityIdOrderByCreatedAtDesc(
                        CurrentUser.get().ownerId(), entityType, entityId).stream()
                .filter(f -> f.getHostelId() == null || hostelAccess.canAccess(f.getHostelId()))
                .map(FileDto::from).toList();
    }

    /** Looks a file up without a signed-in user; for callers that have already authorised access by other means. */
    @Transactional(readOnly = true)
    public FileAttachment get(Long id) {
        return repository.findById(id).orElseThrow(() -> ApiException.notFound("File"));
    }

    /** Deletes the record and the stored bytes (used when an owner replaces or removes their UPI QR). */
    @Transactional
    public void remove(Long id) {
        repository.findById(id).ifPresent(f -> {
            Path p = baseDir().resolve(f.getStoredName()).normalize();
            repository.delete(f);
            try {
                Files.deleteIfExists(p);
            } catch (IOException e) {
                // The record is gone; an orphaned random-named file is harmless
            }
        });
    }

    public Resource load(FileAttachment f) {
        Path p = baseDir().resolve(f.getStoredName()).normalize();
        if (!Files.exists(p)) {
            throw ApiException.notFound("File");
        }
        return new FileSystemResource(p);
    }

    private Path baseDir() {
        return Path.of(props.uploadDir()).toAbsolutePath().normalize();
    }

    private static String sanitizeName(String name) {
        if (name == null || name.isBlank()) {
            return "file";
        }
        String base = Path.of(name.replace('\\', '/')).getFileName().toString();
        base = base.replaceAll("[^A-Za-z0-9._ -]", "_");
        return base.length() > 200 ? base.substring(0, 200) : base;
    }

    /** Determines the type from the file signature; returns null when unsupported. */
    static String detectType(MultipartFile file) {
        byte[] head = new byte[12];
        try (InputStream in = file.getInputStream()) {
            int n = in.readNBytes(head, 0, head.length);
            if (n < 4) {
                return null;
            }
        } catch (IOException e) {
            return null;
        }
        if ((head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if ((head[0] & 0xFF) == 0x89 && head[1] == 'P' && head[2] == 'N' && head[3] == 'G') {
            return "image/png";
        }
        if (head[0] == 'R' && head[1] == 'I' && head[2] == 'F' && head[3] == 'F'
                && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P') {
            return "image/webp";
        }
        if (Arrays.equals(Arrays.copyOf(head, 4), new byte[]{'%', 'P', 'D', 'F'})) {
            return "application/pdf";
        }
        return null;
    }
}
