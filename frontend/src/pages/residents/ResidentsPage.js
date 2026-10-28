import { useEffect, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import PageHeader from '../../components/common/PageHeader';
import HostelFilter from '../../components/common/HostelFilter';
import DataTable, { RowAction, RowActions } from '../../components/common/DataTable';
import Pagination from '../../components/common/Pagination';
import StatusBadge from '../../components/common/StatusBadge';
import { ErrorState, EmptyState, LoadingState } from '../../components/common/States';
import { RESIDENT_STATUS, ResidentAvatar } from '../../components/residents/ResidentBits';
import useApi from '../../hooks/useApi';
import useDebounce from '../../hooks/useDebounce';
import { useAuth } from '../../context/AuthContext';
import { useHostels } from '../../context/HostelContext';
import { residentApi } from '../../services/endpoints';
import { date, money, telLink, whatsappLink } from '../../utils/format';
import Icon from '../../components/common/Icon';

const STATUS_FILTERS = [
  { value: 'ALL', label: 'All residents' },
  { value: 'ACTIVE', label: 'Staying now' },
  { value: 'VACATING_SOON', label: 'Vacating soon' },
  { value: 'REGISTERED', label: 'Registered (not admitted)' },
  { value: 'CHECKED_OUT', label: 'Checked out' },
  { value: 'INACTIVE', label: 'Inactive' },
];
const SORTS = [
  { value: 'name,asc', label: 'Name A–Z' },
  { value: 'name,desc', label: 'Name Z–A' },
  { value: 'createdAt,desc', label: 'Newest first' },
  { value: 'admissionDate,desc', label: 'Recently admitted' },
  { value: 'checkoutDate,asc', label: 'Checkout date' },
  { value: 'room,asc', label: 'Room number' },
];
const stop = (e) => e.stopPropagation();

export default function ResidentsPage() {
  const { can } = useAuth();
  const navigate = useNavigate();
  const { selectedHostelId, selectHostel } = useHostels();
  const [params, setParams] = useSearchParams();
  const status = params.get('status') || 'ALL';
  const sort = params.get('sort') || 'name,asc';
  const page = Number(params.get('page') || 0);
  const size = Number(params.get('size') || 10);
  const [q, setQ] = useState(params.get('q') || '');
  const debouncedQ = useDebounce(q.trim(), 350);

  useEffect(() => { document.title = 'Residents · HostelHub'; }, []);

  const update = (changes) => {
    const next = new URLSearchParams(params);
    Object.entries(changes).forEach(([k, v]) => {
      if (v === null || v === undefined || v === '' || (k === 'status' && v === 'ALL')) next.delete(k); else next.set(k, String(v));
    });
    if (!('page' in changes)) next.delete('page');
    setParams(next, { replace: true });
  };

  useEffect(() => {
    if ((params.get('q') || '') !== debouncedQ) update({ q: debouncedQ });
  }, [debouncedQ]); // eslint-disable-line react-hooks/exhaustive-deps

  const { data, loading, error, reload } = useApi(
    () => residentApi.list({ hostelId: selectedHostelId, status, q: debouncedQ || undefined, sort, page, size }),
    [selectedHostelId, status, debouncedQ, sort, page, size],
  );

  const columns = [
    { key: 'name', label: 'Resident', required: true, width: '24%', sortValue: (r) => r.fullName, render: (r) => (
      <div className="d-flex align-items-center gap-2">
        <ResidentAvatar name={r.fullName} photoUrl={r.photoUrl} size={30} />
        <div className="min-w-0">
          <Link to={`/residents/${r.id}`} className="hh-cell-primary" onClick={stop}>{r.fullName}</Link>
          {r.email && <span className="hh-cell-sub text-truncate">{r.email}</span>}
        </div>
      </div>
    ) },
    { key: 'mobile', label: 'Contact', sortValue: (r) => r.mobile, render: (r) => (
      <div>
        <span className="hh-contact-line"><Icon className="bi bi-telephone" />{r.mobile}</span>
        {r.whatsappOptIn ? <span className="hh-contact-line text-success"><Icon className="bi bi-whatsapp" />WhatsApp opted in</span>
          : <span className="hh-contact-line text-muted-hh"><Icon className="bi bi-whatsapp" />No WhatsApp consent</span>}
      </div>
    ) },
    { key: 'hostel', label: 'Hostel', sortValue: (r) => r.hostelName, render: (r) => r.hostelName, hideOnMobile: !!selectedHostelId },
    { key: 'bed', label: 'Room / Bed', sortValue: (r) => r.roomNumber || '', render: (r) => (r.roomNumber
      ? <div><span className="hh-cell-primary">Room {r.roomNumber}</span> · Bed {r.bedLabel}
        {r.expectedCheckoutDate && <span className="hh-cell-sub">Leaves {date(r.expectedCheckoutDate)}</span>}</div>
      : <span className="hh-dash">—</span>) },
    { key: 'admitted', label: 'Admitted', sortValue: (r) => r.admissionDate || '', render: (r) => (r.admissionDate ? date(r.admissionDate) : <span className="hh-dash">—</span>) },
    { key: 'status', label: 'Status', align: 'center', sortValue: (r) => r.status, render: (r) => <StatusBadge map={RESIDENT_STATUS} value={r.status} /> },
    { key: 'due', label: 'Outstanding', align: 'end', sortValue: (r) => Number(r.outstandingAmount), render: (r) => {
      const overdue = Number(r.overdueAmount) > 0;
      if (!(Number(r.outstandingAmount) > 0)) return <span className="hh-badge hh-badge-success"><Icon className="bi bi-check-circle me-1" />Paid up</span>;
      return (
        <span className={overdue ? 'text-danger fw-semibold' : 'hh-cell-primary'}>
          {money(r.outstandingAmount)}
          {overdue && <span className="hh-cell-sub text-danger">{money(r.overdueAmount)} overdue</span>}
        </span>
      );
    } },
    { key: 'actions', label: 'Actions', align: 'center', sortable: false, required: true, render: (r) => (
      <RowActions>
        <RowAction variant="view" label="View profile" onClick={() => navigate(`/residents/${r.id}`)} />
        {can('RESIDENT_MANAGE') && <RowAction variant="edit" label="Edit resident" onClick={() => navigate(`/residents/${r.id}/edit`)} />}
        {can('PAYMENT_COLLECT') && Number(r.outstandingAmount) > 0 && <RowAction variant="pay" label="Collect payment" onClick={() => navigate(`/payments/collect?residentId=${r.id}`)} />}
        <RowAction variant="call" label={`Call ${r.mobile}`} href={telLink(r.mobile)} />
        <RowAction variant="whatsapp" label="Open WhatsApp chat" href={whatsappLink(r.mobile, `Hi ${r.fullName}, `)} />
      </RowActions>
    ) },
  ];

  const rows = data?.content || [];
  const filtered = status !== 'ALL' || debouncedQ;

  const toolbar = (
    <>
      <div className="hh-search-input" role="search">
        <Icon className="bi bi-search" aria-hidden="true" />
        <input type="search" className="form-control" placeholder="Search name, mobile, email or room…" aria-label="Search residents"
          value={q} onChange={(e) => setQ(e.target.value)} />
      </div>
      <div style={{ minWidth: 170 }}><HostelFilter onChange={(id) => { selectHostel(id); update({}); }} id="residents-hostel" /></div>
      <select className="form-select w-auto" aria-label="Resident status" value={status} onChange={(e) => update({ status: e.target.value })}>
        {STATUS_FILTERS.map((s) => <option key={s.value} value={s.value}>{s.label}</option>)}
      </select>
      <select className="form-select w-auto" aria-label="Sort residents" value={sort} onChange={(e) => update({ sort: e.target.value === 'name,asc' ? null : e.target.value })}>
        {SORTS.map((s) => <option key={s.value} value={s.value}>{s.label}</option>)}
      </select>
    </>
  );

  return (
    <>
      <PageHeader title="Residents" icon="bi-people" subtitle="Search, contact and manage residents"
        actions={can('RESIDENT_MANAGE') && <>
          <Link to="/residents/new" className="btn btn-light"><Icon className="bi bi-person-plus me-1" />Add resident</Link>
        </>} />

      {error ? <ErrorState error={error} onRetry={reload} /> : (
        <div style={{ opacity: loading && data ? 0.6 : 1, transition: 'opacity .15s' }}>
          <DataTable tableId="residents" columns={columns} rows={rows} serial serialStart={(data?.page || 0) * (data?.size || size)}
            onRowClick={(r) => navigate(`/residents/${r.id}`)} toolbar={toolbar}
            empty={loading && !data ? <LoadingState /> : (
              <EmptyState icon="bi-people" title={filtered ? 'No matching residents' : 'No residents yet'}
                message={filtered ? 'Try a different search or status filter.' : 'Add a resident to get started.'}
                />
            )}
            footer={data && <Pagination page={data.page} totalPages={data.totalPages} totalElements={data.totalElements} size={data.size}
              noun="residents" onChange={(p) => update({ page: p || null })} onSizeChange={(s) => update({ size: s === 10 ? null : s })} />} />
        </div>
      )}
    </>
  );
}
