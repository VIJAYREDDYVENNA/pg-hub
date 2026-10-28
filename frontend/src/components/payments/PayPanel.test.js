import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import PayPanel, { ModeNotice } from './PayPanel';
import { ToastProvider } from '../../context/ToastContext';

const GATEWAY = { provider: 'mock', mode: 'SIMULATED', simulated: true, minPartialAmount: 100, partialPayments: true, paymentMethods: ['Simulated checkout'] };

const order = (amount) => ({ attemptId: 5, provider: 'mock', simulated: true, amount, currency: 'INR',
  checkout: { provider: 'mock', orderId: 'order_mock_1', amount: amount * 100, currency: 'INR' } });
const attempt = (status, over = {}) => ({ id: 5, status, amount: 2500, receiptNumber: null, providerPaymentId: null, simulated: true, failureReason: null, ...over });

function setup({ balance = 6000, gateway = GATEWAY, api: apiOverrides = {} } = {}) {
  const api = {
    createOrder: jest.fn().mockImplementation((amount) => Promise.resolve(order(amount))),
    confirm: jest.fn().mockResolvedValue(attempt('SUCCEEDED', { receiptNumber: 'RCP-2026-00007', providerPaymentId: 'pay_mock_abc' })),
    refresh: jest.fn().mockResolvedValue(attempt('SUCCEEDED', { receiptNumber: 'RCP-2026-00007' })),
    ...apiOverrides,
  };
  const onPaid = jest.fn();
  const onChanged = jest.fn();
  const utils = render(<ToastProvider><PayPanel api={api} balance={balance} gateway={gateway} onPaid={onPaid} onChanged={onChanged} /></ToastProvider>);
  return { api, onPaid, onChanged, user: userEvent.setup(), ...utils };
}

const payButton = () => screen.getByTestId('pay-submit');

describe('choosing how much to pay', () => {
  test('defaults to the full outstanding balance', () => {
    setup();
    expect(screen.getByText(/Outstanding balance:/)).toHaveTextContent('6,000');
    expect(payButton()).toHaveTextContent(/Pay.*6,000/);
    expect(screen.queryByLabelText(/Amount/)).not.toBeInTheDocument();
  });

  test('a part payment below the minimum is refused with the reason', async () => {
    const { user, api } = setup();
    await user.click(screen.getByRole('button', { name: 'Pay part' }));
    await user.type(screen.getByLabelText(/Amount/), '50');

    expect(screen.getByText(/Minimum partial payment is/)).toBeInTheDocument();
    expect(payButton()).toBeDisabled();
    expect(api.createOrder).not.toHaveBeenCalled();
  });

  test('a part payment above the balance is refused', async () => {
    const { user } = setup();
    await user.click(screen.getByRole('button', { name: 'Pay part' }));
    await user.type(screen.getByLabelText(/Amount/), '6500');

    expect(screen.getByText(/Cannot exceed the balance/)).toBeInTheDocument();
    expect(payButton()).toBeDisabled();
  });

  test('more than two decimal places is refused', async () => {
    const { user } = setup();
    await user.click(screen.getByRole('button', { name: 'Pay part' }));
    await user.type(screen.getByLabelText(/Amount/), '150.555');

    expect(screen.getByText('Use at most two decimal places')).toBeInTheDocument();
    expect(payButton()).toBeDisabled();
  });

  test('the part-payment option is hidden when the provider does not support it or the balance is already tiny', () => {
    const noPartial = setup({ gateway: { ...GATEWAY, partialPayments: false } });
    expect(screen.queryByRole('button', { name: 'Pay part' })).not.toBeInTheDocument();
    noPartial.unmount();
    setup({ balance: 80 });
    expect(screen.queryByRole('button', { name: 'Pay part' })).not.toBeInTheDocument();
    expect(payButton()).toHaveTextContent(/Pay.*80/);
  });
});

