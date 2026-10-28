import { useEffect, useRef, useState } from 'react';
import Drawer from '../common/Drawer';
import AuthImage from '../common/AuthImage';
import StatusBadge from '../common/StatusBadge';
import { AsyncContent } from '../common/States';
import useApi from '../../hooks/useApi';
import { useToast } from '../../context/ToastContext';
import { maintenanceApi } from '../../services/endpoints';
import { errorMessage, openFile } from '../../services/apiClient';
import { dateTime, fromNow, label, money } from '../../utils/format';
import { MAINT_STATUS, PRIORITY_META } from './constants';
import { useStaffForHostel } from './MaintenanceFormModal';
import Icon from '../common/Icon';

const DONE = ['RESOLVED', 'CLOSED'];
const apiPath = (url) => String(url || '').replace(/^\/api/, '');

function StatusForm({ request, staff, onChanged }) {
  const { notify } = useToast();
  const [form, setForm] = useState({});
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  useEffect(() => {
    setForm({ status: '', note: '', assignedStaffId: request.assignedStaffId || '', actualCost: request.actualCost ?? '', recordExpense: false });
    setError(null);
  }, [request]);
  const set = (k) => (e) => setForm((f) => ({ ...f, [k]: e.target.type === 'checkbox' ? e.target.checked : e.target.value }));
  const resolving = DONE.includes(form.status);

  const submit = async (e) => {
    e.preventDefault();
    if (!form.status) { setError('Choose the new status'); return; }
    if (form.status === 'ASSIGNED' && !form.assignedStaffId) { setError('Choose a staff member to assign'); return; }
    setBusy(true); setError(null);
    try {
      const updated = await maintenanceApi.changeStatus(request.id, {
        status: form.status,
        note: form.note.trim() || null,
        assignedStaffId: form.assignedStaffId ? Number(form.assignedStaffId) : null,
        actualCost: form.actualCost === '' ? null : Number(form.actualCost),
        recordExpense: resolving && !!form.recordExpense,
      });
      notify(`Status changed to ${MAINT_STATUS[updated.status]?.label || updated.status}${resolving && form.recordExpense && form.actualCost ? ' · expense recorded' : ''}`);
      onChanged(updated);
    } catch (err) { setError(errorMessage(err)); } finally { setBusy(false); }
  };

  return (
    <form onSubmit={submit} className="hh-card mb-3" noValidate>
      <h3 className="hh-card-title mb-3">Update status</h3>
      {error && <div className="alert alert-danger py-2">{error}</div>}
      <div className="mb-3">
        <label className="form-label" htmlFor="ms-status">New status</label>
        <select id="ms-status" className="form-select" value={form.status || ''} onChange={set('status')}>
          <option value="">Select…</option>
          {Object.entries(MAINT_STATUS).filter(([k]) => k !== request.status).map(([k, m]) => <option key={k} value={k}>{m.label}</option>)}
        </select>
      </div>
      <div className="mb-3">
        <label className="form-label" htmlFor="ms-staff">Assigned staff</label>
        <select id="ms-staff" className="form-select" value={form.assignedStaffId || ''} onChange={set('assignedStaffId')}>
          <option value="">{request.assignedStaffId ? 'Keep current' : 'Not assigned'}</option>
          {staff.filter((s) => s.status === 'ACTIVE' || s.id === request.assignedStaffId).map((s) => <option key={s.id} value={s.id}>{s.fullName} · {s.designation}</option>)}
        </select>
      </div>
      <div className="mb-3">
        <label className="form-label" htmlFor="ms-note">{resolving ? 'Resolution notes' : 'Note'}</label>
        <textarea id="ms-note" rows={2} className="form-control" maxLength={1000} value={form.note || ''} onChange={set('note')} />
      </div>
      <div className="mb-3">
        <label className="form-label" htmlFor="ms-cost">Actual cost (₹)</label>
        <input id="ms-cost" type="number" min="0" step="0.01" inputMode="decimal" className="form-control" value={form.actualCost ?? ''} onChange={set('actualCost')} />
      </div>
      {resolving && (
        <div className="form-check mb-3">
          <input id="ms-expense" type="checkbox" className="form-check-input" checked={!!form.recordExpense} onChange={set('recordExpense')} disabled={!Number(form.actualCost)} />
          <label className="form-check-label" htmlFor="ms-expense">Also record the actual cost as a maintenance expense</label>
          {!Number(form.actualCost) && <div className="form-text">Enter the actual cost to record an expense.</div>}
        </div>
      )}
      <button type="submit" className="btn btn-primary w-100" disabled={busy}>
        {busy && <span className="spinner-border spinner-border-sm me-2" />}Save status
      </button>
    </form>
  );
}

