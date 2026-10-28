import { useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import useApi from '../../hooks/useApi';
import { adminApi } from '../../services/endpoints';
import PageHeader from '../../components/common/PageHeader';
import DataTable, { RowAction, RowActions } from '../../components/common/DataTable';
import Pagination from '../../components/common/Pagination';
import StatusBadge from '../../components/common/StatusBadge';
import { EmptyState, ErrorState, LoadingState } from '../../components/common/States';
import { date, label, number } from '../../utils/format';
import Icon from '../../components/common/Icon';
import OwnerStatusDialog from './OwnerStatusDialog';
import { OWNER_STATUS, OwnerLink, saCrumbs } from './saCommon';

const STATUS_FILTERS = [{ value: 'ALL', label: 'All statuses' }, { value: 'ACTIVE', label: 'Active' },
  { value: 'SUSPENDED', label: 'Suspended' }, { value: 'INACTIVE', label: 'Inactive' }];

export default function SaOwnersPage() {
  const navigate = useNavigate();
  const { data, loading, error, reload } = useApi(() => adminApi.owners(), []);
  const [q, setQ] = useState('');
  const [status, setStatus] = useState('ALL');
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(10);
  const [change, setChange] = useState(null);

  const filtered = useMemo(() => {
    const term = q.trim().toLowerCase();
    return (data || []).filter((o) => (status === 'ALL' || o.status === status)
      && (!term || [o.businessName, o.contactName, o.email, o.phone].some((v) => (v || '').toLowerCase().includes(term))));
  }, [data, q, status]);

  const totalPages = Math.max(1, Math.ceil(filtered.length / size));
  const current = Math.min(page, totalPages - 1);
  const rows = filtered.slice(current * size, current * size + size);

  const columns = [
    { key: 'name', label: 'Owner', sortValue: (o) => (o.contactName || o.businessName).toLowerCase(), render: (o) => (
      <div><OwnerLink id={o.id}>{o.contactName || o.businessName}</OwnerLink><div className="small text-muted-hh">{o.businessName}</div></div>) },
    { key: 'email', label: 'Email', sortValue: (o) => o.email, hideOnMobile: true, render: (o) => o.email },
    { key: 'phone', label: 'Mobile', sortValue: (o) => o.phone || '', hideOnMobile: true, render: (o) => o.phone || '—' },
    { key: 'hostels', label: 'Hostels', align: 'center', sortValue: (o) => o.hostels, render: (o) => number(o.hostels) },
    { key: 'managers', label: 'Managers', align: 'center', sortValue: (o) => o.managers, render: (o) => number(o.managers) },
    { key: 'plan', label: 'Plan', sortValue: (o) => o.planName || '', render: (o) => (o.planName
      ? <span>{o.planName}<span className="d-block small text-muted-hh">{label(o.subscriptionStatus)}{o.subscriptionEndsOn ? ` · to ${date(o.subscriptionEndsOn)}` : ''}</span></span> : '—') },
    { key: 'createdAt', label: 'Registered', sortValue: (o) => o.createdAt, hideOnMobile: true, render: (o) => date(o.createdAt) },
    { key: 'status', label: 'Status', align: 'center', sortValue: (o) => o.status, render: (o) => <StatusBadge map={OWNER_STATUS} value={o.status} /> },
    { key: 'actions', label: 'Actions', align: 'center', sortable: false, required: true, render: (o) => (
      <RowActions>
        <RowAction variant="view" label="View details" onClick={() => navigate(`/super-admin/owners/${o.id}`)} />
        <RowAction variant="edit" label="Edit owner" onClick={() => navigate(`/super-admin/owners/${o.id}/edit`)} />
        {o.status === 'ACTIVE'
          ? <RowAction variant="delete" icon="bi-pause-circle" label="Suspend owner" onClick={() => setChange({ owner: o, status: 'SUSPENDED' })} />
          : <RowAction variant="pay" icon="bi-play-circle" label="Activate owner" onClick={() => setChange({ owner: o, status: 'ACTIVE' })} />}
      </RowActions>) },
  ];

  const toolbar = (
    <>
      <div className="hh-search-input" role="search">
        <Icon className="bi bi-search" aria-hidden="true" />
        <input type="search" className="form-control" placeholder="Search name, email or mobile…" aria-label="Search owners"
          value={q} onChange={(e) => { setQ(e.target.value); setPage(0); }} />
      </div>
      <select className="form-select w-auto" aria-label="Filter by status" value={status} onChange={(e) => { setStatus(e.target.value); setPage(0); }}>
        {STATUS_FILTERS.map((s) => <option key={s.value} value={s.value}>{s.label}</option>)}
      </select>
    </>
  );

  return (
    <>
      <PageHeader title="Owners" icon="bi-people-fill" subtitle="Hostel owner accounts on the platform" breadcrumb={saCrumbs({ label: 'Owners' })}
        actions={<Link to="/super-admin/owners/new" className="btn btn-primary"><Icon className="bi bi-person-plus me-1" />Create owner</Link>} />
      {error ? <ErrorState error={error} onRetry={reload} /> : (
        <DataTable tableId="sa-owners" columns={columns} rows={rows} serial serialStart={current * size} toolbar={toolbar}
          onRowClick={(o) => navigate(`/super-admin/owners/${o.id}`)}
          empty={loading && !data ? <LoadingState /> : (
            <EmptyState icon="bi-people" title={q || status !== 'ALL' ? 'No matching owners' : 'No owners yet'}
              message={q || status !== 'ALL' ? 'Try a different search or status filter.' : 'Create the first owner account to get started.'}
              action={!q && status === 'ALL' && <Link to="/super-admin/owners/new" className="btn btn-primary">Create owner</Link>} />
          )}
          footer={<Pagination page={current} totalPages={totalPages} totalElements={filtered.length} size={size} noun="owners"
            onChange={setPage} onSizeChange={(s) => { setSize(s); setPage(0); }} />} />
      )}
      <OwnerStatusDialog change={change} onClose={() => setChange(null)} onDone={() => { setChange(null); reload(); }} />
    </>
  );
}