describe('simulated checkout outcomes', () => {
  async function startPartial(ctx, amount = '2500') {
    await ctx.user.click(screen.getByRole('button', { name: 'Pay part' }));
    await ctx.user.type(screen.getByLabelText(/Amount/), amount);
    await ctx.user.click(payButton());
    return screen.findByRole('dialog', { name: 'Simulated checkout' });
  }

  test('success is shown only after the server confirms, with the receipt number', async () => {
    const ctx = setup();
    const dialog = await startPartial(ctx);

    expect(ctx.api.createOrder).toHaveBeenCalledWith(2500);
    expect(within(dialog).getByText(/SIMULATED\./)).toBeInTheDocument();
    expect(screen.queryByText('Payment received')).not.toBeInTheDocument();
    await ctx.user.click(within(dialog).getByRole('button', { name: /Payment succeeds/ }));

    expect(await screen.findByText('Payment received')).toBeInTheDocument();
    expect(screen.getByText(/Receipt RCP-2026-00007/)).toBeInTheDocument();
    expect(screen.getByText(/SIMULATED/)).toBeInTheDocument();
    expect(ctx.api.confirm).toHaveBeenCalledWith('order_mock_1', expect.objectContaining({ mock_outcome: 'SUCCESS', mock_payment_id: expect.stringMatching(/^pay_mock_/) }));
    await ctx.user.click(screen.getByRole('button', { name: 'Done' }));
    expect(ctx.onPaid).toHaveBeenCalledTimes(1);
  });

  test('a failed payment says so, takes no money, and offers a retry', async () => {
    const ctx = setup({ api: { confirm: jest.fn().mockResolvedValue(attempt('FAILED', { failureReason: 'Simulated payment failure' })) } });
    const dialog = await startPartial(ctx);
    await ctx.user.click(within(dialog).getByRole('button', { name: /Payment fails/ }));

    expect(await screen.findByText('Payment failed')).toBeInTheDocument();
    expect(screen.getByText('Simulated payment failure')).toBeInTheDocument();
    expect(screen.queryByText('Payment received')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Try again/ })).toBeEnabled();
    expect(ctx.onPaid).not.toHaveBeenCalled();
  });

  test('a pending payment is not shown as paid; Check status resolves it later', async () => {
    const ctx = setup({ api: { confirm: jest.fn().mockResolvedValue(attempt('PENDING')) } });
    const dialog = await startPartial(ctx);
    await ctx.user.click(within(dialog).getByRole('button', { name: /stays pending/ }));

    expect(await screen.findByText('Waiting for the bank')).toBeInTheDocument();
    expect(screen.queryByText('Payment received')).not.toBeInTheDocument();
    await ctx.user.click(screen.getByRole('button', { name: /Check status/ }));
    expect(ctx.api.refresh).toHaveBeenCalledWith(5);
    expect(await screen.findByText('Payment received')).toBeInTheDocument();
  });

  test('closing the checkout without paying is reported as cancelled, never as paid', async () => {
    const ctx = setup();
    const dialog = await startPartial(ctx);
    await ctx.user.click(within(dialog).getByRole('button', { name: 'Close without paying' }));

    expect(await screen.findByText('Payment window closed')).toBeInTheDocument();
    expect(screen.getByText(/If money was deducted, use “Check status”/)).toBeInTheDocument();
    expect(ctx.api.confirm).not.toHaveBeenCalled();
    expect(ctx.onPaid).not.toHaveBeenCalled();
  });

  test('an amount the server rejects (limits, plan, no balance) is shown as an error', async () => {
    const ctx = setup({ api: { createOrder: jest.fn().mockRejectedValue({ response: { status: 409, data: { message: 'A payment for this invoice is awaiting confirmation from the bank.' } } }) } });
    await ctx.user.click(payButton());

    expect(await screen.findByText('Could not continue')).toBeInTheDocument();
    expect(screen.getByText(/awaiting confirmation from the bank/)).toBeInTheDocument();
  });

  test('an amount mismatch flagged by the server asks the resident to contact the hostel', async () => {
    const ctx = setup({ api: { confirm: jest.fn().mockResolvedValue(attempt('MISMATCH')) } });
    const dialog = await startPartial(ctx);
    await ctx.user.click(within(dialog).getByRole('button', { name: /Payment succeeds/ }));

    expect(await screen.findByText(/did not match this invoice/)).toBeInTheDocument();
    expect(screen.queryByText('Payment received')).not.toBeInTheDocument();
  });

  test('a verification error is surfaced and nothing is marked paid', async () => {
    const ctx = setup({ api: { confirm: jest.fn().mockRejectedValue({ response: { status: 400, data: { message: 'We could not verify this payment.' } } }) } });
    const dialog = await startPartial(ctx);
    await ctx.user.click(within(dialog).getByRole('button', { name: /Payment succeeds/ }));

    expect(await screen.findByText(/could not verify this payment/)).toBeInTheDocument();
    expect(screen.queryByText('Payment received')).not.toBeInTheDocument();
  });
});

describe('mode notice', () => {
  test('simulated and test modes are labelled; live mode shows nothing', () => {
    const { rerender } = render(<ModeNotice gateway={{ mode: 'SIMULATED' }} />);
    expect(screen.getByText(/Simulated payments\./)).toBeInTheDocument();
    rerender(<ModeNotice gateway={{ mode: 'TEST', provider: 'razorpay' }} />);
    expect(screen.getByText(/razorpay Test Mode\./)).toBeInTheDocument();
    expect(screen.getByText(/No real money is charged/)).toBeInTheDocument();
    rerender(<ModeNotice gateway={{ mode: 'LIVE', provider: 'razorpay' }} />);
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
  });
});

