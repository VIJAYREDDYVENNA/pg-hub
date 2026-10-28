import { useRef, useState } from 'react';
import { errorMessage } from '../../services/apiClient';
import Icon from '../common/Icon';
import Modal from '../common/Modal';
import { fromPaise, toPaise } from './financeUtils';
import { money } from '../../utils/format';

const RAZORPAY_SCRIPT = 'https://checkout.razorpay.com/v1/checkout.js';
let razorpayLoader = null;

/** Loads Razorpay Checkout once. The script comes from Razorpay; no keys or secrets are involved here. */
function loadRazorpay() {
  if (window.Razorpay) return Promise.resolve(window.Razorpay);
  if (!razorpayLoader) {
    razorpayLoader = new Promise((resolve, reject) => {
      const s = document.createElement('script');
      s.src = RAZORPAY_SCRIPT;
      s.async = true;
      s.onload = () => resolve(window.Razorpay);
      s.onerror = () => { razorpayLoader = null; reject(new Error('Could not load the payment window. Check your connection.')); };
      document.head.appendChild(s);
    });
  }
  return razorpayLoader;
}

/** Explains that no real money moves; shown wherever a payment can be started. */
export function ModeNotice({ gateway, className = '' }) {
  if (!gateway) return null;
  if (gateway.mode === 'SIMULATED') {
    return (
      <div className={`alert alert-warning py-2 d-flex gap-2 align-items-start ${className}`} role="status">
        <Icon className="bi bi-cone-striped mt-1" />
        <div><strong>Simulated payments.</strong> Nothing is charged and no real money is collected. You choose the outcome in a test window.</div>
      </div>
    );
  }
  if (gateway.mode === 'TEST') {
    return (
      <div className={`alert alert-warning py-2 d-flex gap-2 align-items-start ${className}`} role="status">
        <Icon className="bi bi-cone-striped mt-1" />
        <div><strong>{gateway.provider} Test Mode.</strong> Use the provider's test cards / UPI IDs. No real money is charged.</div>
      </div>
    );
  }
  return null;
}

/** Stand-in for a provider checkout window when the mock gateway is active. */
function SimulatedCheckout({ order, onChoose, onClose }) {
  return (
    <Modal show title="Simulated checkout" onClose={onClose}
      footer={<button type="button" className="btn btn-light" onClick={onClose}>Close without paying</button>}>
      <div className="alert alert-warning py-2 mb-3"><strong>SIMULATED.</strong> No real payment is made.</div>
      <p className="mb-3">Pay <strong>{money(order.amount)}</strong> for this invoice. Choose what the bank should report:</p>
      <div className="d-grid gap-2">
        <button type="button" className="btn btn-success" onClick={() => onChoose('SUCCESS')}>
          <Icon className="bi bi-check-circle-fill me-2" />Payment succeeds
        </button>
        <button type="button" className="btn btn-outline-danger" onClick={() => onChoose('FAILED')}>
          <Icon className="bi bi-x-octagon me-2" />Payment fails
        </button>
        <button type="button" className="btn btn-outline-secondary" onClick={() => onChoose('PENDING')}>
          <Icon className="bi bi-hourglass-split me-2" />Bank is slow (stays pending)
        </button>
      </div>
    </Modal>
  );
}

const RESULT_UI = {
  success: { cls: 'alert-success', icon: 'bi-check-circle-fill', title: 'Payment received' },
  failed: { cls: 'alert-danger', icon: 'bi-x-octagon', title: 'Payment failed' },
  pending: { cls: 'alert-warning', icon: 'bi-hourglass-split', title: 'Waiting for the bank' },
  cancelled: { cls: 'alert-secondary', icon: 'bi-x-circle', title: 'Payment window closed' },
  error: { cls: 'alert-danger', icon: 'bi-exclamation-triangle-fill', title: 'Could not continue' },
};

