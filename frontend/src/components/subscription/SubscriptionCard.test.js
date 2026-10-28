import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import SubscriptionCard from './SubscriptionCard';
import { adminApi } from '../../services/endpoints';
import { FEATURES, PLANS, renderApp, subscriptionDetail } from '../../test/renderUtils';

jest.mock('../../services/endpoints', () => ({
  adminApi: {
    ownerSubscription: jest.fn(), plans: jest.fn(), planFeatures: jest.fn(), updateOwnerSubscription: jest.fn(),
    recordSubscriptionPayment: jest.fn(),
  },
}));

const renderCard = () => renderApp(<SubscriptionCard ownerId={7} />);

beforeEach(() => {
  jest.clearAllMocks();
  adminApi.plans.mockResolvedValue(PLANS);
  adminApi.planFeatures.mockResolvedValue(FEATURES);
  adminApi.ownerSubscription.mockResolvedValue(subscriptionDetail());
  adminApi.updateOwnerSubscription.mockResolvedValue(subscriptionDetail());
  adminApi.recordSubscriptionPayment.mockResolvedValue(subscriptionDetail());
});

test('shows the plan, its state, the dates and each limit against current usage', async () => {
  renderCard();

  expect(await screen.findByText('Growth')).toBeInTheDocument();
  expect(screen.getAllByText('Active').length).toBeGreaterThanOrEqual(1);
  const table = screen.getByRole('table');
  expect(within(table).getByText('Hostels').closest('tr')).toHaveTextContent(/Hostels\s*2\s*3/);
  expect(within(table).getByText('Beds · Alpha').closest('tr')).toHaveTextContent(/78\s*100/);
  expect(within(table).getByText('Beds · Beta').closest('tr')).toHaveTextContent(/45\s*100/);
  expect(within(table).getByText('Manager accounts').closest('tr')).toHaveTextContent(/3\s*5/);
  expect(screen.queryByText('Custom limits')).not.toBeInTheDocument();
  expect(screen.queryByText(/Over the current limits/)).not.toBeInTheDocument();
});

test('flags owner-specific limits and usage that is over a limit (grandfathered)', async () => {
  adminApi.ownerSubscription.mockResolvedValue(subscriptionDetail({
    plan: PLANS[0], overridesPlan: true, customMaxBedsPerHostel: 120,
    planLimits: { maxHostels: 1, maxBedsPerHostel: 50, maxManagers: 1 }, effectiveLimits: { maxHostels: 1, maxBedsPerHostel: 120, maxManagers: 1 },
    usage: { hostels: 3, managers: 1, hostelBeds: [{ hostelId: 1, hostelName: 'Alpha', beds: 107 }] },
    overLimit: ['hostels 3 / 1'],
  }));
  renderCard();

  expect(await screen.findByText('Custom limits')).toBeInTheDocument();
  expect(screen.getByText(/Over the current limits/).closest('[role="status"]')).toHaveTextContent('hostels 3 / 1');
  expect(screen.getByText(/Existing records are kept/)).toBeInTheDocument();
  const bedsRow = screen.getByText('Beds · Alpha').closest('tr');
  expect(within(bedsRow).getByText('custom')).toHaveAttribute('title', 'Plan value: 50');
});

test.each([
  ['TRIAL', 'Trial'], ['GRACE', 'Grace period'], ['RESTRICTED', 'Restricted'],
])('shows the %s state to the Super Admin', async (state, text) => {
  adminApi.ownerSubscription.mockResolvedValue(subscriptionDetail({ accessState: state }));
  renderCard();
  expect(await screen.findByText(text)).toBeInTheDocument();
});

test('lists the change history with who made it, and manual payments', async () => {
  adminApi.ownerSubscription.mockResolvedValue(subscriptionDetail({
    events: [{ id: 2, eventType: 'PLAN_CHANGED', details: 'STARTER -> GROWTH', actorName: 'Platform Administrator', createdAt: '2026-10-20T10:00:00Z' }],
    payments: [{ id: 1, amount: 399, paidOn: '2026-10-01', method: 'UPI', reference: 'UTR998', extendedTo: '2026-11-01', createdAt: '2026-10-01T10:00:00Z' }],
  }));
  renderCard();

  expect(await screen.findByText(/STARTER -> GROWTH/)).toBeInTheDocument();
  expect(screen.getByText(/Platform Administrator/)).toBeInTheDocument();
  expect(screen.getByText(/UTR998/)).toBeInTheDocument();
});

