import { render } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { ToastProvider } from '../context/ToastContext';

/** Renders with a router and the real toast provider (so success/error toasts are visible to assertions). */
export function renderApp(ui, { route = '/' } = {}) {
  return render(
    <MemoryRouter initialEntries={[route]}>
      <ToastProvider>{ui}</ToastProvider>
    </MemoryRouter>
  );
}

// Reusable API fixtures that mirror the backend's response shapes
export const PLANS = [
  { id: 1, code: 'STARTER', name: 'Starter', description: 'One hostel', monthlyPrice: 199, annualPrice: null, currency: 'INR',
    maxHostels: 1, maxBedsPerHostel: 50, maxManagers: 1, trialDays: 14, graceDays: 7, customPricing: false, status: 'ACTIVE', sortOrder: 10,
    entitlements: { ONLINE_PAYMENTS: true, WHATSAPP_MESSAGING: true, EXPENSE_TRACKING: true, ADVANCED_REPORTS: false }, subscribers: 2 },
  { id: 2, code: 'GROWTH', name: 'Growth', description: 'A few hostels', monthlyPrice: 399, annualPrice: null, currency: 'INR',
    maxHostels: 3, maxBedsPerHostel: 100, maxManagers: 5, trialDays: 14, graceDays: 7, customPricing: false, status: 'ACTIVE', sortOrder: 20,
    entitlements: { ONLINE_PAYMENTS: true, WHATSAPP_MESSAGING: true, EXPENSE_TRACKING: true, ADVANCED_REPORTS: true }, subscribers: 1 },
  { id: 4, code: 'ENTERPRISE', name: 'Enterprise', description: 'Custom', monthlyPrice: null, annualPrice: null, currency: 'INR',
    maxHostels: 50, maxBedsPerHostel: 1000, maxManagers: 100, trialDays: 14, graceDays: 7, customPricing: true, status: 'ACTIVE', sortOrder: 40,
    entitlements: { ONLINE_PAYMENTS: true }, subscribers: 0 },
  { id: 9, code: 'OLD', name: 'Legacy', description: '', monthlyPrice: 99, annualPrice: null, currency: 'INR',
    maxHostels: 1, maxBedsPerHostel: 20, maxManagers: 1, trialDays: 0, graceDays: 0, customPricing: false, status: 'INACTIVE', sortOrder: 90,
    entitlements: {}, subscribers: 0 },
];

export const FEATURES = [
  { code: 'ONLINE_PAYMENTS', label: 'Online rent payments', description: 'Collect rent online' },
  { code: 'WHATSAPP_MESSAGING', label: 'WhatsApp messaging', description: 'Send reminders' },
  { code: 'EXPENSE_TRACKING', label: 'Expense tracking', description: 'Expenses' },
  { code: 'ADVANCED_REPORTS', label: 'Advanced reports', description: 'Reports' },
];

export function subscriptionDetail(overrides = {}) {
  return {
    ownerId: 7, plan: PLANS[1], status: 'ACTIVE', accessState: 'ACTIVE', startsOn: '2026-10-01', trialEndsOn: null,
    periodEndsOn: '2026-12-01', graceEndsOn: '2026-12-08', graceDays: 7, billingStatus: 'PAID', customMaxHostels: null,
    customMaxBedsPerHostel: null, customMaxManagers: null, customMonthlyPrice: null, effectiveMonthlyPrice: 399,
    planLimits: { maxHostels: 3, maxBedsPerHostel: 100, maxManagers: 5 }, effectiveLimits: { maxHostels: 3, maxBedsPerHostel: 100, maxManagers: 5 },
    overridesPlan: false, usage: { hostels: 2, managers: 3, hostelBeds: [{ hostelId: 1, hostelName: 'Alpha', beds: 78 }, { hostelId: 2, hostelName: 'Beta', beds: 45 }] },
    overLimit: [], entitlements: PLANS[1].entitlements, events: [], payments: [], ...overrides,
  };
}

export function mySubscription(overrides = {}) {
  return {
    planCode: 'GROWTH', planName: 'Growth', status: 'ACTIVE', accessState: 'ACTIVE', trialEndsOn: null, periodEndsOn: '2026-12-01',
    graceEndsOn: '2026-12-08', daysRemaining: 30, limits: { maxHostels: 3, maxBedsPerHostel: 100, maxManagers: 5 },
    usage: { hostels: 2, managers: 3, hostelBeds: [{ hostelId: 1, hostelName: 'Alpha', beds: 78 }] }, canAddResources: true,
    warningPercent: 80, warnings: [], entitlements: {}, ...overrides,
  };
}
