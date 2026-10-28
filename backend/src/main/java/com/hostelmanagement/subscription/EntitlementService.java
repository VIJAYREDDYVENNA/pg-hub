package com.hostelmanagement.subscription;

import com.hostelmanagement.common.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Central feature gate. Callers ask "may this owner use feature X?" and never look at plan names, so what a plan
 * includes can change in the database without touching code.
 */
@Service
@RequiredArgsConstructor
public class EntitlementService {

    private final SubscriptionService subscriptions;

    public boolean isEnabled(Long ownerId, String feature) {
        return subscriptions.entitledFeatures(ownerId).contains(feature);
    }

    public void require(Long ownerId, String feature) {
        if (!isEnabled(ownerId, feature)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FEATURE_NOT_IN_PLAN",
                    "This feature is not included in your plan. Contact the platform administrator to upgrade.");
        }
    }
}
