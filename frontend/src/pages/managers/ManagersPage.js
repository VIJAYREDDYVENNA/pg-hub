import { useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { userApi } from '../../services/endpoints';
import { errorMessage } from '../../services/apiClient';
import { useHostels } from '../../context/HostelContext';
import { useToast } from '../../context/ToastContext';
import PageHeader from '../../components/common/PageHeader';
import DataTable, { RowAction, RowActions } from '../../components/common/DataTable';
import Pagination from '../../components/common/Pagination';
import StatusBadge from '../../components/common/StatusBadge';
import ConfirmDialog from '../../components/common/ConfirmDialog';
import AssignHostelsModal from '../../components/managers/AssignHostelsModal';
import ManagerCreateModal from '../../components/managers/ManagerCreateModal';
import ResetPasswordModal from '../../components/managers/ResetPasswordModal';
import { EmptyState, ErrorState, LoadingState } from '../../components/common/States';
import { date } from '../../utils/format';
import Icon from '../../components/common/Icon';
import { MANAGER_STATUS, updateBody, useManagers } from './managerCommon';

export default function ManagersPage() {
  const navigate = useNavigate();
  const { hostels } = useHostels();
  const { notify } = useToast();
  const { managers, data, loading, error, reload } = useManagers();
  const [q, setQ] = useState('');
  const [status, setStatus] = useState('ALL');
  const [hostelId, setHostelId] = useState('');
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(10);
  const [creating, setCreating] = useState(false);
  const [resetting, setResetting] = useState(null);
  const [assigning, setAssigning] = useState(null);
  const [toggling, setToggling] = useState(null);

  const hostelName = (id) => hostels.find((h) => h.id === id)?.name || `#${id}`;
  const filtered = useMemo(() => {
    const term = q.trim().toLowerCase();
    // Newest first, so a manager that was just created shows at the top.
    const newestFirst = [...managers].sort((a, b) => String(b.createdAt || '').localeCompare(String(a.createdAt || '')) || b.id - a.id);
    return newestFirst.filter((m) => (status === 'ALL' || String(m.enabled) === (status === 'ACTIVE' ? 'true' : 'false'))
      && (!hostelId || (m.hostelIds || []).includes(Number(hostelId)))
      && (!term || [m.fullName, m.email, m.phone].some((v) => (v || '').toLowerCase().includes(term))));
  }, [managers, q, status, hostelId]);

  const totalPages = Math.max(1, Math.ceil(filtered.length / size));
  const current = Math.min(page, totalPages - 1);
  const rows = filtered.slice(current * size, current * size + size);

  const toggleEnabled = async () => {
    const m = toggling;
    try {
      await userApi.update(m.id, updateBody(m, { enabled: !m.enabled }));
      notify(m.enabled ? `${m.fullName} deactivated and signed out` : `${m.fullName} activated`);
      setToggling(null);
      reload();
    } catch (e) {
      setToggling(null);
      notify(errorMessage(e, 'Could not update the manager.'), 'danger');
    }
  };

  const columns = [
    { key: 'name', label: 'Manager', sortValue: (m) => m.fullName.toLowerCase(), render: (m) => (
      <div><Link to={`/managers/${m.id}`} className="hh-cell-primary" onClick={(e) => e.stopPropagation()}>{m.fullName}</Link>
        <div className="small text-muted-hh">{m.email}</div></div>) },
    { key: 'phone', label: 'Mobile', sortValue: (m) => m.phone || '', hideOnMobile: true, render: (m) => m.phone || '—' },
    { key: 'hostels', label: 'Assigned hostels', sortable: false, render: (m) => (
      <div className="d-flex flex-wrap gap-1">
        {(m.hostelIds || []).length === 0 ? <span className="text-muted-hh">None</span>
          : m.hostelIds.map((id) => <span key={id} className="badge hh-badge hh-badge-info">{hostelName(id)}</span>)}
      </div>) },
    { key: 'count', label: 'Count', align: 'center', sortValue: (m) => (m.hostelIds || []).length, render: (m) => (m.hostelIds || []).length },
    { key: 'status', label: 'Status', align: 'center', sortValue: (m) => String(m.enabled), render: (m) => <StatusBadge map={MANAGER_STATUS} value={String(m.enabled)} /> },
    { key: 'createdAt', label: 'Created', sortValue: (m) => m.createdAt, hideOnMobile: true, render: (m) => date(m.createdAt) },
    { key: 'actions', label: 'Actions', align: 'center', sortable: false, required: true, render: (m) => (
      <RowActions>
        <RowAction variant="view" label="View details" onClick={() => navigate(`/managers/${m.id}`)} />
        <RowAction variant="edit" label="Edit manager" onClick={() => navigate(`/managers/${m.id}/edit`)} />
        <RowAction variant="pay" icon="bi-houses" label="Change hostel assignments" onClick={() => setAssigning(m)} />
        <RowAction variant="pay" icon="bi-key" label="Reset password" onClick={() => setResetting(m)} />
        {m.enabled
          ? <RowAction variant="delete" icon="bi-slash-circle" label="Deactivate manager" onClick={() => setToggling(m)} />
          : <RowAction variant="pay" icon="bi-play-circle" label="Activate manager" onClick={() => setToggling(m)} />}
      </RowActions>) },
  ];

  const filteredNow = q || status !== 'ALL' || hostelId;
  const toolbar = (
    <>
      <div className="hh-search-input" role="search">
        <Icon className="bi bi-search" aria-hidden="true" />
        <input type="search" className="form-control" placeholder="Search name, email or mobile…" aria-label="Search managers"
          value={q} onChange={(e) => { setQ(e.target.value); setPage(0); }} />
      </div>
      <select className="form-select w-auto" aria-label="Filter by hostel" value={hostelId} onChange={(e) => { setHostelId(e.target.value); setPage(0); }}>
        <option value="">All hostels</option>
        {hostels.map((h) => <option key={h.id} value={h.id}>{h.name}</option>)}
      </select>
      <select className="form-select w-auto" aria-label="Filter by status" value={status} onChange={(e) => { setStatus(e.target.value); setPage(0); }}>
        <option value="ALL">All statuses</option><option value="ACTIVE">Active</option><option value="INACTIVE">Inactive</option>
      </select>
    </>
  );

  return (
    <>
      <PageHeader title="Managers" icon="bi-person-gear" subtitle="Create hostel managers and choose which hostels each one can operate"
        actions={<button type="button" className="btn btn-primary" onClick={() => setCreating(true)}><Icon className="bi bi-person-plus me-1" />Create manager</button>} />
      {error ? <ErrorState error={error} onRetry={reload} /> : (
        <DataTable tableId="managers" columns={columns} rows={rows} serial serialStart={current * size} toolbar={toolbar}
          onRowClick={(m) => navigate(`/managers/${m.id}`)}
          empty={loading && !data ? <LoadingState /> : (
            <EmptyState icon="bi-person-gear" title={filteredNow ? 'No matching managers' : 'No managers yet'}
              message={filteredNow ? 'Try a different search or filter.' : 'Create a manager and assign one or more of your hostels.'}
              action={!filteredNow && <button type="button" className="btn btn-primary" onClick={() => setCreating(true)}>Create manager</button>} />
          )}
          footer={<Pagination page={current} totalPages={totalPages} totalElements={filtered.length} size={size} noun="managers"
            onChange={setPage} onSizeChange={(s) => { setSize(s); setPage(0); }} />} />
      )}
      <ManagerCreateModal show={creating} managers={managers} onClose={() => setCreating(false)}
        onCreated={() => { setCreating(false); setQ(''); setStatus('ALL'); setHostelId(''); setPage(0); reload(); }} />
      <ResetPasswordModal user={resetting} onClose={() => setResetting(null)} />
      <AssignHostelsModal manager={assigning} managers={managers} onClose={() => setAssigning(null)}
        onSaved={() => { setAssigning(null); reload(); }} />
      {toggling && (
        <ConfirmDialog show title={toggling.enabled ? 'Deactivate manager' : 'Activate manager'}
          message={toggling.enabled
            ? `Deactivate ${toggling.fullName}? They are signed out immediately and cannot sign in until reactivated. Their hostel assignments are kept.`
            : `Activate ${toggling.fullName}? They can sign in again and will see their assigned hostels.`}
          confirmLabel={toggling.enabled ? 'Deactivate' : 'Activate'} variant={toggling.enabled ? 'danger' : 'primary'}
          onConfirm={toggleEnabled} onClose={() => setToggling(null)} />
      )}
    </>
  );
}
