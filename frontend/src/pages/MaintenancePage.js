import { useCallback, useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import useApi from '../hooks/useApi';
import { maintenanceApi } from '../services/endpoints';
import { useHostels } from '../context/HostelContext';
import PageHeader from '../components/common/PageHeader';
import { EmptyState, ErrorState, LoadingState } from '../components/common/States';
import DataTable, { RowAction, RowActions } from '../components/common/DataTable';
import Pagination from '../components/common/Pagination';
import HostelFilter from '../components/common/HostelFilter';
import StatusBadge from '../components/common/StatusBadge';
import MaintenanceFormModal from '../components/operations/MaintenanceFormModal';
import MaintenanceDrawer from '../components/operations/MaintenanceDrawer';
import { MAINT_STATUS, PRIORITY_META } from '../components/operations/constants';
import { fromNow, label, money } from '../utils/format';
import Icon from '../components/common/Icon';

const TONES = { OPEN: 'red', ASSIGNED: 'blue', IN_PROGRESS: 'yellow', ON_HOLD: 'gray', RESOLVED: 'green', CLOSED: 'gray' };

/** Count of requests per status (one light request per status, size=1). */
function useStatusCounts(hostelId, refreshKey) {
  return useApi(async () => {
    const keys = Object.keys(MAINT_STATUS);
    const results = await Promise.all(keys.map((s) => maintenanceApi.list({ hostelId, status: s, size: 1 })));
    return Object.fromEntries(keys.map((k, i) => [k, results[i].totalElements]));
  }, [hostelId, refreshKey]);
}

export default function MaintenancePage() {
  const [params, setParams] = useSearchParams();
  const { selectedHostelId } = useHostels();
  const [hostelId, setHostelId] = useState(selectedHostelId);
  const [status, setStatus] = useState(params.get('status') || 'ACTIVE');
  const [priority, setPriority] = useState('ALL');
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [refreshKey, setRefreshKey] = useState(0);
  const [form, setForm] = useState(null); // { request } or { prefill }
  const openId = params.get('id');

  useEffect(() => { document.title = 'Maintenance · HostelHub'; }, []);
  useEffect(() => { setHostelId(selectedHostelId); }, [selectedHostelId]);
  useEffect(() => { setPage(0); }, [hostelId, status, priority]);

  // ?new=1&roomId=&hostelId= opens the create form prefilled.
  useEffect(() => {
    if (params.get('new') === '1') {
      setForm({ prefill: {
        hostelId: params.get('hostelId') ? Number(params.get('hostelId')) : null,
        roomId: params.get('roomId') ? Number(params.get('roomId')) : null,
        bedId: params.get('bedId') ? Number(params.get('bedId')) : null,
      } });
    }
  }, [params]);

  const { data, loading, error, reload } = useApi(() => maintenanceApi.list({ hostelId, status, priority, page, size }),
    [hostelId, status, priority, page, size, refreshKey]);
  const { data: counts, loading: countsLoading } = useStatusCounts(hostelId, refreshKey);
  const active = useMemo(() => (counts ? ['OPEN', 'ASSIGNED', 'IN_PROGRESS', 'ON_HOLD'].reduce((s, k) => s + (counts[k] || 0), 0) : null), [counts]);

  const setParam = useCallback((changes) => {
    const p = new URLSearchParams(params);
    Object.entries(changes).forEach(([k, v]) => (v === null || v === undefined ? p.delete(k) : p.set(k, v)));
    setParams(p, { replace: true });
  }, [params, setParams]);

  const closeForm = () => { setForm(null); if (params.get('new')) setParam({ new: null, roomId: null, hostelId: null, bedId: null }); };
  const refresh = () => setRefreshKey((k) => k + 1);
  const rows = data?.content || [];

  const columns = [
    { key: 'title', label: 'Issue', required: true, width: '28%', sortValue: (m) => m.title, render: (m) => (
      <div className="min-w-0">
        <span className="hh-cell-primary text-truncate" style={{ maxWidth: 320 }}>{m.title}</span>
        <span className="hh-cell-sub">{m.category ? label(m.category) : 'General'} · {fromNow(m.createdAt)}</span>
      </div>
    ) },
    { key: 'location', label: 'Location', sortValue: (m) => m.roomNumber || '', render: (m) => (
      <div>
        <span>{m.roomNumber ? `Room ${m.roomNumber}${m.bedLabel ? ` · Bed ${m.bedLabel}` : ''}` : 'Common area'}</span>
        <span className="hh-cell-sub">{m.hostelName}</span>
      </div>
    ) },
    { key: 'priority', label: 'Priority', align: 'center', sortValue: (m) => m.priority, render: (m) => <StatusBadge map={PRIORITY_META} value={m.priority} /> },
    { key: 'status', label: 'Status', align: 'center', sortValue: (m) => m.status, render: (m) => (
      <span className="d-inline-flex flex-wrap gap-1 justify-content-center">
        <StatusBadge map={MAINT_STATUS} value={m.status} />
        {m.blocksAvailability && !['RESOLVED', 'CLOSED'].includes(m.status) && (
          <span className="badge hh-badge hh-badge-danger" title="Room/bed cannot be allocated until resolved"><Icon className="bi bi-sign-stop me-1" />Blocking</span>
        )}
      </span>
    ) },
    { key: 'assignedStaffName', label: 'Assigned', hideOnMobile: true, sortValue: (m) => m.assignedStaffName || '',
      render: (m) => m.assignedStaffName || <span className="hh-dash">—</span> },
    { key: 'cost', label: 'Cost', align: 'end', hideOnMobile: true, sortValue: (m) => Number(m.actualCost ?? m.estimatedCost ?? 0),
      render: (m) => (m.actualCost != null ? money(m.actualCost) : m.estimatedCost != null ? <span className="text-muted-hh">~{money(m.estimatedCost)}</span> : <span className="hh-dash">—</span>) },
    { key: 'actions', label: 'Actions', align: 'center', sortable: false, required: true, render: (m) => (
      <RowActions>
        <RowAction variant="view" label="Open request" onClick={() => setParam({ id: m.id })} />
      </RowActions>
    ) },
  ];

  const toolbar = (
    <>
      <div style={{ minWidth: 170 }}><HostelFilter value={hostelId} onChange={setHostelId} id="maint-hostel-filter" /></div>
      <select className="form-select w-auto" aria-label="Status" value={status} onChange={(e) => setStatus(e.target.value)}>
        <option value="ACTIVE">Active (not resolved){active !== null ? ` · ${active}` : ''}</option>
        <option value="ALL">All statuses</option>
        {Object.entries(MAINT_STATUS).map(([k, m]) => <option key={k} value={k}>{m.label}</option>)}
      </select>
      <select className="form-select w-auto" aria-label="Priority" value={priority} onChange={(e) => setPriority(e.target.value)}>
        <option value="ALL">All priorities</option>
        {Object.entries(PRIORITY_META).map(([k, m]) => <option key={k} value={k}>{m.label}</option>)}
      </select>
    </>
  );

  return (
    <>
      <PageHeader title="Maintenance" icon="bi-tools" subtitle="Track repairs, assign staff and keep blocked rooms out of allocation."
        actions={<button type="button" className="btn btn-primary" onClick={() => setForm({ prefill: { hostelId } })}><Icon className="bi bi-plus-lg me-1" />New request</button>} />

      <div className="row g-2 mb-3" aria-label="Requests by status">
        {Object.entries(MAINT_STATUS).map(([k, m]) => (
          <div className="col-6 col-md-4 col-xl-2" key={k}>
            <button type="button" className={`btn p-0 w-100 text-start border-0 bg-transparent rounded-4 ${status === k ? 'shadow-sm' : ''}`}
              style={status === k ? { outline: '2px solid var(--hh-primary)', outlineOffset: 2 } : undefined}
              aria-pressed={status === k} onClick={() => setStatus(status === k ? 'ACTIVE' : k)}>
              <div className={`hh-summary-card tone-${TONES[k]}`}>
                <div className="hh-summary-icon" aria-hidden="true"><Icon className={`bi ${m.icon}`} /></div>
                <div className="min-w-0">
                  <div className="hh-summary-label">{m.label}</div>
                  <div className="hh-summary-value">{countsLoading && !counts ? <span className="placeholder col-6" /> : counts?.[k] ?? '—'}</div>
                </div>
              </div>
            </button>
          </div>
        ))}
      </div>

      {error ? <ErrorState error={error} onRetry={reload} /> : (
        <div style={{ opacity: loading && data ? 0.6 : 1, transition: 'opacity .15s' }}>
          <DataTable tableId="maintenance" columns={columns} rows={rows} serial serialStart={(data?.page || 0) * (data?.size || size)}
            onRowClick={(m) => setParam({ id: m.id })} toolbar={toolbar}
            empty={loading && !data ? <LoadingState /> : (
              <EmptyState icon="bi-tools" title={status === 'ACTIVE' ? 'No open maintenance requests' : 'No requests match these filters'}
                message={status === 'ACTIVE' ? 'Everything is in working order.' : 'Try another status or hostel.'} />
            )}
            footer={data && <Pagination page={data.page || 0} totalPages={data.totalPages} totalElements={data.totalElements} size={data.size}
              noun="requests" onChange={setPage} onSizeChange={(s) => { setSize(s); setPage(0); }} />} />
        </div>
      )}

      <MaintenanceDrawer id={openId} refreshKey={refreshKey} onClose={() => setParam({ id: null })}
        onEdit={(m) => setForm({ request: m })} onChanged={refresh} />
      <MaintenanceFormModal show={!!form} request={form?.request || null} prefill={form?.prefill || null} onClose={closeForm}
        onSaved={(saved) => {
          setForm(null);
          refresh();
          setParam({ new: null, roomId: null, hostelId: null, bedId: null, id: saved.id });
        }} />
    </>
  );
}
