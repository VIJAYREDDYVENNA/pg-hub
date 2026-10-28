import { useCallback, useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import useApi from '../hooks/useApi';
import useDebounce from '../hooks/useDebounce';
import { bookingApi, enquiryApi } from '../services/endpoints';
import { errorMessage } from '../services/apiClient';
import { useAuth } from '../context/AuthContext';
import { useHostels } from '../context/HostelContext';
import { useToast } from '../context/ToastContext';
import PageHeader from '../components/common/PageHeader';
import DataTable, { RowAction, RowActions } from '../components/common/DataTable';
import Pagination from '../components/common/Pagination';
import HostelFilter from '../components/common/HostelFilter';
import StatusBadge from '../components/common/StatusBadge';
import ConfirmDialog from '../components/common/ConfirmDialog';
import Modal from '../components/common/Modal';
import Tabs from '../components/operations/Tabs';
import { EmptyState, ErrorState, LoadingState } from '../components/common/States';
import EnquiryFormModal from '../components/enquiries/EnquiryFormModal';
import BookingFormModal from '../components/enquiries/BookingFormModal';
import { BOOKING_STATUS, ENQUIRY_STATUS } from '../utils/status';
import { date, money, telLink, today } from '../utils/format';
import Icon from '../components/common/Icon';

const CLOSED = ['CONVERTED', 'LOST'];

/** Pick the hostel for a new resident when the enquiry has none; confirms before creating the resident record. */
function ConvertDialog({ enquiry, onClose, onDone }) {
  const { hostels } = useHostels();
  const { notify } = useToast();
  const [hostelId, setHostelId] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);
  useEffect(() => { setHostelId(enquiry?.hostelId ? String(enquiry.hostelId) : ''); setError(null); }, [enquiry]);
  if (!enquiry) return null;

  const go = async () => {
    setBusy(true);
    setError(null);
    try {
      const r = await enquiryApi.convert(enquiry.id, Number(hostelId));
      notify(`${enquiry.fullName} is now a registered resident. Admit them to a bed from their profile.`);
      onDone(r.residentId);
    } catch (e) {
      setError(errorMessage(e, 'Could not create the resident.'));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal show title={`Convert ${enquiry.fullName} to a resident`} onClose={onClose}
      footer={<>
        <button type="button" className="btn btn-light" onClick={onClose} disabled={busy}>Cancel</button>
        <button type="button" className="btn btn-primary" onClick={go} disabled={busy || !hostelId}>
          {busy && <span className="spinner-border spinner-border-sm me-2" />}Create resident
        </button>
      </>}>
      {error && <div className="alert alert-danger py-2" role="alert">{error}</div>}
      <p>This creates a registered resident from the enquiry (name, mobile, email and WhatsApp consent). You can then admit them to a bed.</p>
      <label htmlFor="convert-hostel" className="form-label">Hostel</label>
      <select id="convert-hostel" className="form-select" value={hostelId} onChange={(e) => setHostelId(e.target.value)}>
        <option value="">Select hostel…</option>
        {hostels.map((h) => <option key={h.id} value={h.id}>{h.name}</option>)}
      </select>
    </Modal>
  );
}

function EnquiriesTab({ canResident }) {
  const navigate = useNavigate();
  const { hostels } = useHostels();
  const [params, setParams] = useSearchParams();
  const [hostelId, setHostelId] = useState(null);
  const [status, setStatus] = useState('ALL');
  const [q, setQ] = useState('');
  const dq = useDebounce(q, 300);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [form, setForm] = useState(undefined); // undefined = closed, null = new, object = edit
  const [reserve, setReserve] = useState(null);
  const [convert, setConvert] = useState(null);
  const [refreshKey, setRefreshKey] = useState(0);
  const refresh = () => setRefreshKey((k) => k + 1);

  const { data, loading, error, reload } = useApi(
    () => enquiryApi.list({ hostelId, status, q: dq.trim() || undefined, page, size }), [hostelId, status, dq, page, size, refreshKey]);
  useEffect(() => { setPage(0); }, [hostelId, status, dq]);

  // Notification links open one enquiry directly: /enquiries?id=12
  const openId = params.get('id') ? Number(params.get('id')) : null;
  useEffect(() => {
    if (!openId) return undefined;
    let alive = true;
    enquiryApi.get(openId).then((e) => { if (alive) setForm(e); }).catch(() => {});
    return () => { alive = false; };
  }, [openId]);
  const closeForm = () => {
    setForm(undefined);
    if (params.get('id')) { const p = new URLSearchParams(params); p.delete('id'); setParams(p, { replace: true }); }
  };

  const hostelName = (id) => hostels.find((h) => h.id === id)?.name;
  const overdue = (e) => e.nextFollowUpDate && e.nextFollowUpDate < today() && !CLOSED.includes(e.status);

  const columns = [
    { key: 'fullName', label: 'Enquirer', required: true, sortValue: (e) => e.fullName.toLowerCase(), render: (e) => (
      <div className="min-w-0"><span className="hh-cell-primary">{e.fullName}</span><span className="hh-cell-sub">{e.mobile}{e.email ? ` · ${e.email}` : ''}</span></div>
    ) },
    { key: 'interest', label: 'Looking for', hideOnMobile: true, sortValue: (e) => e.preferredRoomType || '', render: (e) => (
      <div><span>{e.preferredRoomType || 'Any room'}</span>
        <span className="hh-cell-sub">{hostelName(e.hostelId) || 'Any hostel'}{e.budget != null ? ` · up to ${money(e.budget)}` : ''}</span></div>
    ) },
    { key: 'expected', label: 'Wants to move in', hideOnMobile: true, sortValue: (e) => e.expectedAdmissionDate || '9999', render: (e) => (e.expectedAdmissionDate ? date(e.expectedAdmissionDate) : <span className="hh-dash">—</span>) },
    { key: 'followUp', label: 'Follow up', sortValue: (e) => e.nextFollowUpDate || '9999', render: (e) => (e.nextFollowUpDate
      ? <span className={overdue(e) ? 'text-danger fw-semibold' : ''}>{overdue(e) && <Icon className="bi bi-exclamation-circle me-1" />}{date(e.nextFollowUpDate)}{overdue(e) ? ' (overdue)' : ''}</span>
      : <span className="hh-dash">—</span>) },
    { key: 'source', label: 'Source', hideOnMobile: true, sortValue: (e) => e.source || '', render: (e) => e.source || <span className="hh-dash">—</span> },
    { key: 'status', label: 'Status', align: 'center', sortValue: (e) => e.status, render: (e) => <StatusBadge map={ENQUIRY_STATUS} value={e.status} /> },
    { key: 'actions', label: 'Actions', align: 'center', sortable: false, required: true, render: (e) => (
      <RowActions>
        <RowAction variant="edit" label="Open enquiry" onClick={() => setForm(e)} />
        <RowAction variant="call" label={`Call ${e.fullName}`} href={telLink(e.mobile)} />
        {!CLOSED.includes(e.status) && (
          <RowAction variant="view" icon="bi-bookmark-plus" label="Reserve a bed"
            onClick={() => setReserve({ enquiryId: e.id, hostelId: e.hostelId, guestName: e.fullName, guestMobile: e.mobile, expectedAdmissionDate: e.expectedAdmissionDate || undefined })} />
        )}
        {canResident && !CLOSED.includes(e.status) && <RowAction variant="view" icon="bi-person-plus" label="Convert to resident" onClick={() => setConvert(e)} />}
      </RowActions>
    ) },
  ];

  const toolbar = (
    <>
      <div className="hh-search" style={{ minWidth: 220 }}>
        <Icon className="bi bi-search" aria-hidden="true" />
        <input type="search" className="form-control" placeholder="Search name, mobile or email" aria-label="Search enquiries" value={q} onChange={(e) => setQ(e.target.value)} />
      </div>
      <div style={{ minWidth: 170 }}><HostelFilter value={hostelId} onChange={setHostelId} id="enq-hostel-filter" /></div>
      <select className="form-select w-auto" aria-label="Status" value={status} onChange={(e) => setStatus(e.target.value)}>
        <option value="ALL">All statuses</option>
        {Object.entries(ENQUIRY_STATUS).map(([k, m]) => <option key={k} value={k}>{m.label}</option>)}
      </select>
    </>
  );

  if (error) return <ErrorState error={error} onRetry={reload} />;
  return (
    <>
      <div className="d-flex justify-content-end mb-2">
        <button type="button" className="btn btn-primary" onClick={() => setForm(null)}><Icon className="bi bi-plus-lg me-1" />New enquiry</button>
      </div>
      <div style={{ opacity: loading && data ? 0.6 : 1, transition: 'opacity .15s' }}>
        <DataTable tableId="enquiries" columns={columns} rows={data?.content || []} serial serialStart={(data?.page || 0) * (data?.size || size)}
          onRowClick={(e) => setForm(e)} toolbar={toolbar}
          empty={loading && !data ? <LoadingState /> : (
            <EmptyState icon="bi-chat-dots" title={status === 'ALL' && !dq ? 'No enquiries yet' : 'No enquiries match these filters'}
              message={status === 'ALL' && !dq ? 'Record every call and walk-in here so nobody is forgotten.' : 'Try another status, hostel or search.'} />
          )}
          footer={data && <Pagination page={data.page || 0} totalPages={data.totalPages} totalElements={data.totalElements} size={data.size}
            noun="enquiries" onChange={setPage} onSizeChange={(s) => { setSize(s); setPage(0); }} />} />
      </div>
      <EnquiryFormModal show={form !== undefined} enquiry={form || null} defaultHostelId={hostelId} onClose={closeForm}
        onSaved={() => { closeForm(); refresh(); }} />
      <BookingFormModal show={!!reserve} prefill={reserve} onClose={() => setReserve(null)} onSaved={() => { setReserve(null); refresh(); }} />
      <ConvertDialog enquiry={convert} onClose={() => setConvert(null)} onDone={(residentId) => { setConvert(null); navigate(`/residents/${residentId}`); }} />
    </>
  );
}

function ReservationsTab() {
  const { notify } = useToast();
  const [hostelId, setHostelId] = useState(null);
  const [status, setStatus] = useState('RESERVED');
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [creating, setCreating] = useState(false);
  const [cancelling, setCancelling] = useState(null);
  const [refreshKey, setRefreshKey] = useState(0);
  const { data, loading, error, reload } = useApi(() => bookingApi.list({ hostelId, status, page, size }), [hostelId, status, page, size, refreshKey]);
  useEffect(() => { setPage(0); }, [hostelId, status]);

  const cancel = useCallback(async (reason) => {
    try {
      await bookingApi.cancel(cancelling.id, reason.trim());
      notify(`Reservation for ${cancelling.guestName} cancelled. The bed is free again.`, 'warning');
      setCancelling(null);
      setRefreshKey((k) => k + 1);
    } catch (e) {
      notify(errorMessage(e), 'danger');
      setCancelling(null);
    }
  }, [cancelling, notify]);

  const columns = [
    { key: 'guestName', label: 'Guest', required: true, sortValue: (b) => b.guestName.toLowerCase(), render: (b) => (
      <div className="min-w-0"><span className="hh-cell-primary">{b.guestName}</span><span className="hh-cell-sub">{b.guestMobile}</span></div>
    ) },
    { key: 'bed', label: 'Bed', sortValue: (b) => `${b.hostelName}${b.roomNumber}${b.bedLabel}`, render: (b) => (
      <div><span>Room {b.roomNumber} · Bed {b.bedLabel}</span><span className="hh-cell-sub">{b.hostelName}{b.location ? ` · ${b.location}` : ''}</span></div>
    ) },
    { key: 'arrival', label: 'Arrives', sortValue: (b) => b.expectedAdmissionDate, render: (b) => date(b.expectedAdmissionDate) },
    { key: 'hold', label: 'Held until', hideOnMobile: true, sortValue: (b) => b.holdUntil || '9999', render: (b) => (b.holdUntil ? date(b.holdUntil) : <span className="hh-dash">—</span>) },
    { key: 'advance', label: 'Advance', align: 'end', hideOnMobile: true, sortValue: (b) => Number(b.advanceAmount || 0), render: (b) => (b.advanceAmount ? money(b.advanceAmount) : <span className="hh-dash">—</span>) },
    { key: 'status', label: 'Status', align: 'center', sortValue: (b) => b.status, render: (b) => <StatusBadge map={BOOKING_STATUS} value={b.status} /> },
    { key: 'actions', label: 'Actions', align: 'center', sortable: false, required: true, render: (b) => (
      <RowActions>
        <RowAction variant="call" label={`Call ${b.guestName}`} href={telLink(b.guestMobile)} />
        {b.status === 'RESERVED' && <RowAction variant="delete" icon="bi-x-circle" label="Cancel reservation" onClick={() => setCancelling(b)} />}
      </RowActions>
    ) },
  ];

  const toolbar = (
    <>
      <div style={{ minWidth: 170 }}><HostelFilter value={hostelId} onChange={setHostelId} id="bk-hostel-filter" /></div>
      <select className="form-select w-auto" aria-label="Status" value={status} onChange={(e) => setStatus(e.target.value)}>
        <option value="ALL">All statuses</option>
        {Object.entries(BOOKING_STATUS).map(([k, m]) => <option key={k} value={k}>{m.label}</option>)}
      </select>
    </>
  );

  if (error) return <ErrorState error={error} onRetry={reload} />;
  return (
    <>
      <div className="d-flex justify-content-end mb-2">
        <button type="button" className="btn btn-primary" onClick={() => setCreating(true)}><Icon className="bi bi-bookmark-plus me-1" />Reserve a bed</button>
      </div>
      <div style={{ opacity: loading && data ? 0.6 : 1, transition: 'opacity .15s' }}>
        <DataTable tableId="bookings" columns={columns} rows={data?.content || []} serial serialStart={(data?.page || 0) * (data?.size || size)} toolbar={toolbar}
          empty={loading && !data ? <LoadingState /> : (
            <EmptyState icon="bi-bookmark" title={status === 'RESERVED' ? 'No beds are reserved' : 'No reservations match this filter'}
              message="Reserve a bed for a guest who is arriving later. Admit them from their resident profile when they arrive." />
          )}
          footer={data && <Pagination page={data.page || 0} totalPages={data.totalPages} totalElements={data.totalElements} size={data.size}
            noun="reservations" onChange={setPage} onSizeChange={(s) => { setSize(s); setPage(0); }} />} />
      </div>
      <BookingFormModal show={creating} prefill={null} onClose={() => setCreating(false)} onSaved={() => { setCreating(false); setRefreshKey((k) => k + 1); }} />
      <ConfirmDialog show={!!cancelling} variant="danger" requireReason title="Cancel reservation" confirmLabel="Cancel reservation"
        reasonLabel="Why is it being cancelled?"
        message={cancelling && `The bed reserved for ${cancelling.guestName} (room ${cancelling.roomNumber}, bed ${cancelling.bedLabel}) becomes available again.`}
        onConfirm={cancel} onClose={() => setCancelling(null)} />
    </>
  );
}

/** Leads and bed reservations. Needs ENQUIRY_MANAGE; converting an enquiry to a resident also needs RESIDENT_MANAGE. */
export default function EnquiriesPage() {
  const { can } = useAuth();
  const [params, setParams] = useSearchParams();
  const tab = params.get('tab') === 'reservations' ? 'reservations' : 'enquiries';
  useEffect(() => { document.title = 'Enquiries · HostelHub'; }, []);
  const setTab = (t) => { const p = new URLSearchParams(params); p.set('tab', t); p.delete('id'); setParams(p, { replace: true }); };

  return (
    <>
      <PageHeader title="Enquiries & reservations" icon="bi-chat-dots" subtitle="Follow up with prospective residents and hold beds for guests who are arriving." />
      <Tabs active={tab} onChange={setTab} tabs={[
        { key: 'enquiries', label: 'Enquiries', icon: 'bi-chat-dots' },
        { key: 'reservations', label: 'Reserved beds', icon: 'bi-bookmark-fill' },
      ]} />
      {tab === 'enquiries' ? <EnquiriesTab canResident={can('RESIDENT_MANAGE')} /> : <ReservationsTab />}
    </>
  );
}