/**
 * Full or partial online payment for one invoice. `api` has createOrder / confirm / refresh so the same panel serves
 * signed-in staff and residents on a pay link. Success is only ever shown after the backend verified the payment with
 * the provider; the browser's own "success" callback is just a trigger for that verification.
 */
export default function PayPanel({ api, balance, gateway, onPaid, onChanged }) {
  const balancePaise = toPaise(balance);
  const minPaise = toPaise(gateway?.minPartialAmount ?? 100);
  const partialAllowed = gateway?.partialPayments !== false && balancePaise > minPaise;
  const [mode, setMode] = useState('FULL');
  const [amount, setAmount] = useState('');
  const [busy, setBusy] = useState(false);
  const [sim, setSim] = useState(null);
  const [result, setResult] = useState(null);
  const orderRef = useRef(null);
  const settledRef = useRef(false);

  const chosenPaise = mode === 'FULL' ? balancePaise : toPaise(amount);
  let amountError = null;
  if (mode === 'PARTIAL') {
    if (!Number.isFinite(chosenPaise) || chosenPaise <= 0) amountError = 'Enter an amount';
    else if (chosenPaise > balancePaise) amountError = `Cannot exceed the balance of ${money(balance)}`;
    else if (chosenPaise < minPaise && chosenPaise < balancePaise) amountError = `Minimum partial payment is ${money(gateway?.minPartialAmount ?? 100)}`;
    else if (String(amount).split('.')[1]?.length > 2) amountError = 'Use at most two decimal places';
  }

  const finish = (attempt) => {
    settledRef.current = true;
    const status = attempt?.status;
    if (status === 'SUCCEEDED') setResult({ kind: 'success', attempt });
    else if (status === 'FAILED') setResult({ kind: 'failed', attempt, message: attempt.failureReason || 'The payment did not go through. No money was taken for this attempt.' });
    else if (status === 'MISMATCH') setResult({ kind: 'error', attempt, message: 'The payment amount did not match this invoice, so it was not applied. Please contact the hostel.' });
    else setResult({ kind: 'pending', attempt, message: 'Your payment was submitted but the bank has not confirmed it yet. It will be applied automatically once confirmed.' });
    onChanged?.();
  };

  const verify = async (fields) => {
    setBusy(true);
    try {
      finish(await api.confirm(orderRef.current.checkout.orderId, fields));
    } catch (e) {
      setResult({ kind: 'error', message: errorMessage(e), attempt: { id: orderRef.current?.attemptId } });
    } finally {
      setBusy(false);
    }
  };

  const openRazorpay = async (order) => {
    const Razorpay = await loadRazorpay();
    const c = order.checkout;
    const rz = new Razorpay({
      key: c.key,
      order_id: c.orderId,
      amount: c.amount,
      currency: c.currency,
      name: 'HostelHub',
      description: c.description,
      theme: { color: '#FF5A0A' },
      handler: (resp) => verify(resp),
      modal: {
        ondismiss: () => {
          if (!settledRef.current) setResult({ kind: 'cancelled', attempt: { id: order.attemptId } });
        },
      },
    });
    rz.open();
  };

  const start = async () => {
    if (amountError || !(chosenPaise > 0)) return;
    setResult(null);
    settledRef.current = false;
    setBusy(true);
    try {
      const order = await api.createOrder(Number(fromPaise(chosenPaise)));
      orderRef.current = order;
      if (order.checkout?.provider === 'razorpay') await openRazorpay(order);
      else setSim(order);
    } catch (e) {
      setResult({ kind: 'error', message: errorMessage(e) });
    } finally {
      setBusy(false);
    }
  };

  const checkStatus = async () => {
    const id = result?.attempt?.id;
    if (!id) return;
    setBusy(true);
    try {
      finish(await api.refresh(id));
    } catch (e) {
      setResult({ kind: 'error', message: errorMessage(e), attempt: result.attempt });
    } finally {
      setBusy(false);
    }
  };

  const chooseSimulated = (outcome) => {
    setSim(null);
    verify({ mock_payment_id: `pay_mock_${Math.random().toString(36).slice(2, 10)}`, mock_outcome: outcome });
  };

  if (result?.kind === 'success') {
    const a = result.attempt;
    return (
      <div className="alert alert-success mb-0" role="status">
        <div className="d-flex gap-2 align-items-center fw-bold"><Icon className="bi bi-check-circle-fill" />Payment received</div>
        <div className="mt-1">{money(a.amount)} paid{a.receiptNumber ? ` · Receipt ${a.receiptNumber}` : ''}{a.simulated ? ' · SIMULATED' : ''}</div>
        {a.providerPaymentId && <div className="small text-muted-hh">Reference {a.providerPaymentId}</div>}
        <button type="button" className="btn btn-success btn-sm mt-2" onClick={() => onPaid?.(a)}>Done</button>
      </div>
    );
  }

  const ui = result ? RESULT_UI[result.kind] : null;
  return (
    <div>
      <ModeNotice gateway={gateway} />
      {ui && (
        <div className={`alert ${ui.cls}`} role="alert">
          <div className="d-flex gap-2 align-items-center fw-bold"><Icon className={`bi ${ui.icon}`} />{ui.title}</div>
          {result.message && <div className="mt-1">{result.message}</div>}
          {result.kind === 'cancelled' && <div className="mt-1">If money was deducted, use “Check status”. Otherwise you can try again.</div>}
          {(result.kind === 'pending' || result.kind === 'cancelled') && result.attempt?.id && (
            <button type="button" className="btn btn-sm btn-outline-dark mt-2" onClick={checkStatus} disabled={busy}>
              <Icon className="bi bi-arrow-repeat me-1" />Check status
            </button>
          )}
        </div>
      )}

      <div className="mb-2 fw-semibold">Outstanding balance: <span className="text-danger">{money(balance)}</span></div>
      {partialAllowed && (
        <div className="btn-group w-100 mb-3" role="group" aria-label="Payment amount">
          <button type="button" className={`btn ${mode === 'FULL' ? 'btn-primary' : 'btn-outline-primary'}`} onClick={() => setMode('FULL')}>Pay full amount</button>
          <button type="button" className={`btn ${mode === 'PARTIAL' ? 'btn-primary' : 'btn-outline-primary'}`} onClick={() => setMode('PARTIAL')}>Pay part</button>
        </div>
      )}
      {mode === 'PARTIAL' && (
        <div className="mb-3">
          <label htmlFor="pay-amount" className="form-label">Amount (₹)</label>
          <input id="pay-amount" type="number" inputMode="decimal" step="0.01" min="1" value={amount}
            className={`form-control ${amountError && amount ? 'is-invalid' : ''}`} onChange={(e) => setAmount(e.target.value)} />
          {amountError && amount && <div className="invalid-feedback d-block">{amountError}</div>}
          <div className="form-text">Minimum {money(gateway?.minPartialAmount ?? 100)}. The rest stays due.</div>
        </div>
      )}
      <button type="button" className="btn btn-primary w-100" data-testid="pay-submit" onClick={start} disabled={busy || !!amountError || !(chosenPaise > 0)}>
        {busy ? <span className="spinner-border spinner-border-sm me-2" /> : <Icon className="bi bi-credit-card me-2" />}
        {result && ['failed', 'cancelled', 'error'].includes(result.kind) ? 'Try again — ' : 'Pay '}{Number.isFinite(chosenPaise) && chosenPaise > 0 ? money(chosenPaise / 100) : ''}
      </button>
      {gateway?.paymentMethods?.length > 0 && (
        <div className="small text-muted-hh mt-2">Available: {gateway.paymentMethods.join(', ')}</div>
      )}
      {sim && <SimulatedCheckout order={sim} onChoose={chooseSimulated} onClose={() => { setSim(null); setResult({ kind: 'cancelled', attempt: { id: sim.attemptId } }); }} />}
    </div>
  );
}
