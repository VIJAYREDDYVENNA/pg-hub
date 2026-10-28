package com.hostelmanagement.notification;

import com.hostelmanagement.common.PageRequests;
import com.hostelmanagement.common.PageResponse;
import com.hostelmanagement.common.audit.AuditService;
import com.hostelmanagement.hostel.HostelRepository;
import com.hostelmanagement.notification.whatsapp.WhatsAppService;
import com.hostelmanagement.security.AuthUser;
import com.hostelmanagement.security.CurrentUser;
import com.hostelmanagement.security.HostelAccessService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@Tag(name = "Announcements")
@RestController
@RequestMapping("/api/announcements")
@RequiredArgsConstructor
public class AnnouncementController {

    private final AnnouncementRepository repository;
    private final HostelRepository hostelRepository;
    private final HostelAccessService hostelAccess;
    private final NotificationService notifications;
    private final WhatsAppService whatsApp;
    private final AuditService auditService;

    public record AnnouncementRequest(Long hostelId, @NotBlank @Size(max = 200) String title,
                                      @NotBlank @Size(max = 2000) String body, boolean sendWhatsApp) {
    }

    public record AnnouncementDto(Long id, Long hostelId, String hostelName, String title, String body,
                                  boolean sendWhatsapp, Instant createdAt, Integer whatsappQueued,
                                  Integer whatsappSkipped) {
    }

    @GetMapping
    @Transactional(readOnly = true)
    public PageResponse<AnnouncementDto> list(@RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequests.of(page, size, null, Set.of(), Sort.by(Sort.Direction.DESC, "createdAt"));
        var scope = hostelAccess.scope((Long) null);
        return PageResponse.of(repository.visible(CurrentUser.get().ownerId(), scope, pageable),
                a -> toDto(a, null, null));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PERM_NOTIFICATION_SEND')")
    @Transactional
    public AnnouncementDto create(@Valid @RequestBody AnnouncementRequest req) {
        AuthUser user = CurrentUser.get();
        if (req.hostelId() == null && !user.isOwner()) {
            throw com.hostelmanagement.common.ApiException.badRequest("Select a hostel for the announcement");
        }
        List<Long> hostels = hostelAccess.scope(req.hostelId());
        Announcement a = new Announcement();
        a.setOwnerId(user.ownerId());
        a.setHostelId(req.hostelId());
        a.setTitle(req.title().trim());
        a.setBody(req.body().trim());
        a.setSendWhatsapp(req.sendWhatsApp());
        a.setCreatedBy(user.userId());
        repository.save(a);
        for (Long hid : hostels) {
            notifications.notifyHostel(user.ownerId(), hid, NotificationTypes.ANNOUNCEMENT, a.getTitle(), a.getBody(),
                    "/announcements", "announcement-" + a.getId() + "-" + hid);
        }
        int[] wa = {0, 0};
        if (req.sendWhatsApp()) {
            wa = whatsApp.broadcastAnnouncement(user, hostels, a.getTitle(), a.getBody(), a.getId());
        }
        auditService.log("ANNOUNCEMENT_CREATED", "ANNOUNCEMENT", a.getId(), a.getTitle());
        return toDto(a, req.sendWhatsApp() ? wa[0] : null, req.sendWhatsApp() ? wa[1] : null);
    }

    private AnnouncementDto toDto(Announcement a, Integer queued, Integer skipped) {
        String hostelName = a.getHostelId() == null ? "All hostels"
                : hostelRepository.findById(a.getHostelId()).map(h -> h.getName()).orElse(null);
        return new AnnouncementDto(a.getId(), a.getHostelId(), hostelName, a.getTitle(), a.getBody(),
                a.isSendWhatsapp(), a.getCreatedAt(), queued, skipped);
    }
}
