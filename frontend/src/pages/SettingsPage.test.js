import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import SettingsPage from './SettingsPage';
import { settingsApi } from '../services/endpoints';
import { setAuth } from '../test/authMock';
import { renderApp } from '../test/renderUtils';

jest.mock('../context/AuthContext', () => require('../test/authMock').factory());
jest.mock('../components/paymentmethods/PaymentMethodsCard', () => ({ __esModule: true, default: () => <div>payment-methods-card</div> }));
jest.mock('../components/operations/NotificationPreferences', () => ({ __esModule: true, default: () => <div>prefs</div> }));
jest.mock('../components/subscription/UsagePanel', () => ({ PlanUsageCard: () => <div>plan-usage</div> }));
jest.mock('../components/operations/WhatsAppModeBanner', () => ({ __esModule: true, default: () => <div>wa-banner</div> }));
jest.mock('../services/endpoints', () => ({
  settingsApi: { get: jest.fn(), update: jest.fn() },
  whatsappApi: { status: jest.fn() },
}));

const SETTINGS = { vacancyWarningDays: 7, paymentGraceDays: 5, lateFeeAmount: 0, reminderDaysBefore: 3, autoRemindersEnabled: false, currencyCode: 'INR' };

beforeEach(() => {
  jest.clearAllMocks();
  settingsApi.get.mockResolvedValue(SETTINGS);
  settingsApi.update.mockImplementation((body) => Promise.resolve(body));
  require('../services/endpoints').whatsappApi.status.mockResolvedValue({ mode: 'MOCK', configured: false });
});

test('automatic reminders are off by default, explained, and can be switched on and saved', async () => {
  setAuth({ owner: true });
  renderApp(<SettingsPage />);
  const box = await screen.findByLabelText(/Send automatic WhatsApp rent reminders/);
  expect(box).not.toBeChecked();
  expect(screen.getByText(/only to residents who\s+agreed to WhatsApp/)).toBeInTheDocument();
  expect(screen.getByText(/stop as soon as the invoice is paid/)).toBeInTheDocument();
  expect(screen.getByText(/currently in mock mode/)).toBeInTheDocument();
  expect(screen.getByText('payment-methods-card')).toBeInTheDocument();

  await userEvent.click(box);
  await userEvent.click(screen.getByRole('button', { name: /Save settings/ }));
  await waitFor(() => expect(settingsApi.update).toHaveBeenCalled());
  expect(settingsApi.update.mock.calls[0][0]).toMatchObject({ autoRemindersEnabled: true, reminderDaysBefore: 3 });
});

test('a user without SETTINGS_MANAGE cannot change the reminder switch', async () => {
  setAuth({ permissions: ['HOSTEL_VIEW'] });
  renderApp(<SettingsPage />);
  expect(await screen.findByLabelText(/Send automatic WhatsApp rent reminders/)).toBeDisabled();
  expect(screen.queryByRole('button', { name: /Save settings/ })).not.toBeInTheDocument();
});
