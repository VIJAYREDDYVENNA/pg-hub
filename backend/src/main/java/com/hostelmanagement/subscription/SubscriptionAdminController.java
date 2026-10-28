package com.hostelmanagement.subscription;

import com.hostelmanagement.subscription.SubscriptionDtos.FeatureDto;
import com.hostelmanagement.subscription.SubscriptionDtos.OwnerSubscriptionDto;
import com.hostelmanagement.subscription.SubscriptionDtos.PlanDto;
import com.hostelmanagement.subscription.SubscriptionDtos.PlanRequest;
import com.hostelmanagement.subscription.SubscriptionDtos.PlanStatusRequest;
import com.hostelmanagement.subscription.SubscriptionDtos.RecordPaymentRequest;
import com.hostelmanagement.subscription.SubscriptionDtos.UpdateSubscriptionRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Plan catalogue and per-owner subscription administration. Super Admin only. */
@Tag(name = "Subscription administration (super admin)")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class SubscriptionAdminController {

    private final SubscriptionService service;

    @GetMapping("/plans")
    public List<PlanDto> plans() {
        return service.listPlans();
    }

    @GetMapping("/plans/features")
    public List<FeatureDto> features() {
        return Features.all();
    }

    @PostMapping("/plans")
    @ResponseStatus(HttpStatus.CREATED)
    public PlanDto createPlan(@Valid @RequestBody PlanRequest req) {
        return service.createPlan(req);
    }

    @PutMapping("/plans/{id}")
    public PlanDto updatePlan(@PathVariable Long id, @Valid @RequestBody PlanRequest req) {
        return service.updatePlan(id, req);
    }

    @PutMapping("/plans/{id}/status")
    public PlanDto planStatus(@PathVariable Long id, @Valid @RequestBody PlanStatusRequest req) {
        return service.setPlanStatus(id, req.status());
    }

    @GetMapping("/owners/{ownerId}/subscription")
    public OwnerSubscriptionDto subscription(@PathVariable Long ownerId) {
        return service.detail(ownerId);
    }

    @PutMapping("/owners/{ownerId}/subscription")
    public OwnerSubscriptionDto updateSubscription(@PathVariable Long ownerId,
                                                   @Valid @RequestBody UpdateSubscriptionRequest req) {
        return service.update(ownerId, req);
    }

    @PostMapping("/owners/{ownerId}/subscription/payments")
    @ResponseStatus(HttpStatus.CREATED)
    public OwnerSubscriptionDto recordPayment(@PathVariable Long ownerId, @Valid @RequestBody RecordPaymentRequest req) {
        return service.recordPayment(ownerId, req);
    }
}
