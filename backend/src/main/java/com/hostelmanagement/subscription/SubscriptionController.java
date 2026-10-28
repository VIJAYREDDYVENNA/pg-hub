package com.hostelmanagement.subscription;

import com.hostelmanagement.subscription.SubscriptionDtos.SubscriptionSummary;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in owner's / manager's own plan, limits and usage (read-only). */
@Tag(name = "Subscription")
@RestController
@RequestMapping("/api/subscription")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService service;

    @GetMapping
    @PreAuthorize("!hasRole('SUPER_ADMIN')")
    public SubscriptionSummary mine() {
        return service.summaryForCurrentOwner();
    }
}
