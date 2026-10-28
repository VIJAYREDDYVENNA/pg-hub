import test from 'node:test';
import assert from 'node:assert/strict';
import {
  addDays, addMonths, buildSubscriptionInput, defaultExpiry, isPriceOverridden, limitReachedMessage, overriddenLimits,
  planDefaults, usageLevel, usageText, validateSubscriptionForm,
} from './subscriptionLogic.mjs';

const starter = { id: 1, code: 'STARTER', monthlyPrice: 199, trialDays: 14, graceDays: 7, maxHostels: 1, maxBedsPerHostel: 50, maxManagers: 1 };
const growth = { id: 2, code: 'GROWTH', monthlyPrice: 399, trialDays: 14, graceDays: 7, maxHostels: 3, maxBedsPerHostel: 100, maxManagers: 5 };
const enterprise = { id: 4, code: 'ENTERPRISE', monthlyPrice: null, trialDays: 14, graceDays: 7, maxHostels: 50, maxBedsPerHostel: 1000, maxManagers: 100 };

const formFor = (plan, extra = {}) => ({ ...planDefaults(plan), startsOn: '2026-10-27', expiresOn: '', ...extra });

test('selecting a plan populates limits, trial, grace and price from the plan', () => {
  assert.deepEqual(planDefaults(growth), {
    trialDays: '14', graceDays: '7', maxHostels: '3', maxBedsPerHostel: '100', maxManagers: '5', customMonthlyPrice: '399',
  });
  assert.equal(planDefaults(enterprise).customMonthlyPrice, '', 'a custom-priced plan has no default price');
  assert.deepEqual(planDefaults(undefined), {});
});

test('switching plans replaces the defaults', () => {
  assert.equal(planDefaults(starter).maxHostels, '1');
  assert.equal(planDefaults(growth).maxHostels, '3');
});

test('trial length drives the default expiry date', () => {
  assert.equal(defaultExpiry('2026-10-27', '14'), '2026-11-10');
  assert.equal(defaultExpiry('2026-10-27', 0), '2026-11-27', 'no trial = one paid month');
  assert.equal(defaultExpiry('', '14'), '');
  assert.equal(defaultExpiry('2026-10-27', 'abc'), '');
});

test('date arithmetic is calendar-correct', () => {
  assert.equal(addDays('2026-12-25', 10), '2027-01-04');
  assert.equal(addDays('2028-02-28', 1), '2028-02-29');
  assert.equal(addMonths('2026-01-31', 1), '2026-02-28', 'clamped to the end of a shorter month');
  assert.equal(addMonths('2028-01-31', 1), '2028-02-29');
  assert.equal(addMonths('2026-11-30', 3), '2027-02-28');
});

test('a limit changed from the plan value is reported as a custom override', () => {
  assert.deepEqual(overriddenLimits(starter, formFor(starter)), []);
  assert.deepEqual(overriddenLimits(starter, formFor(starter, { maxHostels: '4' })), ['maxHostels']);
  assert.deepEqual(overriddenLimits(starter, formFor(starter, { maxHostels: '4', maxManagers: '9' })), ['maxHostels', 'maxManagers']);
  assert.deepEqual(overriddenLimits(starter, formFor(starter, { maxHostels: '' })), [], 'blank is a validation error, not an override');
});

test('price override is detected, and any price on a custom-priced plan counts as an override', () => {
  assert.equal(isPriceOverridden(starter, formFor(starter)), false);
  assert.equal(isPriceOverridden(starter, formFor(starter, { customMonthlyPrice: '150' })), true);
  assert.equal(isPriceOverridden(enterprise, formFor(enterprise)), false, 'blank price on enterprise');
  assert.equal(isPriceOverridden(enterprise, formFor(enterprise, { customMonthlyPrice: '5000' })), true);
});

test('the payload sends only deliberate overrides', () => {
  const unchanged = buildSubscriptionInput({ plan: growth, form: formFor(growth) });
  assert.deepEqual(unchanged, { planId: 2, trialDays: 14, graceDays: 7, startsOn: '2026-10-27' });

  const custom = buildSubscriptionInput({ plan: growth, form: formFor(growth, { maxHostels: '4', maxBedsPerHostel: '75', expiresOn: '2026-12-31', customMonthlyPrice: '350' }) });
  assert.equal(custom.customMaxHostels, 4);
  assert.equal(custom.customMaxBedsPerHostel, 75);
  assert.equal('customMaxManagers' in custom, false);
  assert.equal(custom.expiresOn, '2026-12-31');
  assert.equal(custom.customMonthlyPrice, 350);
});

test('a valid default form has no errors', () => {
  assert.deepEqual(validateSubscriptionForm({ plan: starter, form: formFor(starter) }), {});
});

test('a missing plan is reported', () => {
  assert.equal(validateSubscriptionForm({ plan: undefined, form: {} }).planId, 'Select a subscription plan');
});

test('invalid numbers produce clear messages', () => {
  const errors = validateSubscriptionForm({
    plan: starter,
    form: formFor(starter, { trialDays: '-1', graceDays: '100', maxHostels: '0', maxBedsPerHostel: '1.5', maxManagers: '', customMonthlyPrice: '-5' }),
  });
  assert.match(errors.trialDays, /between 0 and 365/);
  assert.match(errors.graceDays, /between 0 and 90/);
  assert.match(errors.maxHostels, /Maximum hostels must be a whole number between 1 and 1000/);
  assert.match(errors.maxBedsPerHostel, /whole number/);
  assert.match(errors.maxManagers, /Maximum manager accounts/);
  assert.match(errors.customMonthlyPrice, /zero or more/);
});

test('limits above the sanity ceiling are rejected', () => {
  const errors = validateSubscriptionForm({ plan: starter, form: formFor(starter, { maxHostels: '5000', maxBedsPerHostel: '999999' }) });
  assert.ok(errors.maxHostels);
  assert.ok(errors.maxBedsPerHostel);
});

test('zero managers is allowed, zero hostels is not', () => {
  assert.equal(validateSubscriptionForm({ plan: starter, form: formFor(starter, { maxManagers: '0' }) }).maxManagers, undefined);
  assert.ok(validateSubscriptionForm({ plan: starter, form: formFor(starter, { maxHostels: '0' }) }).maxHostels);
});

test('an expiry before the start date is rejected', () => {
  const errors = validateSubscriptionForm({ plan: starter, form: formFor(starter, { startsOn: '2026-10-27', expiresOn: '2026-10-01' }) });
  assert.equal(errors.expiresOn, 'The expiry date cannot be before the start date');
  assert.deepEqual(validateSubscriptionForm({ plan: starter, form: formFor(starter, { expiresOn: '2026-10-27' }) }), {});
});

test('usage levels: ok, warn, full, over', () => {
  assert.equal(usageLevel(1, 3), 'ok');
  assert.equal(usageLevel(79, 100), 'ok');
  assert.equal(usageLevel(80, 100), 'warn');
  assert.equal(usageLevel(99, 100), 'warn');
  assert.equal(usageLevel(100, 100), 'full');
  assert.equal(usageLevel(104, 100), 'over', 'grandfathered usage above a reduced limit');
  assert.equal(usageLevel(3, 5, 50), 'warn', 'threshold is configurable');
  assert.equal(usageLevel(0, 0), 'ok');
  assert.equal(usageLevel(2, 0), 'over');
});

test('usage and limit-reached texts', () => {
  assert.equal(usageText(75, 100), '75 / 100');
  assert.match(limitReachedMessage('Hostel', 3, 3), /Hostel limit reached \(3 \/ 3\)\. Contact the platform administrator to upgrade/);
});
