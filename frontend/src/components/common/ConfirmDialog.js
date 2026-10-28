import { useState } from 'react';
import Modal from './Modal';

/** Confirmation dialog; optionally asks for a reason (required for financial reversals). */
export default function ConfirmDialog({ show, title, message, confirmLabel = 'Confirm', variant = 'primary',
  requireReason = false, reasonLabel = 'Reason', onConfirm, onClose }) {
  const [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false);
  const submit = async () => {
    setBusy(true);
    try { await onConfirm(reason); setReason(''); } finally { setBusy(false); }
  };
  return (
    <Modal show={show} title={title} onClose={onClose}
      footer={<>
        <button type="button" className="btn btn-light" onClick={onClose} disabled={busy}>Cancel</button>
        <button type="button" className={`btn btn-${variant}`} disabled={busy || (requireReason && !reason.trim())} onClick={submit}>
          {busy && <span className="spinner-border spinner-border-sm me-2" />}{confirmLabel}
        </button>
      </>}>
      <p className="mb-3">{message}</p>
      {requireReason && (
        <div>
          <label className="form-label" htmlFor="confirm-reason">{reasonLabel}</label>
          <textarea id="confirm-reason" className="form-control" rows={2} value={reason} onChange={(e) => setReason(e.target.value)} />
        </div>
      )}
    </Modal>
  );
}