test('changing the plan in the edit dialog resets limits to the new plan and saves without overrides', async () => {
  const user = userEvent.setup();
  renderCard();
  await user.click(await screen.findByRole('button', { name: /Edit subscription/ }));

  const dialog = await screen.findByRole('dialog');
  expect(within(dialog).getByLabelText('Maximum hostels')).toHaveValue(3);
  await user.selectOptions(within(dialog).getByLabelText('Plan'), '1');
  expect(within(dialog).getByLabelText('Maximum hostels')).toHaveValue(1);
  expect(within(dialog).getByLabelText('Maximum beds per hostel')).toHaveValue(50);
  await user.click(within(dialog).getByRole('button', { name: 'Save changes' }));

  await waitFor(() => expect(adminApi.updateOwnerSubscription).toHaveBeenCalledTimes(1));
  const [ownerId, body] = adminApi.updateOwnerSubscription.mock.calls[0];
  expect(ownerId).toBe(7);
  expect(body).toMatchObject({ planId: 1, status: 'ACTIVE', customMaxHostels: null, customMaxBedsPerHostel: null, customMaxManagers: null });
});

test('a custom limit typed in the edit dialog is saved as an override for this owner only', async () => {
  const user = userEvent.setup();
  renderCard();
  await user.click(await screen.findByRole('button', { name: /Edit subscription/ }));
  const dialog = await screen.findByRole('dialog');

  const hostels = within(dialog).getByLabelText('Maximum hostels');
  await user.clear(hostels);
  await user.type(hostels, '6');
  expect(within(dialog).getByText('Custom')).toBeInTheDocument();
  await user.type(within(dialog).getByLabelText(/Reason for the change/), 'Agreed in writing');
  await user.click(within(dialog).getByRole('button', { name: 'Save changes' }));

  await waitFor(() => expect(adminApi.updateOwnerSubscription).toHaveBeenCalled());
  const body = adminApi.updateOwnerSubscription.mock.calls[0][1];
  expect(body).toMatchObject({ planId: 2, customMaxHostels: 6, customMaxBedsPerHostel: null, customMaxManagers: null, reason: 'Agreed in writing' });
});

test('an invalid limit blocks saving with a clear message', async () => {
  const user = userEvent.setup();
  renderCard();
  await user.click(await screen.findByRole('button', { name: /Edit subscription/ }));
  const dialog = await screen.findByRole('dialog');
  fireEvent.change(within(dialog).getByLabelText('Maximum hostels'), { target: { value: '0' } });

  await user.click(within(dialog).getByRole('button', { name: 'Save changes' }));

  expect(await within(dialog).findByText(/Maximum hostels must be a whole number between 1 and 1000/)).toBeInTheDocument();
  expect(adminApi.updateOwnerSubscription).not.toHaveBeenCalled();
});

test('only active plans (plus the current one) can be selected when editing', async () => {
  const user = userEvent.setup();
  renderCard();
  await user.click(await screen.findByRole('button', { name: /Edit subscription/ }));
  const dialog = await screen.findByRole('dialog');

  const options = within(within(dialog).getByLabelText('Plan')).getAllByRole('option').map((o) => o.textContent);
  expect(options).toEqual(['Starter', 'Growth', 'Enterprise']);
});

test('recording a payment sends the amount and how far it extends the subscription', async () => {
  const user = userEvent.setup();
  renderCard();
  await user.click(await screen.findByRole('button', { name: /Record payment/ }));
  const dialog = await screen.findByRole('dialog');

  await user.type(within(dialog).getByLabelText(/Amount/), '399');
  await user.type(within(dialog).getByLabelText(/Reference/), 'UTR123');
  await user.click(within(dialog).getByRole('button', { name: 'Record payment' }));

  await waitFor(() => expect(adminApi.recordSubscriptionPayment).toHaveBeenCalledTimes(1));
  expect(adminApi.recordSubscriptionPayment.mock.calls[0]).toEqual([7, expect.objectContaining({ amount: 399, method: 'UPI', reference: 'UTR123', extendMonths: 1 })]);
});

test('a payment dated in the future or of zero is rejected before it is sent', async () => {
  const user = userEvent.setup();
  renderCard();
  await user.click(await screen.findByRole('button', { name: /Record payment/ }));
  const dialog = await screen.findByRole('dialog');

  await user.type(within(dialog).getByLabelText(/Amount/), '0');
  fireEvent.change(within(dialog).getByLabelText(/^Paid on/), { target: { value: '2099-01-01' } });
  await user.click(within(dialog).getByRole('button', { name: 'Record payment' }));

  expect(await within(dialog).findByText('Amount must be more than zero')).toBeInTheDocument();
  expect(within(dialog).getByText('The date cannot be in the future')).toBeInTheDocument();
  expect(adminApi.recordSubscriptionPayment).not.toHaveBeenCalled();
});

test('a failed load offers a retry instead of a blank card', async () => {
  adminApi.ownerSubscription.mockRejectedValueOnce({ response: { status: 500, data: {} } });
  const user = userEvent.setup();
  renderCard();

  const retry = await screen.findByRole('button', { name: /retry|try again/i });
  await user.click(retry);

  expect(await screen.findByText('Growth')).toBeInTheDocument();
});
