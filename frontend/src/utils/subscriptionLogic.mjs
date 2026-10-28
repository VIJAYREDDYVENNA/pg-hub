/**
 * Pure subscription helpers (no React, no DOM) so they can be unit-tested with `node --test`.
 * Plan values always come from the API (the database); nothing here knows what "Starter" includes.
 */

export const LIMIT_FIELDS = [
  { key: 'maxHostels', custom: 'customMaxHostels', label: 'Maximum hostels', min: 1, max: 1000 },
  { key: 'maxBedsPerHostel', custom: 'customMaxBedsPerHostel', label: 'Maximum beds per hostel', min: 1, max: 10000 },
  { key: 'maxManagers', custom: 'customMaxManagers', label: 'Maximum manager accounts', min: 0, max: 1000 },
];

const isBlank = (v) => v === undefined || v === null || String(v).trim() === '';
const isWhole = (v) => /^-?\d+$/.test(String(v).trim());

/** Form values (strings) that a freshly selected plan pre-fills. */
export function planDefaults(plan) {
  if (!plan) return {};
  return {
    trialDays: String(plan.trialDays),
    graceDays: String(plan.graceDays),
    maxHostels: String(plan.maxHostels),
    maxBedsPerHostel: String(plan.maxBedsPerHostel),
    maxManagers: String(plan.maxManagers),
    customMonthlyPrice: plan.monthlyPrice === null || plan.monthlyPrice === undefined ? '' : String(plan.monthlyPrice),
  };
}

const pad = (n) => String(n).padStart(2, '0');
const fmt = (d) => `${d.getUTCFullYear()}-${pad(d.getUTCMonth() + 1)}-${pad(d.getUTCDate())}`;
const parse = (iso) => {
  const [y, m, d] = iso.split('-').map(Number);
  return new Date(Date.UTC(y, m - 1, d));
};

/** ISO date + n days, computed in UTC so time zones and daylight saving cannot shift it. */
export function addDays(iso, n) {
  const d = parse(iso);
  d.setUTCDate(d.getUTCDate() + n);
  return fmt(d);
}

/** ISO date + n calendar months, clamped to the end of shorter months (31 Jan + 1 month = 28/29 Feb). */
export function addMonths(iso, n) {
  const d = parse(iso);
  const day = d.getUTCDate();
  d.setUTCDate(1);
  d.setUTCMonth(d.getUTCMonth() + n);
  const last = new Date(Date.UTC(d.getUTCFullYear(), d.getUTCMonth() + 1, 0)).getUTCDate();
  d.setUTCDate(Math.min(day, last));
  return fmt(d);
}

/** The expiry date implied by a start date and trial length (matches the server's defaults). */
export function defaultExpiry(startsOn, trialDays) {
  if (!startsOn || !isWhole(trialDays ?? '')) return '';
  const days = Number(trialDays);
  return days > 0 ? addDays(startsOn, days) : addMonths(startsOn, 1);
}

/** Which limits (by key) the form sets to something other than the plan's value. */
export function overriddenLimits(plan, form) {
  if (!plan) return [];
  return LIMIT_FIELDS.filter(({ key }) => !isBlank(form[key]) && isWhole(form[key]) && Number(form[key]) !== plan[key])
    .map(({ key }) => key);
}

export function isPriceOverridden(plan, form) {
  if (!plan || isBlank(form.customMonthlyPrice)) return false;
  return plan.monthlyPrice === null || plan.monthlyPrice === undefined
    || Number(form.customMonthlyPrice) !== Number(plan.monthlyPrice);
}

/** Validation errors by form field name; an empty object means the subscription section is valid. */
export function validateSubscriptionForm({ plan, form }) {
  const errors = {};
  if (!plan) {
    errors.planId = 'Select a subscription plan';
    return errors;
  }
  if (isBlank(form.trialDays) || !isWhole(form.trialDays) || Number(form.trialDays) < 0 || Number(form.trialDays) > 365) {
    errors.trialDays = 'Trial duration must be a whole number of days between 0 and 365';
  }
  if (isBlank(form.graceDays) || !isWhole(form.graceDays) || Number(form.graceDays) < 0 || Number(form.graceDays) > 90) {
    errors.graceDays = 'Grace period must be a whole number of days between 0 and 90';
  }
  LIMIT_FIELDS.forEach(({ key, label, min, max }) => {
    if (isBlank(form[key]) || !isWhole(form[key]) || Number(form[key]) < min || Number(form[key]) > max) {
      errors[key] = `${label} must be a whole number between ${min} and ${max}`;
    }
  });
  if (!isBlank(form.customMonthlyPrice) && (Number.isNaN(Number(form.customMonthlyPrice)) || Number(form.customMonthlyPrice) < 0)) {
    errors.customMonthlyPrice = 'Enter a price of zero or more';
  }
  if (!isBlank(form.startsOn) && !/^\d{4}-\d{2}-\d{2}$/.test(form.startsOn)) errors.startsOn = 'Enter a valid start date';
  if (!isBlank(form.expiresOn) && !/^\d{4}-\d{2}-\d{2}$/.test(form.expiresOn)) errors.expiresOn = 'Enter a valid expiry date';
  if (!errors.startsOn && !errors.expiresOn && !isBlank(form.startsOn) && !isBlank(form.expiresOn) && form.expiresOn < form.startsOn) {
    errors.expiresOn = 'The expiry date cannot be before the start date';
  }
  return errors;
}

/**
 * The `subscription` object for owner creation. Limits equal to the plan's are not sent as overrides, so the owner
 * keeps following the plan if its limits are edited later; only a deliberately different value becomes a custom limit.
 */
export function buildSubscriptionInput({ plan, form }) {
  const input = {
    planId: plan.id,
    trialDays: Number(form.trialDays),
    graceDays: Number(form.graceDays),
  };
  if (!isBlank(form.startsOn)) input.startsOn = form.startsOn;
  if (!isBlank(form.expiresOn)) input.expiresOn = form.expiresOn;
  LIMIT_FIELDS.forEach(({ key, custom }) => {
    if (Number(form[key]) !== plan[key]) input[custom] = Number(form[key]);
  });
  if (isPriceOverridden(plan, form)) input.customMonthlyPrice = Number(form.customMonthlyPrice);
  return input;
}

/** 'ok' below the warning threshold, 'warn' from it, 'full' exactly at the limit, 'over' above it. */
export function usageLevel(used, limit, warnPercent = 80) {
  if (!(limit > 0)) return used > 0 ? 'over' : 'ok';
  if (used > limit) return 'over';
  if (used === limit) return 'full';
  return used * 100 >= limit * warnPercent ? 'warn' : 'ok';
}

export const usageText = (used, limit) => `${used} / ${limit}`;

export function limitReachedMessage(what, used, limit) {
  return `${what} limit reached (${used} / ${limit}). Contact the platform administrator to upgrade your plan.`;
}
