package com.hostelmanagement.notification;

import com.hostelmanagement.common.PageResponse;
import com.hostelmanagement.notification.NotificationService.NotificationDto;
import com.hostelmanagement.notification.NotificationService.PreferenceDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "Notifications")
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService service;

    @GetMapping
    public PageResponse<NotificationDto> list(@RequestParam(defaultValue = "false") boolean unreadOnly,
                                              @RequestParam(required = false) Long hostelId,
                                              @RequestParam(required = false) String type,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return service.list(unreadOnly, hostelId, type, page, size);
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount() {
        return Map.of("count", service.unreadCount());
    }

    @PostMapping("/{id}/read")
    public Map<String, String> read(@PathVariable Long id) {
        service.markRead(id);
        return Map.of("status", "ok");
    }

    @PostMapping("/read-all")
    public Map<String, Integer> readAll() {
        return Map.of("updated", service.markAllRead());
    }

    @GetMapping("/preferences")
    public List<PreferenceDto> preferences() {
        return service.preferences();
    }

    @PutMapping("/preferences")
    public List<PreferenceDto> updatePreferences(@RequestBody List<PreferenceDto> prefs) {
        return service.updatePreferences(prefs);
    }
}
