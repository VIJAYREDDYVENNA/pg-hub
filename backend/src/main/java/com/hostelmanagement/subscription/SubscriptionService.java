package com.hostelmanagement.subscription;

import com.hostelmanagement.common.ApiException;
import com.hostelmanagement.common.AppClock;
import com.hostelmanagement.common.Money;
import com.hostelmanagement.common.audit.AuditService;
import com.hostelmanagement.security.AuthUser;
import com.hostelmanagement.security.CurrentUser;
import com.hostelmanagement.security.HostelAccessService;
import com.hostelmanagement.subscription.OwnerSubscription.AccessState;
import com.hostelmanagement.subscription.SubscriptionDtos.EventDto;
import com.hostelmanagement.subscription.SubscriptionDtos.HostelUsage;
import com.hostelmanagement.subscription.SubscriptionDtos.Limits;
import com.hostelmanagement.subscription.SubscriptionDtos.OwnerSubscriptionDto;
import com.hostelmanagement.subscription.SubscriptionDtos.PaymentDto;
import com.hostelmanagement.subscription.SubscriptionDtos.PlanDto;
import com.hostelmanagement.subscription.SubscriptionDtos.PlanRequest;
import com.hostelmanagement.subscription.SubscriptionDtos.RecordPaymentRequest;
import com.hostelmanagement.subscription.SubscriptionDtos.SubscriptionInput;
import com.hostelmanagement.subscription.SubscriptionDtos.SubscriptionSummary;
import com.hostelmanagement.subscription.SubscriptionDtos.UpdateSubscriptionRequest;
import com.hostelmanagement.subscription.SubscriptionDtos.Usage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Plans, owner subscriptions, usage and effective limits. Effective limit = the owner's override when present,
 * otherwise the plan's value, read at the moment of each check, so a Super Admin edit applies to the very next
 * request. Reducing a limit never removes data: usage above a limit is "grandfathered" and only blocks growth.
 */
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private static final Set<String> STATUSES = Set.of(OwnerSubscription.TRIAL, OwnerSubscription.ACTIVE,
            OwnerSubscription.SUSPENDED, OwnerSubscription.CANCELLED);

    private final PlanRepository plans;
    private final OwnerSubscriptionRepository subscriptions;
    private final SubscriptionEventRepository events;
    private final SubscriptionPaymentRepository payments;
    private final JdbcTemplate jdbc;
    private final AuditService auditService;
    private final HostelAccessService hostelAccess;
    private final AppClock clock;

    @Value("${app.subscription.warning-percent:80}")
    private int warningPercent;

    // ================================================================== plans (Super Admin)

    @Transactional(readOnly = true)
    public List<PlanDto> listPlans() {
        Map<Long, Long> counts = new HashMap<>();
        subscriptions.countPerPlan().forEach(r -> counts.put((Long) r[0], (Long) r[1]));
        return plans.findAllByOrderBySortOrderAscIdAsc().stream()
                .map(p -> toDto(p, counts.getOrDefault(p.getId(), 0L))).toList();
    }

    @Transactional
    public PlanDto createPlan(PlanRequest req) {
        if (req.code() == null || req.code().isBlank()) {
            throw ApiException.badRequest("A plan code is required");
        }
        if (plans.existsByCodeIgnoreCase(req.code())) {
            throw ApiException.conflict("A plan with code " + req.code() + " already exists");
        }
        SubscriptionPlan p = new SubscriptionPlan();
        p.setCode(req.code());
        applyPlan(p, req);
        plans.save(p);
        auditService.log(null, actorId(), "PLAN_CREATED", "PLAN", p.getId(), describe(p));
        return toDto(p, 0);
    }

    @Transactional
    public PlanDto updatePlan(Long id, PlanRequest req) {
        SubscriptionPlan p = plan(id);
        if (SubscriptionPlan.ARCHIVED.equals(p.getStatus())) {
            throw ApiException.conflict("An archived plan cannot be edited");
        }
        String before = describe(p);
        applyPlan(p, req);
        plans.flush();
        auditService.log(null, actorId(), "PLAN_UPDATED", "PLAN", id, before + " -> " + describe(p));
        return toDto(p, subscriptions.countByPlanId(id));
    }

    @Transactional
    public PlanDto setPlanStatus(Long id, String status) {
        SubscriptionPlan p = plan(id);
        if (SubscriptionPlan.ARCHIVED.equals(p.getStatus()) && !SubscriptionPlan.ARCHIVED.equals(status)) {
            throw ApiException.conflict("An archived plan cannot be reactivated. Create a new plan instead.");
        }
        String old = p.getStatus();
        p.setStatus(status);
        plans.flush();
        // Owners already on the plan keep it; only new assignments are affected, so no data is touched
        auditService.log(null, actorId(), "PLAN_STATUS_CHANGED", "PLAN", id, p.getCode() + " " + old + " -> " + status);
        return toDto(p, subscriptions.countByPlanId(id));
    }

    private void applyPlan(SubscriptionPlan p, PlanRequest req) {
        if (!req.customPricing() && req.monthlyPrice() == null) {
            throw ApiException.badRequest("Enter a monthly price, or mark the plan as custom-priced");
        }
        Map<String, Boolean> entitlements = new HashMap<>();
        if (req.entitlements() != null) {
            for (var e : req.entitlements().entrySet()) {
                if (!Features.isKnown(e.getKey())) {
                    throw ApiException.badRequest("Unknown feature " + e.getKey());
                }
                entitlements.put(e.getKey(), Boolean.TRUE.equals(e.getValue()));
            }
        }
        Features.all().forEach(f -> entitlements.putIfAbsent(f.code(), Boolean.FALSE));
        p.setName(req.name().trim());
        p.setDescription(req.description() == null || req.description().isBlank() ? null : req.description().trim());
        p.setMonthlyPrice(req.monthlyPrice() == null ? null : Money.of(req.monthlyPrice()));
        p.setAnnualPrice(req.annualPrice() == null ? null : Money.of(req.annualPrice()));
        p.setCurrency(req.currency() == null ? "INR" : req.currency());
        p.setMaxHostels(req.maxHostels());
        p.setMaxBedsPerHostel(req.maxBedsPerHostel());
        p.setMaxManagers(req.maxManagers());
        p.setTrialDays(req.trialDays());
        p.setGraceDays(req.graceDays());
        p.setCustomPricing(req.customPricing());
        p.setSortOrder(req.sortOrder() == null ? 100 : req.sortOrder());
        p.getEntitlements().clear();
        p.getEntitlements().putAll(entitlements);
    }

    private PlanDto toDto(SubscriptionPlan p, long subscribers) {
        return new PlanDto(p.getId(), p.getCode(), p.getName(), p.getDescription(), p.getMonthlyPrice(),
                p.getAnnualPrice(), p.getCurrency(), p.getBillingInterval(), p.getMaxHostels(), p.getMaxBedsPerHostel(),
                p.getMaxManagers(), p.getTrialDays(), p.getGraceDays(), p.isCustomPricing(), p.getStatus(),
                p.getSortOrder(), new LinkedHashMap<>(new java.util.TreeMap<>(p.getEntitlements())), subscribers);
    }

    private static String describe(SubscriptionPlan p) {
        return p.getCode() + " price=" + p.getMonthlyPrice() + " hostels=" + p.getMaxHostels() + " beds/hostel="
                + p.getMaxBedsPerHostel() + " managers=" + p.getMaxManagers() + " trial=" + p.getTrialDays()
                + " grace=" + p.getGraceDays() + " status=" + p.getStatus();
    }

    private SubscriptionPlan plan(Long id) {
        return plans.findById(id).orElseThrow(() -> ApiException.notFound("Plan"));
    }

    // ================================================================== owner subscription lifecycle

    /**
     * Creates the owner's one and only subscription. Called inside the owner-creation transaction, so an owner can
     * never exist half-initialised. Dates and limits default from the plan; any explicit value is an override.
     */
    @Transactional
    public OwnerSubscription initialize(Long ownerId, SubscriptionInput in, Long actorUserId) {
        if (in == null) {
            throw ApiException.badRequest("A subscription plan is required");
        }
        SubscriptionPlan plan = plans.findById(in.planId()).orElseThrow(() -> ApiException.badRequest("The selected plan does not exist"));
        requireAssignable(plan);
        if (subscriptions.findByOwnerId(ownerId).isPresent()) {
            throw ApiException.conflict("This owner already has a subscription");
        }
        LocalDate today = clock.today();
        LocalDate start = in.startsOn() == null ? today : in.startsOn();
        int trialDays = in.trialDays() == null ? plan.getTrialDays() : in.trialDays();
        OwnerSubscription s = new OwnerSubscription();
        s.setOwnerId(ownerId);
        s.setPlanId(plan.getId());
        s.setStartsOn(start);
        s.setGraceDays(in.graceDays() == null ? plan.getGraceDays() : in.graceDays());
        s.setCustomMaxHostels(in.customMaxHostels());
        s.setCustomMaxBedsPerHostel(in.customMaxBedsPerHostel());
        s.setCustomMaxManagers(in.customMaxManagers());
        s.setCustomMonthlyPrice(in.customMonthlyPrice() == null ? null : Money.of(in.customMonthlyPrice()));
        if (in.expiresOn() != null && in.expiresOn().isBefore(start)) {
            throw ApiException.badRequest("The expiry date cannot be before the start date");
        }
        if (trialDays > 0) {
            s.setStatus(OwnerSubscription.TRIAL);
            s.setTrialEndsOn(in.expiresOn() != null ? in.expiresOn() : start.plusDays(trialDays));
            s.setBillingStatus(OwnerSubscription.BILLING_NOT_REQUIRED);
        } else {
            s.setStatus(OwnerSubscription.ACTIVE);
            s.setPeriodEndsOn(in.expiresOn() != null ? in.expiresOn() : start.plusMonths(1));
            s.setBillingStatus(OwnerSubscription.BILLING_UNPAID);
        }
        subscriptions.save(s);
        String details = "plan=" + plan.getCode() + " status=" + s.getStatus() + " start=" + start + " ends=" + s.endsOn()
                + " grace=" + s.getGraceDays() + " limits=" + limitsText(effective(s, plan)) + (overrides(s) ? " (custom)" : "");
        recordEvent(ownerId, "CREATED", details, actorUserId);
        auditService.log(ownerId, actorUserId, "SUBSCRIPTION_CREATED", "SUBSCRIPTION", s.getId(), details);
        return s;
    }

    /**
     * Grandfathers an owner that predates subscriptions (or was created outside the Super Admin flow). Never reduces
     * what the owner already has: each limit is at least the owner's current usage.
     */
    @Transactional
    public OwnerSubscription grandfather(Long ownerId) {
        SubscriptionPlan plan = plans.findByCode("ENTERPRISE")
                .orElseThrow(() -> new IllegalStateException("The ENTERPRISE plan is missing; cannot grandfather owner " + ownerId));
        Usage usage = usage(ownerId, false);
        int maxBeds = usage.hostelBeds().stream().mapToInt(HostelUsage::beds).max().orElse(0);
        OwnerSubscription s = new OwnerSubscription();
        s.setOwnerId(ownerId);
        s.setPlanId(plan.getId());
        s.setStatus(OwnerSubscription.ACTIVE);
        s.setStartsOn(clock.today());
        s.setPeriodEndsOn(null);
        s.setGraceDays(plan.getGraceDays());
        s.setCustomMaxHostels(usage.hostels() > plan.getMaxHostels() ? usage.hostels() : null);
        s.setCustomMaxBedsPerHostel(maxBeds > plan.getMaxBedsPerHostel() ? maxBeds : null);
        s.setCustomMaxManagers(usage.managers() > plan.getMaxManagers() ? usage.managers() : null);
        s.setBillingStatus(OwnerSubscription.BILLING_NOT_REQUIRED);
        subscriptions.save(s);
        String details = "grandfathered on " + plan.getCode() + " with no expiry; usage hostels=" + usage.hostels()
                + " managers=" + usage.managers() + " largest hostel beds=" + maxBeds;
        recordEvent(ownerId, "GRANDFATHERED", details, null);
        auditService.log(ownerId, null, "SUBSCRIPTION_CREATED", "SUBSCRIPTION", s.getId(), details);
        return s;
    }

    @Transactional
    public OwnerSubscriptionDto update(Long ownerId, UpdateSubscriptionRequest req) {
        OwnerSubscription s = subscriptions.lockByOwnerId(ownerId).orElseThrow(() -> ApiException.notFound("Subscription"));
        SubscriptionPlan oldPlan = plan(s.getPlanId());
        SubscriptionPlan newPlan = plans.findById(req.planId()).orElseThrow(() -> ApiException.badRequest("The selected plan does not exist"));
        if (!newPlan.getId().equals(oldPlan.getId())) {
            requireAssignable(newPlan);
        }
        if (!STATUSES.contains(req.status())) {
            throw ApiException.badRequest("Unknown subscription status");
        }
        if (OwnerSubscription.TRIAL.equals(req.status()) && req.trialEndsOn() == null) {
            throw ApiException.badRequest("A trial needs an end date");
        }
        if (req.trialEndsOn() != null && req.trialEndsOn().isBefore(req.startsOn())) {
            throw ApiException.badRequest("The trial end date cannot be before the start date");
        }
        if (req.periodEndsOn() != null && req.periodEndsOn().isBefore(req.startsOn())) {
            throw ApiException.badRequest("The expiry date cannot be before the start date");
        }
        Limits beforeLimits = effective(s, oldPlan);
        String beforeState = state(s, oldPlan);

        s.setPlanId(newPlan.getId());
        s.setStatus(req.status());
        s.setStartsOn(req.startsOn());
        s.setTrialEndsOn(req.trialEndsOn());
        s.setPeriodEndsOn(req.periodEndsOn());
        s.setGraceDays(req.graceDays());
        s.setCustomMaxHostels(req.customMaxHostels());
        s.setCustomMaxBedsPerHostel(req.customMaxBedsPerHostel());
        s.setCustomMaxManagers(req.customMaxManagers());
        s.setCustomMonthlyPrice(req.customMonthlyPrice() == null ? null : Money.of(req.customMonthlyPrice()));
        subscriptions.flush();

        Limits afterLimits = effective(s, newPlan);
        String afterState = state(s, newPlan);
        List<String> changes = new ArrayList<>();
        if (!oldPlan.getId().equals(newPlan.getId())) {
            changes.add("plan " + oldPlan.getCode() + " -> " + newPlan.getCode());
            recordEvent(ownerId, "PLAN_CHANGED", oldPlan.getCode() + " -> " + newPlan.getCode(), actorId());
        }
        if (!beforeLimits.equals(afterLimits)) {
            changes.add("limits " + limitsText(beforeLimits) + " -> " + limitsText(afterLimits));
            recordEvent(ownerId, "LIMITS_CHANGED", limitsText(beforeLimits) + " -> " + limitsText(afterLimits)
                    + (overrides(s) ? " (custom overrides in effect)" : ""), actorId());
        }
        if (!Objects.equals(beforeState, afterState)) {
            changes.add("state " + beforeState + " -> " + afterState);
            recordEvent(ownerId, "STATUS_CHANGED", beforeState + " -> " + afterState
                    + (req.reason() == null || req.reason().isBlank() ? "" : ": " + req.reason().trim()), actorId());
        }
        auditService.log(ownerId, actorId(), "SUBSCRIPTION_UPDATED", "SUBSCRIPTION", s.getId(),
                changes.isEmpty() ? "no effective change" : String.join("; ", changes));
        return detail(ownerId);
    }

    @Transactional
    public OwnerSubscriptionDto recordPayment(Long ownerId, RecordPaymentRequest req) {
        OwnerSubscription s = subscriptions.lockByOwnerId(ownerId).orElseThrow(() -> ApiException.notFound("Subscription"));
        LocalDate today = clock.today();
        if (req.paidOn().isAfter(today)) {
            throw ApiException.badRequest("The payment date cannot be in the future");
        }
        SubscriptionPayment p = new SubscriptionPayment();
        p.setOwnerId(ownerId);
        p.setAmount(Money.of(req.amount()));
        p.setPaidOn(req.paidOn());
        p.setMethod(req.method());
        p.setReference(blank(req.reference()));
        p.setNotes(blank(req.notes()));
        p.setRecordedBy(actorId());
        // Only an explicitly recorded payment renews anything. Nothing here is driven by a provider callback.
        if (req.extendMonths() > 0) {
            LocalDate base = s.getPeriodEndsOn() == null || s.getPeriodEndsOn().isBefore(today) ? today : s.getPeriodEndsOn();
            LocalDate newEnd = base.plusMonths(req.extendMonths());
            s.setPeriodEndsOn(newEnd);
            p.setExtendedTo(newEnd);
            if (OwnerSubscription.TRIAL.equals(s.getStatus())) {
                s.setStatus(OwnerSubscription.ACTIVE);
            }
        }
        s.setBillingStatus(OwnerSubscription.BILLING_PAID);
        payments.save(p);
        subscriptions.flush();
        String details = req.amount().toPlainString() + " via " + req.method() + (p.getExtendedTo() == null ? ""
                : ", paid up to " + p.getExtendedTo());
        recordEvent(ownerId, "PAYMENT_RECORDED", details, actorId());
        auditService.log(ownerId, actorId(), "SUBSCRIPTION_PAYMENT_RECORDED", "SUBSCRIPTION", s.getId(), details);
        return detail(ownerId);
    }

    private void requireAssignable(SubscriptionPlan plan) {
        if (!SubscriptionPlan.ACTIVE.equals(plan.getStatus())) {
            throw ApiException.badRequest("The plan " + plan.getName() + " is " + plan.getStatus().toLowerCase(Locale.ROOT)
                    + " and cannot be assigned to owners");
        }
    }

    // ================================================================== reading

    @Transactional(readOnly = true)
    public OwnerSubscriptionDto detail(Long ownerId) {
        OwnerSubscription s = subscriptions.findByOwnerId(ownerId).orElseThrow(() -> ApiException.notFound("Subscription"));
        SubscriptionPlan plan = plan(s.getPlanId());
        LocalDate today = clock.today();
        Usage usage = usage(ownerId, false);
        Limits eff = effective(s, plan);
        BigDecimal price = s.getCustomMonthlyPrice() != null ? s.getCustomMonthlyPrice() : plan.getMonthlyPrice();
        List<EventDto> eventDtos = events.findTop50ByOwnerIdOrderByIdDesc(ownerId).stream()
                .map(e -> new EventDto(e.getId(), e.getEventType(), e.getDetails(), e.getActorUserId(),
                        actorName(e.getActorUserId()), e.getCreatedAt())).toList();
        List<PaymentDto> paymentDtos = payments.findTop50ByOwnerIdOrderByIdDesc(ownerId).stream()
                .map(p -> new PaymentDto(p.getId(), p.getAmount(), p.getPaidOn(), p.getMethod(), p.getReference(),
                        p.getNotes(), p.getExtendedTo(), p.getRecordedBy(), p.getCreatedAt())).toList();
        return new OwnerSubscriptionDto(ownerId, toDto(plan, subscriptions.countByPlanId(plan.getId())), s.getStatus(),
                s.accessState(today).name(), s.getStartsOn(), s.getTrialEndsOn(), s.getPeriodEndsOn(), s.graceEndsOn(),
                s.getGraceDays(), s.getBillingStatus(), s.getCustomMaxHostels(), s.getCustomMaxBedsPerHostel(),
                s.getCustomMaxManagers(), s.getCustomMonthlyPrice(), price, planLimits(plan), eff, overrides(s), usage,
                overLimit(usage, eff), new LinkedHashMap<>(new java.util.TreeMap<>(plan.getEntitlements())), eventDtos,
                paymentDtos);
    }

    /** The calling owner's own view (any tenant user): plan, dates, limits, usage and warnings. */
    @Transactional(readOnly = true)
    public SubscriptionSummary summaryForCurrentOwner() {
        AuthUser me = CurrentUser.get();
        Long ownerId = me.ownerId();
        OwnerSubscription s = subscriptions.findByOwnerId(ownerId).orElseThrow(() -> ApiException.notFound("Subscription"));
        SubscriptionPlan plan = plan(s.getPlanId());
        LocalDate today = clock.today();
        Limits eff = effective(s, plan);
        Usage all = usage(ownerId, false);
        // Per-hostel bed usage is limited to the hostels this user may see
        Set<Long> visible = me.isOwner() ? null : hostelAccess.accessibleHostelIds();
        Usage usage = visible == null ? all : new Usage(all.hostels(), all.managers(),
                all.hostelBeds().stream().filter(h -> visible.contains(h.hostelId())).toList());
        AccessState state = s.accessState(today);
        LocalDate end = s.endsOn();
        Long days = end == null ? null : ChronoUnit.DAYS.between(today, end);
        return new SubscriptionSummary(plan.getCode(), plan.getName(), s.getStatus(), state.name(), s.getTrialEndsOn(),
                s.getPeriodEndsOn(), s.graceEndsOn(), days, eff, usage, state != AccessState.RESTRICTED, warningPercent,
                warnings(s, plan, state, eff, usage, days), new LinkedHashMap<>(new java.util.TreeMap<>(plan.getEntitlements())));
    }

    private List<String> warnings(OwnerSubscription s, SubscriptionPlan plan, AccessState state, Limits eff, Usage usage, Long days) {
        List<String> w = new ArrayList<>();
        if (state == AccessState.RESTRICTED) {
            w.add("Your subscription is not active. Your records stay available, but you cannot add hostels, beds or "
                    + "managers until it is renewed. Contact the platform administrator.");
        } else if (state == AccessState.GRACE) {
            w.add("Your subscription period has ended. You can keep adding resources until " + s.graceEndsOn()
                    + ", after which new hostels, beds and managers are paused. Contact the platform administrator to renew.");
        } else if (days != null && days <= 7 && days >= 0) {
            w.add((OwnerSubscription.TRIAL.equals(s.getStatus()) ? "Your trial" : "Your subscription") + " ends in "
                    + days + " day(s).");
        }
        warnIfNear(w, "Hostels", usage.hostels(), eff.maxHostels());
        usage.hostelBeds().forEach(h -> warnIfNear(w, "Beds in " + h.hostelName(), h.beds(), eff.maxBedsPerHostel()));
        warnIfNear(w, "Manager accounts", usage.managers(), eff.maxManagers());
        return w;
    }

    private void warnIfNear(List<String> out, String what, int used, int limit) {
        if (limit <= 0) {
            return;
        }
        if (used > limit) {
            out.add(what + ": " + used + " / " + limit + " (over the limit; existing records are kept, but you cannot add more).");
        } else if (used == limit) {
            out.add(what + ": " + used + " / " + limit + ". Limit reached; contact the platform administrator to upgrade.");
        } else if (used * 100 >= (long) limit * warningPercent) {
            out.add(what + ": " + used + " / " + limit + " used.");
        }
    }

    // ================================================================== limits and usage

    public Limits planLimits(SubscriptionPlan plan) {
        return new Limits(plan.getMaxHostels(), plan.getMaxBedsPerHostel(), plan.getMaxManagers());
    }

    public Limits effective(OwnerSubscription s, SubscriptionPlan plan) {
        return new Limits(
                s.getCustomMaxHostels() != null ? s.getCustomMaxHostels() : plan.getMaxHostels(),
                s.getCustomMaxBedsPerHostel() != null ? s.getCustomMaxBedsPerHostel() : plan.getMaxBedsPerHostel(),
                s.getCustomMaxManagers() != null ? s.getCustomMaxManagers() : plan.getMaxManagers());
    }

    public static boolean overrides(OwnerSubscription s) {
        return s.getCustomMaxHostels() != null || s.getCustomMaxBedsPerHostel() != null || s.getCustomMaxManagers() != null;
    }

    private static List<String> overLimit(Usage usage, Limits eff) {
        List<String> over = new ArrayList<>();
        if (usage.hostels() > eff.maxHostels()) {
            over.add("hostels " + usage.hostels() + " / " + eff.maxHostels());
        }
        if (usage.managers() > eff.maxManagers()) {
            over.add("managers " + usage.managers() + " / " + eff.maxManagers());
        }
        usage.hostelBeds().stream().filter(h -> h.beds() > eff.maxBedsPerHostel())
                .forEach(h -> over.add(h.hostelName() + " beds " + h.beds() + " / " + eff.maxBedsPerHostel()));
        return over;
    }

    /**
     * Usage of a resource. {@code lock = true} uses locking reads (FOR SHARE), which see the latest committed rows
     * even inside a REPEATABLE READ transaction; the limit checks need that so two concurrent creators cannot both
     * count the same stale total.
     */
    public int countHostels(Long ownerId, boolean lock) {
        return count("SELECT COUNT(*) FROM hostels WHERE owner_id = ? AND deleted_at IS NULL", lock, ownerId);
    }

    /** Every non-deleted bed of non-deleted rooms, whatever its status (inactive and maintenance beds count). */
    public int countBeds(Long hostelId, boolean lock) {
        return count("""
                SELECT COUNT(*) FROM beds b JOIN rooms r ON r.id = b.room_id
                WHERE r.hostel_id = ? AND b.deleted_at IS NULL AND r.deleted_at IS NULL""", lock, hostelId);
    }

    /** Enabled non-owner logins, counted once each however many hostels they manage. */
    public int countManagers(Long ownerId, boolean lock) {
        return count("""
                SELECT COUNT(*) FROM users u WHERE u.owner_id = ? AND u.enabled = TRUE AND NOT EXISTS (
                    SELECT 1 FROM user_roles ur JOIN roles r ON r.id = ur.role_id WHERE ur.user_id = u.id AND r.code = 'OWNER')""",
                lock, ownerId);
    }

    private int count(String sql, boolean lock, Object arg) {
        Integer n = jdbc.queryForObject(lock ? sql + " FOR SHARE" : sql, Integer.class, arg);
        return n == null ? 0 : n;
    }

    public Usage usage(Long ownerId, boolean lock) {
        List<HostelUsage> beds = jdbc.query("SELECT id, name FROM hostels WHERE owner_id = ? AND deleted_at IS NULL ORDER BY name",
                (rs, i) -> new HostelUsage(rs.getLong("id"), rs.getString("name"), 0), ownerId).stream()
                .map(h -> new HostelUsage(h.hostelId(), h.hostelName(), countBeds(h.hostelId(), lock))).toList();
        return new Usage(beds.size(), countManagers(ownerId, lock), beds);
    }

    // ================================================================== helpers

    /** Locks the owner's subscription row for the rest of the caller's transaction and returns it with its plan. */
    OwnerSubscription lock(Long ownerId) {
        return subscriptions.lockByOwnerId(ownerId).orElseThrow(() -> new ApiException(
                org.springframework.http.HttpStatus.FORBIDDEN, "SUBSCRIPTION_MISSING",
                "No subscription is set up for this account. Contact the platform administrator."));
    }

    SubscriptionPlan planOf(OwnerSubscription s) {
        return plan(s.getPlanId());
    }

    private String state(OwnerSubscription s, SubscriptionPlan plan) {
        return s.getStatus() + (s.endsOn() == null ? "" : " until " + s.endsOn());
    }

    private static String limitsText(Limits l) {
        return l.maxHostels() + " hostels / " + l.maxBedsPerHostel() + " beds per hostel / " + l.maxManagers() + " managers";
    }

    private void recordEvent(Long ownerId, String type, String details, Long actor) {
        SubscriptionEvent e = new SubscriptionEvent();
        e.setOwnerId(ownerId);
        e.setEventType(type);
        e.setDetails(details == null ? null : details.length() > 1000 ? details.substring(0, 1000) : details);
        e.setActorUserId(actor);
        events.save(e);
    }

    private String actorName(Long userId) {
        if (userId == null) {
            return null;
        }
        List<String> names = jdbc.queryForList("SELECT full_name FROM users WHERE id = ?", String.class, userId);
        return names.isEmpty() ? null : names.get(0);
    }

    private static Long actorId() {
        AuthUser u = CurrentUser.getOrNull();
        return u == null ? null : u.userId();
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /** Names of the entitled features of an owner's plan, for services that gate features. */
    @Transactional(readOnly = true)
    public Set<String> entitledFeatures(Long ownerId) {
        OwnerSubscription s = subscriptions.findByOwnerId(ownerId).orElse(null);
        if (s == null) {
            return Set.of();
        }
        return plan(s.getPlanId()).getEntitlements().entrySet().stream().filter(e -> Boolean.TRUE.equals(e.getValue()))
                .map(Map.Entry::getKey).collect(Collectors.toSet());
    }
}