/** Details of one maintenance request: info, status timeline, images and status change. */
export default function MaintenanceDrawer({ id, refreshKey, onClose, onEdit, onChanged }) {
  const { notify } = useToast();
  const fileRef = useRef(null);
  const [uploading, setUploading] = useState(false);
  const { data: m, setData, loading, error, reload } = useApi(() => (id ? maintenanceApi.get(id) : Promise.resolve(null)), [id, refreshKey]);
  const staff = useStaffForHostel(m?.hostelId, !!m?.hostelId);

  const upload = async (e) => {
    const files = Array.from(e.target.files || []);
    e.target.value = '';
    if (files.length === 0) return;
    setUploading(true);
    try {
      for (const f of files) {
        // eslint-disable-next-line no-await-in-loop
        await maintenanceApi.uploadImage(id, f);
      }
      notify(`${files.length} photo(s) uploaded`);
      reload();
    } catch (err) { notify(errorMessage(err, 'Upload failed'), 'danger'); } finally { setUploading(false); }
  };

  return (
    <Drawer show={!!id} onClose={onClose} width={560} title={m ? m.title : 'Maintenance request'}
      subtitle={m && `#${m.id} · ${m.hostelName || ''}${m.roomNumber ? ` · Room ${m.roomNumber}` : ''}${m.bedLabel ? ` · Bed ${m.bedLabel}` : ''}`}
      footer={m && m.status !== 'CLOSED' && (
        <button type="button" className="btn btn-light" onClick={() => onEdit(m)}><Icon className="bi bi-pencil me-1" />Edit details</button>
      )}>
      <AsyncContent loading={loading && !m} error={error} onRetry={reload}>
        {m && (
          <>
            <div className="d-flex flex-wrap gap-2 mb-3">
              <StatusBadge map={MAINT_STATUS} value={m.status} />
              <StatusBadge map={PRIORITY_META} value={m.priority} />
              {m.blocksAvailability && !DONE.includes(m.status) && (
                <span className="badge hh-badge hh-badge-danger"><Icon className="bi bi-sign-stop me-1" />Blocking {m.bedId ? 'bed' : 'room'}</span>
              )}
            </div>
            {m.description && <p style={{ whiteSpace: 'pre-wrap' }}>{m.description}</p>}
            <dl className="hh-kv mb-3">
              <dt>Category</dt><dd>{m.category ? label(m.category) : '—'}</dd>
              <dt>Location</dt><dd>{m.roomId ? `Room ${m.roomNumber}${m.bedLabel ? ` · Bed ${m.bedLabel}` : ''}` : 'Common area'}</dd>
              <dt>Hostel</dt><dd>{m.hostelName || '—'}</dd>
              <dt>Assigned to</dt><dd>{m.assignedStaffName || '—'}</dd>
              <dt>Estimated cost</dt><dd>{money(m.estimatedCost)}</dd>
              <dt>Actual cost</dt><dd>{money(m.actualCost)}</dd>
              <dt>Reported</dt><dd>{dateTime(m.createdAt)}</dd>
              {m.resolvedAt && <><dt>Resolved</dt><dd>{dateTime(m.resolvedAt)}</dd></>}
              {m.resolutionNotes && <><dt>Resolution</dt><dd>{m.resolutionNotes}</dd></>}
            </dl>

            {m.status !== 'CLOSED' && <StatusForm request={m} staff={staff} onChanged={(u) => { setData(u); onChanged?.(u); }} />}

            <section className="hh-card mb-3">
              <div className="hh-card-header">
                <h3 className="hh-card-title">Photos</h3>
                <button type="button" className="btn btn-sm btn-soft" onClick={() => fileRef.current?.click()} disabled={uploading}>
                  {uploading ? <span className="spinner-border spinner-border-sm me-1" /> : <Icon className="bi bi-camera me-1" />}Add photo
                </button>
                <input ref={fileRef} type="file" accept="image/*" capture="environment" multiple hidden onChange={upload} />
              </div>
              {m.images?.length ? (
                <div className="d-grid gap-2" style={{ gridTemplateColumns: 'repeat(auto-fill, minmax(110px, 1fr))' }}>
                  {m.images.map((img) => (
                    <button key={img.id} type="button" className="p-0 border-0 bg-transparent" title={img.originalName}
                      onClick={() => openFile(apiPath(img.url)).catch(() => notify('Could not open the photo', 'danger'))}>
                      <span className="ratio ratio-1x1 d-block bg-light rounded-3 overflow-hidden">
                        <AuthImage src={img.url} alt={img.originalName || 'Maintenance photo'} className="w-100 h-100 object-fit-cover"
                          fallback={<span className="d-flex align-items-center justify-content-center"><Icon className="bi bi-image text-muted-hh" /></span>} />
                      </span>
                    </button>
                  ))}
                </div>
              ) : <div className="small text-muted-hh">No photos yet. Add before/after photos for the record.</div>}
            </section>

            <section className="hh-card">
              <h3 className="hh-card-title mb-2">Timeline</h3>
              {(m.history || []).length === 0 && <div className="small text-muted-hh">No history recorded.</div>}
              <ol className="list-unstyled mb-0">
                {[...(m.history || [])].reverse().map((h) => (
                  <li key={h.id} className="hh-feed-item">
                    <span className="hh-feed-icon bg-primary-light"><Icon className={`bi ${MAINT_STATUS[h.statusTo]?.icon || 'bi-circle'}`} aria-hidden="true" /></span>
                    <span className="min-w-0">
                      <span className="d-block fw-semibold">
                        {h.statusFrom && h.statusFrom !== h.statusTo ? `${MAINT_STATUS[h.statusFrom]?.label || h.statusFrom} → ` : ''}
                        {MAINT_STATUS[h.statusTo]?.label || h.statusTo}
                      </span>
                      {h.staffName && <span className="d-block small">Assigned: {h.staffName}</span>}
                      {h.note && <span className="d-block small text-muted-hh">{h.note}</span>}
                      <span className="d-block small text-muted-hh" title={dateTime(h.createdAt)}>{fromNow(h.createdAt)}</span>
                    </span>
                  </li>
                ))}
              </ol>
            </section>
          </>
        )}
      </AsyncContent>
    </Drawer>
  );
}
