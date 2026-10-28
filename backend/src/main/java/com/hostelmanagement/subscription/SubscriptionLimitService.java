package com.hostelmanagement.subscription;

import com.hostelmanagement.common.ApiException;
import com.hostelmanagement.common.AppClock;
import com.hostelmanagement.subscription.OwnerSubscription.AccessState;
import com.hostelmanagement.subscription.SubscriptionDtos.Limits;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;


/**
 * The single place that decides whether an owner may create another hostel, bed or manager account.
 *
 * <p>Concurrency: every check first takes a row lock on the owner's subscription ({@code SELECT ... FOR UPDATE}),
 * which serialises all billable creations of that owner, then counts with locking reads so the total is current.
 * Callers must already be in a transaction (propagation MANDATORY), so the check and the insert commit together.
 *
 * <p>Policy: usage above a limit (a grandfathered owner, or a limit that was lowered) is never touched; it simply
 * blocks growth. An owner whose subscription is past its grace period (or suspended/cancelled) is RESTRICTED: no new
 * billable resources, but everything that exists stays readable and payments can still be recorded.
 */
@Service
@RequiredArgsConstructor
public class SubscriptionLimitService {

    public static final String LIMIT_REACHED = "PLAN_LIMIT_REACHED";
    public static final String RESTRICTED = "SUBSCRIPTION_RESTRICTED";

    private final SubscriptionService subscriptions;
    private final JdbcTemplate jdbc;
    private final AppClock clock;

    @Transactional(propagation = Propagation.MANDATORY)
    public void assertCanAddHostel(Long ownerId) {
        Context c = begin(ownerId);
        int used = subscriptions.countHostels(ownerId, true);
        if (used + 1 > c.limits().maxHostels()) {
            throw limit("Your " + c.planName() + " plan allows " + plural(c.limits().maxHostels(), "hostel") + " and "
                    + used + " " + (used == 1 ? "is" : "are") + " already in use.");
        }
    }

    /** @param count how many beds are about to be added to {@code hostelId} (a whole room's worth, or 1). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void assertCanAddBeds(Long ownerId, Long hostelId, int count) {
        if (count <= 0) {
            return;
        }
        Context c = begin(ownerId);
        int used = subscriptions.countBeds(hostelId, true);
        if (used + count > c.limits().maxBedsPerHostel()) {
            String name = jdbc.queryForObject("SELECT name FROM hostels WHERE id = ?", String.class, hostelId);
            throw limit("Your " + c.planName() + " plan allows " + plural(c.limits().maxBedsPerHostel(), "bed")
                    + " per hostel and " + name + " already has " + used + (count > 1 ? ", so " + count + " more do not fit." : "."));
        }
    }

    /** Called when a manager login is created or re-enabled. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void assertCanEnableManager(Long ownerId) {
        Context c = begin(ownerId);
        int used = subscriptions.countManagers(ownerId, true);
        if (used + 1 > c.limits().maxManagers()) {
            throw limit("Your " + c.planName() + " plan allows " + plural(c.limits().maxManagers(), "manager account")
                    + " and " + used + " " + (used == 1 ? "is" : "are") + " already enabled.");
        }
    }

    private Context begin(Long ownerId) {
        OwnerSubscription s = subscriptions.lock(ownerId);
        SubscriptionPlan plan = subscriptions.planOf(s);
        AccessState state = s.accessState(clock.today());
        if (state == AccessState.RESTRICTED) {
            throw new ApiException(HttpStatus.FORBIDDEN, RESTRICTED,
                    "Your subscription is not active, so new hostels, beds and manager accounts are paused. Your existing "
                            + "records are unaffected. Contact the platform administrator to renew.");
        }
        return new Context(plan.getName(), subscriptions.effective(s, plan));
    }

    private static ApiException limit(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, LIMIT_REACHED,
                message + " Contact the platform administrator to upgrade your plan.");
    }

    private static String plural(int n, String noun) {
        return n + " " + noun + (n == 1 ? "" : "s");
    }

    private record Context(String planName, Limits limits) {
    }

}
