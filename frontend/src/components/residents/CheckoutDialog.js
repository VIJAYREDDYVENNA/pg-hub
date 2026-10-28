import { useState } from 'react';
import Modal from '../common/Modal';
import { admissionApi } from '../../services/endpoints';
import { errorMessage } from '../../services/apiClient';
import { useToast } from '../../context/ToastContext';
import { today } from '../../utils/format';

/** Records a checkout (frees the bed). resident = { admissionId, name } */
export default function CheckoutDialog({ resident, onClose, onDone }) {
  const [checkoutDate, setDate] = useState(today());
  const [notes, setNotes] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  const { notify } = useToast();
  const submit = async () => {
    setBusy(true); setError(null);
    try {
      await admissionApi.checkout(resident.admissionId, { checkoutDate, notes });
      notify(`${resident.name} checked out. The bed is now free.`);
      onDone();
    } catch (e) { setError(errorMessage(e)); } finally { setBusy(false); }
  };
  return (
    <Modal show={!!resident} title={`Check out ${resident?.name || ''}`} onClose={onClose}
      footer={<>
        <button type="button" className="btn btn-light" onClick={onClose}>Cancel</button>
        <button type="button" className="btn btn-primary" disabled={busy} onClick={submit}>{busy && <span className="spinner-border spinner-border-sm me-2" />}Confirm checkout</button>
      </>}>
      {error && <div className="alert alert-danger py-2">{error}</div>}
      <p className="small text-muted-hh">The bed is released immediately. Unpaid rent invoices for periods after this date are cancelled; outstanding dues stay on the resident's account.</p>
      <label className="form-label" htmlFor="co-date">Checkout date</label>
      <input id="co-date" type="date" className="form-control mb-3" value={checkoutDate} max={today()} onChange={(e) => setDate(e.target.value)} />
      <label className="form-label" htmlFor="co-notes">Notes (optional)</label>
      <textarea id="co-notes" className="form-control" rows={2} value={notes} onChange={(e) => setNotes(e.target.value)} />
    </Modal>
  );
}
