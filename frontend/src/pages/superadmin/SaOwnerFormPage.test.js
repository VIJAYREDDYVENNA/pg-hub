import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router-dom';
import SaOwnerFormPage from './SaOwnerFormPage';
import { adminApi } from '../../services/endpoints';
import { FEATURES, PLANS, renderApp } from '../../test/renderUtils';

jest.mock('../../services/endpoints', () => ({
  adminApi: { plans: jest.fn(), planFeatures: jest.fn(), createOwner: jest.fn(), owner: jest.fn(), updateOwner: jest.fn() },
}));

const PASSWORD = 'Passw0rd1x';

function renderForm() {
  return renderApp(
    <Routes>
      <Route path="/super-admin/owners/new" element={<SaOwnerFormPage />} />
      <Route path="/super-admin/owners/:ownerId" element={<div>Owner details page</div>} />
    </Routes>,
    { route: '/super-admin/owners/new' }
  );
}

async function fillOwner(user) {
  await user.type(screen.getByLabelText(/Business \/ organisation name/), 'Sunrise PG');
  await user.type(screen.getByLabelText(/Owner full name/), 'Ravi Kumar');
  await user.type(screen.getByLabelText(/Email address/), 'ravi@example.com');
  await user.type(screen.getByLabelText(/Mobile number/), '9876543210');
  await user.type(screen.getByLabelText(/Initial password/), PASSWORD);
}

const plan = (id) => screen.getByLabelText(/Subscription plan/).value === String(id);

beforeEach(() => {
  jest.clearAllMocks();
  adminApi.plans.mockResolvedValue(PLANS);
  adminApi.planFeatures.mockResolvedValue(FEATURES);
  adminApi.createOwner.mockResolvedValue({ id: 42, businessName: 'Sunrise PG' });
});

test('only active plans are offered, and no limit fields show until a plan is chosen', async () => {
  renderForm();
  const select = await screen.findByLabelText(/Subscription plan/);
  const options = within(select).getAllByRole('option').map((o) => o.textContent);

  expect(options.join('|')).toMatch(/Starter — ₹199\/month/);
  expect(options.join('|')).toMatch(/Enterprise — custom price/);
  expect(options.join('|')).not.toMatch(/Legacy/);
  expect(screen.queryByLabelText('Maximum hostels')).not.toBeInTheDocument();
});

test('choosing a plan fills limits, trial, grace, price and the expiry date from the plan', async () => {
  const user = userEvent.setup();
  renderForm();
  await user.selectOptions(await screen.findByLabelText(/Subscription plan/), '2');

  expect(screen.getByLabelText('Maximum hostels')).toHaveValue(3);
  expect(screen.getByLabelText('Maximum beds per hostel')).toHaveValue(100);
  expect(screen.getByLabelText('Maximum manager accounts')).toHaveValue(5);
  expect(screen.getByLabelText('Trial (days)')).toHaveValue(14);
  expect(screen.getByLabelText('Grace period (days)')).toHaveValue(7);
  expect(screen.getByLabelText('Monthly price (₹)')).toHaveValue(399);
  expect(screen.getByLabelText('Trial ends on')).not.toHaveValue('');
  expect(screen.getAllByText('From the plan').length).toBeGreaterThanOrEqual(3);
  expect(screen.queryByText('Custom')).not.toBeInTheDocument();
  expect(plan(2)).toBe(true);
});

test('switching plans replaces the previous plan\'s defaults', async () => {
  const user = userEvent.setup();
  renderForm();
  const select = await screen.findByLabelText(/Subscription plan/);
  await user.selectOptions(select, '2');
  await user.selectOptions(select, '1');

  expect(screen.getByLabelText('Maximum hostels')).toHaveValue(1);
  expect(screen.getByLabelText('Maximum beds per hostel')).toHaveValue(50);
  expect(screen.getByLabelText('Maximum manager accounts')).toHaveValue(1);
});

test('a limit that differs from the plan is marked Custom, shows the plan value, and can be reset', async () => {
  const user = userEvent.setup();
  renderForm();
  await user.selectOptions(await screen.findByLabelText(/Subscription plan/), '1');

  const hostels = screen.getByLabelText('Maximum hostels');
  await user.clear(hostels);
  await user.type(hostels, '4');

  expect(screen.getByText('Custom')).toBeInTheDocument();
  expect(screen.getByText(/overrides the plan \(1\)/)).toBeInTheDocument();
  await user.click(screen.getByRole('button', { name: 'Use plan value' }));
  expect(hostels).toHaveValue(1);
  expect(screen.queryByText('Custom')).not.toBeInTheDocument();
});

