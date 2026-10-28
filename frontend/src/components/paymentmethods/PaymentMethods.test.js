import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import PaymentMethodsCard from './PaymentMethodsCard';
import ManualPaymentDetails from './ManualPaymentDetails';
import { paymentMethodApi } from '../../services/endpoints';
import { setAuth } from '../../test/authMock';
import { renderApp } from '../../test/renderUtils';

jest.mock('../../context/AuthContext', () => require('../../test/authMock').factory());
jest.mock('../../context/HostelContext', () => ({
  useHostels: () => ({ hostels: [{ id: 1, name: 'Alpha' }, { id: 2, name: 'Beta' }], selectedHostelId: null, selectHostel: jest.fn(), loading: false }),
}));
jest.mock('../common/AuthImage', () => ({ __esModule: true, default: ({ alt }) => <img alt={alt} /> }));
jest.mock('../../services/endpoints', () => ({
  paymentMethodApi: { get: jest.fn(), update: jest.fn(), removeOverride: jest.fn(), uploadQr: jest.fn(), removeQr: jest.fn() },
}));

const NOTE = 'UPI and bank-transfer payments made outside the online payment button are not detected automatically.';
const view = (over = {}) => ({
  hostelId: null, inherited: false, acceptsCash: true, acceptsUpi: false, upiId: null, upiPayeeName: null, qrFileId: null,
  acceptsBankTransfer: false, bankAccountHolder: null, bankName: null, bankBranch: null, bankIfsc: null, bankAccountMasked: null,
  hasBankAccount: false, acceptsOnline: true, instructions: null, encryptionAvailable: true, updatedAt: null, note: NOTE, ...over,
});

beforeEach(() => {
  jest.clearAllMocks();
  paymentMethodApi.get.mockResolvedValue(view());
  paymentMethodApi.update.mockImplementation((hostelId, body) => Promise.resolve(view({ ...body, bankAccountMasked: '•••• 3456', hasBankAccount: !!body.bankAccountNumber })));
});

