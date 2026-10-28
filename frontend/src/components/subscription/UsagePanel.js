import useApi from '../../hooks/useApi';
import { subscriptionApi } from '../../services/endpoints';
import Icon from '../common/Icon';
import StatusBadge from '../common/StatusBadge';
import { SUBSCRIPTION_STATE } from '../../utils/status';
import { limitReachedMessage, usageLevel, usageText } from '../../utils/subscriptionLogic.mjs';
import { date, label } from '../../utils/format';

/** The signed-in owner's / manager's plan, limits and usage. Failure to load must never block the page it decorates. */
export function useSubscription() {
  const q = useApi(() => subscriptionApi.mine(), []);
  return { summary: q.data, reload: q.reload };
}

const LEVEL_BADGE = { ok: 'text-bg-light border', warn: 'text-bg-warning', full: 'text-bg-danger', over: 'text-bg-danger' };

/** "Hostels: 2 / 3" with colour and an icon for warn / full / over so colour is never the only cue. */
export function UsageChip({ label: what, used, limit, warnPercent = 80, className = '' }) {
  const level = usageLevel(used, limit, warnPercent);
  const icon = level === 'ok' ? null : 'bi-exclamation-triangle-fill';
  return (
    <span className={`badge fs-6 fw-medium ${LEVEL_BADGE[level]} ${className}`}
      title={level === 'full' ? 'Limit reached' : level === 'over' ? 'Above the limit' : undefined}>
      {icon && <Icon className={`bi ${icon} me-1`} />}{what}: {usageText(used, limit)}
    </span>
  );
}

/** Whether another hostel / bed can be added right now, with the message to show when it cannot. */
export function hostelAddStatus(summary) {
  if (!summary) return { blocked: false };
  if (!summary.canAddResources) return { blocked: true, message: 'Your subscription is not active. Contact the platform administrator to renew.' };
  if (summary.usage.hostels >= summary.limits.maxHostels) {
    return { blocked: true, message: limitReachedMessage('Hostel', summary.usage.hostels, summary.limits.maxHostels) };
  }
  return { blocked: false };
}

export function bedAddStatus(summary, hostelId) {
  if (!summary) return { blocked: false };
  if (!summary.canAddResources) return { blocked: true, message: 'Your subscription is not active. Contact the platform administrator to renew.' };
  const used = summary.usage.hostelBeds.find((h) => h.hostelId === Number(hostelId))?.beds ?? 0;
  if (used >= summary.limits.maxBedsPerHostel) {
    return { blocked: true, message: limitReachedMessage('Bed', used, summary.limits.maxBedsPerHostel) };
  }
  return { blocked: false };
}

/** Warnings from the server (limits near/at/over, trial ending, grace, restricted). */
export function SubscriptionNotice({ summary, only }) {
  const warnings = (summary?.warnings || []).filter((w) => !only || only(w));
  if (warnings.length === 0) return null;
  const severe = summary.accessState === 'RESTRICTED' || warnings.some((w) => /Limit reached|over the limit/i.test(w));
  return (
    <div className={`alert ${severe ? 'alert-danger' : 'alert-warning'} py-2 small mb-3`} role="status">
      <Icon className="bi bi-info-circle me-2" />{warnings.join(' ')}
    </div>
  );
}

/** Settings card: plan, state, dates and every limit against its usage. */
export function PlanUsageCard() {
  const { summary } = useSubscription();
  if (!summary) return null;
  const { limits, usage } = summary;
  return (
    <section className="hh-card h-100" aria-labelledby="plan-usage-title">
      <h2 className="hh-card-title mb-3" id="plan-usage-title"><Icon className="bi bi-tags-fill me-2 text-primary-hh" />Plan &amp; usage</h2>
      <div className="d-flex align-items-center gap-2 mb-2">
        <span className="fw-bold fs-5">{summary.planName}</span>
        <StatusBadge map={SUBSCRIPTION_STATE} value={summary.accessState} />
      </div>
      <dl className="hh-dl mb-3">
        <dt>Status</dt><dd>{label(summary.status)}</dd>
        {summary.trialEndsOn && summary.status === 'TRIAL' && <><dt>Trial ends</dt><dd>{date(summary.trialEndsOn)}</dd></>}
        <dt>{summary.status === 'TRIAL' ? 'Then' : 'Paid until'}</dt><dd>{summary.periodEndsOn ? date(summary.periodEndsOn) : 'No expiry'}</dd>
        {summary.daysRemaining != null && summary.daysRemaining >= 0 && <><dt>Days left</dt><dd>{summary.daysRemaining}</dd></>}
      </dl>
      <div className="d-flex flex-wrap gap-2 mb-2">
        <UsageChip label="Hostels" used={usage.hostels} limit={limits.maxHostels} warnPercent={summary.warningPercent} />
        <UsageChip label="Managers" used={usage.managers} limit={limits.maxManagers} warnPercent={summary.warningPercent} />
      </div>
      {usage.hostelBeds.length > 0 && (
        <ul className="list-unstyled mb-3">
          {usage.hostelBeds.map((h) => (
            <li key={h.hostelId} className="d-flex justify-content-between align-items-center py-1 border-bottom">
              <span>{h.hostelName}</span>
              <UsageChip label="Beds" used={h.beds} limit={limits.maxBedsPerHostel} warnPercent={summary.warningPercent} className="fs-6" />
            </li>
          ))}
        </ul>
      )}
      <SubscriptionNotice summary={summary} />
      <p className="small text-muted-hh mb-0">Need more hostels, beds or managers? Contact the platform administrator to upgrade your plan. Your existing records are never removed.</p>
    </section>
  );
}

/** Slim banner for the whole app when the subscription needs attention (grace, restricted, trial ending soon). */
export function SubscriptionBanner() {
  const { summary } = useSubscription();
  if (!summary) return null;
  const state = summary.accessState;
  const endingSoon = (state === 'TRIAL' || state === 'ACTIVE') && summary.daysRemaining != null && summary.daysRemaining >= 0 && summary.daysRemaining <= 7;
  if (state !== 'GRACE' && state !== 'RESTRICTED' && !endingSoon) return null;
  const text = (summary.warnings || []).find((w) => /subscription|trial/i.test(w));
  if (!text) return null;
  return (
    <div className={`alert ${state === 'RESTRICTED' ? 'alert-danger' : 'alert-warning'} py-2 small m-3 mb-0`} role="status">
      <Icon className="bi bi-exclamation-triangle-fill me-2" />{text}
    </div>
  );
}
