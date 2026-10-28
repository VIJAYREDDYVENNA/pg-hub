import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import OnlinePaymentPanel from './OnlinePaymentPanel';
import { onlinePaymentApi } from '../../services/endpoints';
import { setAuth } from '../../test/authMock';
import { renderApp } from '../../test/renderUtils';

jest.mock('../../context/AuthContext', () => require('../../test/authMock').factory());
jest.mock('../../services/endpoints', () => ({
  onlinePaymentApi: {
    config: jest.fn(), attempts: jest.fn(), createLink: jest.fn(), refresh: jest.fn(), settleSimulated: jest.fn(), forInvoice: jest.fn(),
  },
}));

const OPEN = { id: 11, invoiceNumber: 'INV-2026-00011', status: 'PENDING', balance: 5000 };
const PAID = { ...OPEN, status: 'PAID', balance: 0 };
const CONFIG = { provider: 'mock', mode: 'SIMULATED', simulated: true, currency: 'INR', minPartialAmount: 100, partialPayments: true, webhooks: true, paymentMethods: ['Simulated checkout'] };
const attempt = (over) => ({ id: 1, invoiceId: 11, provider: 'mock', providerOrderId: 'order_1', amount: 2000, status: 'FAILED', failureReason: 'Simulated payment failure',
  simulated: true, initiatedVia: 'STAFF', createdAt: '2026-10-26T10:00:00Z', ...over });

beforeEach(() => {
  jest.clearAllMocks();
  onlinePaymentApi.config.mockResolvedValue(CONFIG);
  onlinePaymentApi.attempts.mockResolvedValue([]);
  onlinePaymentApi.forInvoice.mockReturnValue({ createOrder: jest.fn(), confirm: jest.fn(), refresh: jest.fn() });
});

const renderPanel = (invoice = OPEN) => renderApp(<OnlinePaymentPanel invoice={invoice} onInvoiceChanged={jest.fn()} />);

describe('who can collect online', () => {
  test('a user who may collect payments sees Collect online and Create payment link, plus the simulated-mode warning', async () => {
    setAuth({ permissions: ['PAYMENT_VIEW', 'PAYMENT_COLLECT'] });
    renderPanel();

    expect(await screen.findByRole('button', { name: /Collect online/ })).toBeEnabled();
    expect(screen.getByRole('button', { name: /Create payment link/ })).toBeInTheDocument();
    expect(screen.getByText(/Simulated payments\./)).toBeInTheDocument();
    expect(screen.getByText(/no real money is collected/)).toBeInTheDocument();
  });

  test('a view-only user sees the attempt history but no way to start a payment', async () => {
    setAuth({ permissions: ['PAYMENT_VIEW'] });
    onlinePaymentApi.attempts.mockResolvedValue([attempt()]);
    renderPanel();

    expect(await screen.findByText('Simulated payment failure')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Collect online/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Create payment link/ })).not.toBeInTheDocument();
  });

  test('a settled invoice with no history shows nothing; with history it shows the history only', async () => {
    setAuth({ owner: true });
    const first = renderPanel(PAID);
    await waitFor(() => expect(onlinePaymentApi.attempts).toHaveBeenCalled());
    expect(screen.queryByText('Online payment')).not.toBeInTheDocument();
    first.unmount();

    onlinePaymentApi.attempts.mockResolvedValue([attempt({ status: 'SUCCEEDED', failureReason: null, receiptNumber: 'RCP-2026-00001' })]);
    renderPanel(PAID);
    expect(await screen.findByText('Receipt RCP-2026-00001')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Collect online/ })).not.toBeInTheDocument();
  });
});

describe('payment link', () => {
  test('creating a link shows it once, with its expiry and a copy button', async () => {
    setAuth({ owner: true });
    onlinePaymentApi.createLink.mockResolvedValue({ url: 'http://localhost:3000/pay/abc123', expiresAt: '2026-11-02T10:00:00Z' });
    const user = userEvent.setup();
    renderPanel();

    await user.click(await screen.findByRole('button', { name: /Create payment link/ }));

    expect(await screen.findByDisplayValue('http://localhost:3000/pay/abc123')).toBeInTheDocument();
    expect(screen.getByText(/shown only now/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Copy/ })).toBeInTheDocument();
    expect(onlinePaymentApi.createLink).toHaveBeenCalledWith(11);
  });

  test('a server refusal (e.g. feature not in plan) is reported, not swallowed', async () => {
    setAuth({ owner: true });
    onlinePaymentApi.createLink.mockRejectedValue({ response: { status: 403, data: { message: 'This feature is not included in your plan. Contact the platform administrator to upgrade.' } } });
    const user = userEvent.setup();
    renderPanel();

    await user.click(await screen.findByRole('button', { name: /Create payment link/ }));

    expect(await screen.findByText(/not included in your plan/)).toBeInTheDocument();
  });
});

describe('attempt history actions', () => {
  test('a pending simulated payment offers Check status and the two simulated bank outcomes', async () => {
    setAuth({ owner: true });
    onlinePaymentApi.attempts.mockResolvedValue([attempt({ status: 'PENDING', failureReason: null })]);
    onlinePaymentApi.refresh.mockResolvedValue({});
    const user = userEvent.setup();
    renderPanel();

    expect(await screen.findByText('Awaiting bank')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Simulate bank success' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Simulate bank failure' })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: /Check status/ }));
    await waitFor(() => expect(onlinePaymentApi.refresh).toHaveBeenCalledWith(1));
  });

  test('a successful attempt has no refresh or simulate buttons', async () => {
    setAuth({ owner: true });
    onlinePaymentApi.attempts.mockResolvedValue([attempt({ status: 'SUCCEEDED', failureReason: null })]);
    renderPanel();

    expect(await screen.findByText('Paid')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Check status/ })).not.toBeInTheDocument();
  });

  test('real (non-simulated) providers never show the simulate buttons', async () => {
    setAuth({ owner: true });
    onlinePaymentApi.config.mockResolvedValue({ ...CONFIG, provider: 'razorpay', mode: 'TEST', simulated: false });
    onlinePaymentApi.attempts.mockResolvedValue([attempt({ status: 'PENDING', simulated: false, provider: 'razorpay', failureReason: null })]);
    renderPanel();

    expect(await screen.findByText('Awaiting bank')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Simulate bank/ })).not.toBeInTheDocument();
    expect(screen.getByText(/razorpay Test Mode/)).toBeInTheDocument();
  });
});
