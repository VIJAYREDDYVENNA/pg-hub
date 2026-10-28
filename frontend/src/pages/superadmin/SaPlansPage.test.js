import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import SaPlansPage from './SaPlansPage';
import { adminApi } from '../../services/endpoints';
import { FEATURES, PLANS, renderApp } from '../../test/renderUtils';

jest.mock('../../services/endpoints', () => ({
  adminApi: { plans: jest.fn(), planFeatures: jest.fn(), createPlan: jest.fn(), updatePlan: jest.fn(), setPlanStatus: jest.fn() },
}));

const findRow = async (code) => (await screen.findByText(code)).closest('tr');

beforeEach(() => {
  jest.clearAllMocks();
  adminApi.plans.mockResolvedValue(PLANS);
  adminApi.planFeatures.mockResolvedValue(FEATURES);
  adminApi.createPlan.mockResolvedValue({ id: 20 });
  adminApi.updatePlan.mockResolvedValue({ id: 1 });
  adminApi.setPlanStatus.mockResolvedValue({ id: 1 });
});

test('lists every plan with price, limits, trial/grace, subscribers and status', async () => {
  renderApp(<SaPlansPage />);

  const starter = await findRow('STARTER');
  expect(starter).toHaveTextContent('Starter');
  expect(starter).toHaveTextContent('₹199');
  expect(starter).toHaveTextContent(/1\s*50\s*1/);
  expect(starter).toHaveTextContent('14 / 7 d');
  expect(starter).toHaveTextContent('Active');
  expect((await findRow('ENTERPRISE'))).toHaveTextContent('Custom');
  expect((await findRow('OLD'))).toHaveTextContent('Inactive');
});

test('creating a plan validates the code and sends limits, prices and feature entitlements', async () => {
  const user = userEvent.setup();
  renderApp(<SaPlansPage />);
  await user.click(await screen.findByRole('button', { name: /New plan/ }));
  const dialog = await screen.findByRole('dialog');

  await user.type(within(dialog).getByLabelText(/^Code/), 'bad code');
  await user.type(within(dialog).getByLabelText(/^Name/), 'Pro');
  await user.type(within(dialog).getByLabelText(/Monthly price/), '999');
  await user.click(within(dialog).getByRole('button', { name: 'Create plan' }));
  expect(await within(dialog).findByText(/Capital letters, digits or _/)).toBeInTheDocument();
  expect(adminApi.createPlan).not.toHaveBeenCalled();

  const code = within(dialog).getByLabelText(/^Code/);
  await user.clear(code);
  await user.type(code, 'PRO');
  await user.click(within(dialog).getByLabelText(/Advanced reports/));
  await user.click(within(dialog).getByRole('button', { name: 'Create plan' }));

  await waitFor(() => expect(adminApi.createPlan).toHaveBeenCalledTimes(1));
  expect(adminApi.createPlan.mock.calls[0][0]).toMatchObject({
    code: 'PRO', name: 'Pro', monthlyPrice: 999, maxHostels: 1, maxBedsPerHostel: 50, maxManagers: 1, trialDays: 14, graceDays: 7,
    customPricing: false, entitlements: expect.objectContaining({ ADVANCED_REPORTS: true, ONLINE_PAYMENTS: false }),
  });
});

test('editing a plan with subscribers warns that limits apply immediately but nothing is removed', async () => {
  const user = userEvent.setup();
  renderApp(<SaPlansPage />);
  const row = await findRow('STARTER');

  await user.click(within(row).getByRole('button', { name: /Edit/ }));
  const dialog = await screen.findByRole('dialog');

  expect(within(dialog).getByText(/2 owner\(s\) are on this plan/)).toBeInTheDocument();
  expect(within(dialog).getByLabelText(/^Code/)).toBeDisabled();
  expect(within(dialog).getByLabelText(/^Hostels per owner/)).toHaveValue(1);
  const hostels = within(dialog).getByLabelText(/^Hostels per owner/);
  await user.clear(hostels);
  await user.type(hostels, '2');
  await user.click(within(dialog).getByRole('button', { name: 'Save plan' }));

  await waitFor(() => expect(adminApi.updatePlan).toHaveBeenCalledWith(1, expect.objectContaining({ maxHostels: 2, name: 'Starter' })));
});

test('out-of-range numbers are rejected before saving', async () => {
  const user = userEvent.setup();
  renderApp(<SaPlansPage />);
  await user.click(within(await findRow('GROWTH')).getByRole('button', { name: /Edit/ }));
  const dialog = await screen.findByRole('dialog');
  const hostels = within(dialog).getByLabelText(/^Hostels per owner/);
  await user.clear(hostels);
  await user.type(hostels, '0');
  await user.click(within(dialog).getByRole('button', { name: 'Save plan' }));

  expect(await within(dialog).findByText('Minimum 1')).toBeInTheDocument();
  expect(adminApi.updatePlan).not.toHaveBeenCalled();
});

test('deactivating asks for confirmation and explains existing owners are unaffected', async () => {
  const user = userEvent.setup();
  renderApp(<SaPlansPage />);
  await user.click(within(await findRow('GROWTH')).getByRole('button', { name: 'Deactivate' }));

  const confirm = await screen.findByRole('dialog');
  expect(within(confirm).getByText(/Owners already on it are not affected/)).toBeInTheDocument();
  expect(adminApi.setPlanStatus).not.toHaveBeenCalled();
  await user.click(within(confirm).getByRole('button', { name: 'Deactivate' }));
  await waitFor(() => expect(adminApi.setPlanStatus).toHaveBeenCalledWith(2, 'INACTIVE'));
});

test('an inactive plan can be reactivated, and an archived plan can no longer be edited', async () => {
  adminApi.plans.mockResolvedValue([...PLANS, { ...PLANS[3], id: 12, code: 'ARCH', name: 'Archived plan', status: 'ARCHIVED' }]);
  const user = userEvent.setup();
  renderApp(<SaPlansPage />);

  const inactive = await findRow('OLD');
  expect(within(inactive).getByRole('button', { name: 'Activate' })).toBeInTheDocument();
  const archived = await findRow('ARCH');
  expect(within(archived).queryByRole('button', { name: /Edit/ })).not.toBeInTheDocument();
  expect(within(archived).queryByRole('button')).not.toBeInTheDocument();
  await user.click(within(inactive).getByRole('button', { name: 'Activate' }));
  expect(await screen.findByText('It will be available for new assignments again.')).toBeInTheDocument();
});

test('a server error while saving is shown in the dialog', async () => {
  adminApi.updatePlan.mockRejectedValue({ response: { status: 409, data: { message: 'An archived plan cannot be edited' } } });
  const user = userEvent.setup();
  renderApp(<SaPlansPage />);
  await user.click(within(await findRow('STARTER')).getByRole('button', { name: /Edit/ }));
  const dialog = await screen.findByRole('dialog');
  await user.click(within(dialog).getByRole('button', { name: 'Save plan' }));

  expect(await within(dialog).findByText('An archived plan cannot be edited')).toBeInTheDocument();
});

