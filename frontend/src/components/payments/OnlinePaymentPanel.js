import { useState } from 'react';
import useApi from '../../hooks/useApi';
import { onlinePaymentApi } from '../../services/endpoints';
import { errorMessage } from '../../services/apiClient';
import { useAuth } from '../../context/AuthContext';
import { useToast } from '../../context/ToastContext';
import Icon from '../common/Icon';
import Modal from '../common/Modal';
import StatusBadge from '../common/StatusBadge';
import PayPanel, { ModeNotice } from './PayPanel';
import { isOpenInvoice } from './financeUtils';
import { dateTime, money } from '../../utils/format';
import { ATTEMPT_STATUS } from '../../utils/status';

function LinkModal({ link, onClose }) {
  const { notify } = useToast();
  const copy = async () => {
    try {
      await navigator.clipboard.writeText(link.url);
      notify('Payment link copied.');
    } catch (e) {
      notify('Could not copy automatically. Select the link and copy it.', 'warning');
    }
  };
  return (
    <Modal show title="Payment link" onClose={onClose}
      footer={<button type="button" className="btn btn-light" onClick={onClose}>Close</button>}>
      <p className="small text-muted-hh">
        Share this link with the resident (for example on WhatsApp). Anyone who has it can pay this invoice, and nothing else.
        It expires on {dateTime(link.expiresAt)} and stops working when a newer link is created or the invoice is settled.
      </p>
      <div className="input-group">
        <input className="form-control" readOnly value={link.url} aria-label="Payment link" onFocus={(e) => e.target.select()} />
        <button type="button" className="btn btn-primary" onClick={copy}><Icon className="bi bi-clipboard-check me-1" />Copy</button>
      </div>
      <div className="small text-muted-hh mt-2">This link is shown only now. Creating another one replaces it.</div>
    </Modal>
  );
}

function AttemptRow({ attempt, simulatedMode, onChanged }) {
  const { notify } = useToast();
  const [busy, setBusy] = useState(false);
  const run = async (fn) => {
    setBusy(true);
    try {
      await fn();
      onChanged();
    } catch (e) {
      notify(errorMessage(e), 'danger');
    } finally {
      setBusy(false);
    }
  };
  const canRefresh = ['CREATED', 'PENDING', 'FAILED'].includes(attempt.status);
  return (
    <li className="hh-feed-item d-block">
      <div className="d-flex justify-content-between gap-2">
        <span className="fw-semibold">{money(attempt.amount)}</span>
        <StatusBadge map={ATTEMPT_STATUS} value={attempt.status} />
      </div>
      <div className="small text-muted-hh">
        {dateTime(attempt.createdAt)} · {attempt.initiatedVia === 'LINK' ? 'Pay link' : 'Staff'} · {attempt.provider}
        {attempt.simulated && ' · SIMULATED'}
      </div>
      {attempt.failureReason && <div className="small text-danger">{attempt.failureReason}</div>}
      {attempt.receiptNumber && <div className="small">Receipt {attempt.receiptNumber}</div>}
      {canRefresh && (
        <div className="d-flex gap-2 flex-wrap mt-1">
          <button type="button" className="btn btn-sm btn-light" disabled={busy}
            onClick={() => run(() => onlinePaymentApi.refresh(attempt.id))}>
            <Icon className="bi bi-arrow-repeat me-1" />Check status
          </button>
          {simulatedMode && attempt.status === 'PENDING' && (
            <>
              <button type="button" className="btn btn-sm btn-outline-success" disabled={busy}
                onClick={() => run(() => onlinePaymentApi.settleSimulated(attempt.id, 'SUCCEEDED'))}>Simulate bank success</button>
              <button type="button" className="btn btn-sm btn-outline-danger" disabled={busy}
                onClick={() => run(() => onlinePaymentApi.settleSimulated(attempt.id, 'FAILED'))}>Simulate bank failure</button>
            </>
          )}
        </div>
      )}
    </li>
  );
}

/** Online collection controls for one invoice: pay now, share a pay link, and the gateway attempt history. */
export default function OnlinePaymentPanel({ invoice, onInvoiceChanged }) {
  const { can } = useAuth();
  const { notify } = useToast();
  const config = useApi(() => onlinePaymentApi.config(), []);
  const attempts = useApi(() => onlinePaymentApi.attempts(invoice.id), [invoice.id]);
  const [paying, setPaying] = useState(false);
  const [link, setLink] = useState(null);
  const [linkBusy, setLinkBusy] = useState(false);

  const open = isOpenInvoice(invoice) && Number(invoice.balance) > 0;
  const canCollect = can('PAYMENT_COLLECT');
  const rows = attempts.data || [];
  if (!open && rows.length === 0) return null;

  const changed = () => {
    attempts.reload();
    onInvoiceChanged?.();
  };

  const createLink = async () => {
    setLinkBusy(true);
    try {
      setLink(await onlinePaymentApi.createLink(invoice.id));
    } catch (e) {
      notify(errorMessage(e), 'danger');
    } finally {
      setLinkBusy(false);
    }
  };

  return (
    <section className="hh-card mb-3 no-print" aria-labelledby="online-title">
      <div className="hh-card-header">
        <h2 className="hh-card-title" id="online-title"><Icon className="bi bi-globe2 me-2 text-primary-hh" />Online payment</h2>
      </div>
      <ModeNotice gateway={config.data} />
      {open && canCollect && (
        <div className="d-grid gap-2 mb-2">
          <button type="button" className="btn btn-primary" onClick={() => setPaying(true)} disabled={!config.data}>
            <Icon className="bi bi-credit-card me-2" />Collect online
          </button>
          <button type="button" className="btn btn-light" onClick={createLink} disabled={linkBusy || !config.data}>
            {linkBusy ? <span className="spinner-border spinner-border-sm me-2" /> : <Icon className="bi bi-link-45deg me-2" />}
            Create payment link
          </button>
        </div>
      )}
      {rows.length > 0 && (
        <>
          <div className="small fw-semibold text-muted-hh mt-2">Gateway attempts</div>
          <ul className="list-unstyled mb-0">
            {rows.map((a) => (
              <AttemptRow key={a.id} attempt={a} simulatedMode={config.data?.simulated} onChanged={changed} />
            ))}
          </ul>
        </>
      )}
      <Modal show={paying} title={`Collect online · ${invoice.invoiceNumber}`} onClose={() => { setPaying(false); changed(); }}>
        {config.data && (
          <PayPanel api={onlinePaymentApi.forInvoice(invoice.id)} balance={invoice.balance} gateway={config.data}
            onChanged={changed} onPaid={() => { setPaying(false); changed(); }} />
        )}
      </Modal>
      {link && <LinkModal link={link} onClose={() => setLink(null)} />}
    </section>
  );
}