describe('owner setup', () => {
  test('always states that UPI/QR/bank payments are not auto-reconciled', async () => {
    setAuth({ owner: true });
    renderApp(<PaymentMethodsCard />);
    expect(await screen.findByText(NOTE)).toBeInTheDocument();
    await userEvent.click(screen.getByLabelText(/UPI \(manual\)/));
    expect(screen.getByText(/cannot tell you who paid or for which invoice/)).toBeInTheDocument();
  });

  test('rejects a malformed UPI ID without calling the server', async () => {
    setAuth({ owner: true });
    renderApp(<PaymentMethodsCard />);
    await userEvent.click(await screen.findByLabelText(/UPI \(manual\)/));
    await userEvent.type(screen.getByLabelText(/^UPI ID/), 'not-a-upi-id');
    await userEvent.click(screen.getByRole('button', { name: /Save payment methods/ }));
    expect(await screen.findByText(/Enter a UPI ID such as name@bank/)).toBeInTheDocument();
    expect(paymentMethodApi.update).not.toHaveBeenCalled();
  });

  test('bank transfer needs holder, IFSC and a 9-18 digit account number; a valid form is sent', async () => {
    setAuth({ owner: true });
    renderApp(<PaymentMethodsCard />);
    await userEvent.click(await screen.findByLabelText(/Bank transfer \(manual\)/));
    await userEvent.click(screen.getByRole('button', { name: /Save payment methods/ }));
    expect(await screen.findByText('Enter the account holder name')).toBeInTheDocument();
    expect(screen.getByText('Enter the IFSC')).toBeInTheDocument();
    expect(screen.getByText('Enter the account number')).toBeInTheDocument();

    await userEvent.type(screen.getByLabelText(/Account holder name/), 'Sunrise PG');
    await userEvent.type(screen.getByLabelText(/^IFSC/), 'hdfc0001234');
    await userEvent.type(screen.getByLabelText(/^Account number/), '12AB');
    await userEvent.click(screen.getByRole('button', { name: /Save payment methods/ }));
    expect(await screen.findByText('Account number must be 9 to 18 digits')).toBeInTheDocument();

    await userEvent.clear(screen.getByLabelText(/^Account number/));
    await userEvent.type(screen.getByLabelText(/^Account number/), '123456789012');
    await userEvent.click(screen.getByRole('button', { name: /Save payment methods/ }));
    await waitFor(() => expect(paymentMethodApi.update).toHaveBeenCalled());
    const [, body] = paymentMethodApi.update.mock.calls[0];
    expect(body).toMatchObject({ acceptsBankTransfer: true, bankAccountHolder: 'Sunrise PG', bankIfsc: 'HDFC0001234', bankAccountNumber: '123456789012' });
  });

  test('a saved account number is shown masked only and kept when the field is left blank', async () => {
    setAuth({ owner: true });
    paymentMethodApi.get.mockResolvedValue(view({ acceptsBankTransfer: true, bankAccountHolder: 'Sunrise PG', bankIfsc: 'HDFC0001234', hasBankAccount: true, bankAccountMasked: '•••• 9012' }));
    renderApp(<PaymentMethodsCard />);
    expect(await screen.findByLabelText(/saved: •••• 9012/)).toHaveValue('');
    await userEvent.type(screen.getByLabelText(/Account holder name/), ' Hostel');
    await userEvent.click(screen.getByRole('button', { name: /Save payment methods/ }));
    await waitFor(() => expect(paymentMethodApi.update).toHaveBeenCalled());
    expect(paymentMethodApi.update.mock.calls[0][1].bankAccountNumber).toBeNull();
  });

  test('a per-hostel scope loads and saves that hostel', async () => {
    setAuth({ owner: true });
    renderApp(<PaymentMethodsCard />);
    await screen.findByText(NOTE);
    await userEvent.selectOptions(screen.getByLabelText('Applies to'), '2');
    await waitFor(() => expect(paymentMethodApi.get).toHaveBeenLastCalledWith(2));
  });

  test('users without SETTINGS_MANAGE see a read-only form with no save button', async () => {
    setAuth({ permissions: ['PAYMENT_VIEW'] });
    renderApp(<PaymentMethodsCard />);
    expect(await screen.findByText('Read-only')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Save payment methods/ })).not.toBeInTheDocument();
    expect(screen.getByLabelText(/^Cash/)).toBeDisabled();
  });

  test('warns when the server cannot encrypt account numbers', async () => {
    setAuth({ owner: true });
    paymentMethodApi.get.mockResolvedValue(view({ encryptionAvailable: false }));
    renderApp(<PaymentMethodsCard />);
    expect(await screen.findByText(/no data-encryption key is configured/)).toBeInTheDocument();
  });
});

describe('what residents see', () => {
  const info = {
    cash: true,
    upi: { upiId: 'sunrise@okhdfc', payeeName: 'Sunrise PG', hasQr: true },
    bank: { accountHolder: 'Sunrise PG', bankName: 'HDFC', branch: 'Indiranagar', ifsc: 'HDFC0001234', accountNumber: '123456789012' },
    online: true, instructions: 'Send the screenshot to the warden', note: NOTE,
  };

  test('shows UPI, QR, bank details and the no-auto-reconcile note', () => {
    renderApp(<ManualPaymentDetails info={info} qrSrc="/api/public/pay/tok/qr" invoiceNumber="INV-1" />);
    expect(screen.getByTestId('upi-id')).toHaveTextContent('sunrise@okhdfc');
    expect(screen.getByAltText('UPI QR code')).toHaveAttribute('src', '/api/public/pay/tok/qr');
    expect(screen.getByTestId('bank-account')).toHaveTextContent('123456789012');
    expect(screen.getByText('HDFC0001234')).toBeInTheDocument();
    expect(screen.getByText(/Send the screenshot/)).toBeInTheDocument();
    expect(screen.getByText(NOTE)).toBeInTheDocument();
  });

  test('renders nothing when no manual method is accepted', () => {
    renderApp(<ManualPaymentDetails info={{ cash: false, upi: null, bank: null, online: true }} />);
    expect(screen.queryByTestId('manual-payment')).not.toBeInTheDocument();
  });
});