test('creating an owner sends the plan and only the deliberate overrides', async () => {
  const user = userEvent.setup();
  renderForm();
  await fillOwner(user);
  await user.selectOptions(await screen.findByLabelText(/Subscription plan/), '2');
  const hostels = screen.getByLabelText('Maximum hostels');
  await user.clear(hostels);
  await user.type(hostels, '4');
  const beds = screen.getByLabelText('Maximum beds per hostel');
  await user.clear(beds);
  await user.type(beds, '75');

  await user.click(screen.getByRole('button', { name: 'Create owner' }));

  await waitFor(() => expect(adminApi.createOwner).toHaveBeenCalledTimes(1));
  const payload = adminApi.createOwner.mock.calls[0][0];
  expect(payload).toMatchObject({ businessName: 'Sunrise PG', email: 'ravi@example.com', status: 'ACTIVE' });
  expect(payload.subscription).toMatchObject({ planId: 2, trialDays: 14, graceDays: 7, customMaxHostels: 4, customMaxBedsPerHostel: 75 });
  expect(payload.subscription).not.toHaveProperty('customMaxManagers');
  expect(payload.subscription).not.toHaveProperty('customMonthlyPrice');
  expect(await screen.findByText('Owner details page')).toBeInTheDocument();
});

test('leaving the limits at the plan values sends no overrides', async () => {
  const user = userEvent.setup();
  renderForm();
  await fillOwner(user);
  await user.selectOptions(await screen.findByLabelText(/Subscription plan/), '1');

  await user.click(screen.getByRole('button', { name: 'Create owner' }));

  await waitFor(() => expect(adminApi.createOwner).toHaveBeenCalled());
  const { subscription } = adminApi.createOwner.mock.calls[0][0];
  expect(Object.keys(subscription).filter((k) => k.startsWith('custom'))).toEqual([]);
});

test('an owner cannot be created without choosing a plan', async () => {
  const user = userEvent.setup();
  renderForm();
  await fillOwner(user);

  await user.click(await screen.findByRole('button', { name: 'Create owner' }));

  expect(await screen.findByText('Select a subscription plan')).toBeInTheDocument();
  expect(adminApi.createOwner).not.toHaveBeenCalled();
});

test('invalid numbers are explained and nothing is sent', async () => {
  const user = userEvent.setup();
  renderForm();
  await fillOwner(user);
  await user.selectOptions(await screen.findByLabelText(/Subscription plan/), '1');
  fireEvent.change(screen.getByLabelText('Trial (days)'), { target: { value: '-3' } });
  fireEvent.change(screen.getByLabelText('Maximum hostels'), { target: { value: '0' } });

  await user.click(screen.getByRole('button', { name: 'Create owner' }));

  expect(await screen.findByText(/Trial duration must be a whole number of days between 0 and 365/)).toBeInTheDocument();
  expect(screen.getByText(/Maximum hostels must be a whole number between 1 and 1000/)).toBeInTheDocument();
  expect(adminApi.createOwner).not.toHaveBeenCalled();
});

test('an expiry before the start date is rejected', async () => {
  const user = userEvent.setup();
  renderForm();
  await fillOwner(user);
  await user.selectOptions(await screen.findByLabelText(/Subscription plan/), '1');
  fireEvent.change(screen.getByLabelText('Start date'), { target: { value: '2026-11-10' } });
  fireEvent.change(screen.getByLabelText('Trial ends on'), { target: { value: '2026-11-01' } });

  await user.click(screen.getByRole('button', { name: 'Create owner' }));

  expect(await screen.findByText('The expiry date cannot be before the start date')).toBeInTheDocument();
  expect(adminApi.createOwner).not.toHaveBeenCalled();
});

test('a server-side subscription error is shown next to the field it concerns', async () => {
  adminApi.createOwner.mockRejectedValue({ response: { status: 400, data: { fieldErrors: { 'subscription.trialDays': 'Trial is too long for this plan' } } } });
  const user = userEvent.setup();
  renderForm();
  await fillOwner(user);
  await user.selectOptions(await screen.findByLabelText(/Subscription plan/), '1');

  await user.click(screen.getByRole('button', { name: 'Create owner' }));

  expect(await screen.findByText('Trial is too long for this plan')).toBeInTheDocument();
  expect(screen.queryByText('Owner details page')).not.toBeInTheDocument();
});
