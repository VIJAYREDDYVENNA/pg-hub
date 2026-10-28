import { useMemo, useState } from 'react';
import useApi from '../../hooks/useApi';
import { adminApi } from '../../services/endpoints';
import PageHeader from '../../components/common/PageHeader';
import DataTable from '../../components/common/DataTable';
import Pagination from '../../components/common/Pagination';
import StatusBadge from '../../components/common/StatusBadge';
import { EmptyState, ErrorState, LoadingState } from '../../components/common/States';
import { date, label } from '../../utils/format';
import Icon from '../../components/common/Icon';
import { HOSTEL_STATUS, OwnerLink, saCrumbs } from './saCommon';

export default function SaHostelsPage() {
  const { data, loading, error, reload } = useApi(() => adminApi.hostels(), []);
  const [q, setQ] = useState('');
  const [ownerId, setOwnerId] = useState('');
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(10);

  const owners = useMemo(() => {
    const m = new Map();
    (data || []).forEach((h) => m.set(h.ownerId, h.ownerName));
    return [...m.entries()].sort((a, b) => a[1].localeCompare(b[1]));
  }, [data]);

  const filtered = useMemo(() => {
    const term = q.trim().toLowerCase();
    return (data || []).filter((h) => (!ownerId || String(h.ownerId) === ownerId)
      && (!term || [h.name, h.city, h.state, h.ownerName].some((v) => (v || '').toLowerCase().includes(term))));
  }, [data, q, ownerId]);

  const totalPages = Math.max(1, Math.ceil(filtered.length / size));
  const current = Math.min(page, totalPages - 1);
  const rows = filtered.slice(current * size, current * size + size);

  const columns = [
    { key: 'name', label: 'Hostel', sortValue: (h) => h.name.toLowerCase(), render: (h) => <div><span className="fw-medium">{h.name}</span><div className="small text-muted-hh">{label(h.hostelType)}</div></div> },
    { key: 'owner', label: 'Owner', sortValue: (h) => h.ownerName.toLowerCase(), render: (h) => <OwnerLink id={h.ownerId}>{h.ownerName}</OwnerLink> },
    { key: 'city', label: 'Location', sortValue: (h) => h.city, hideOnMobile: true, render: (h) => `${h.city}, ${h.state}` },
    { key: 'rooms', label: 'Rooms', align: 'center', sortValue: (h) => h.rooms, render: (h) => h.rooms },
    { key: 'beds', label: 'Beds', align: 'center', sortValue: (h) => h.beds, render: (h) => h.beds },
    { key: 'residents', label: 'Residents', align: 'center', sortValue: (h) => h.residents, render: (h) => h.residents },
    { key: 'managers', label: 'Managers', align: 'center', sortValue: (h) => h.managers, hideOnMobile: true, render: (h) => h.managers },
    { key: 'createdAt', label: 'Created', sortValue: (h) => h.createdAt, hideOnMobile: true, render: (h) => date(h.createdAt) },
    { key: 'status', label: 'Status', align: 'center', sortValue: (h) => h.status, render: (h) => <StatusBadge map={HOSTEL_STATUS} value={h.status} /> },
  ];

  const toolbar = (
    <>
      <div className="hh-search-input" role="search">
        <Icon className="bi bi-search" aria-hidden="true" />
        <input type="search" className="form-control" placeholder="Search hostel, city or owner…" aria-label="Search hostels"
          value={q} onChange={(e) => { setQ(e.target.value); setPage(0); }} />
      </div>
      <select className="form-select w-auto" aria-label="Filter by owner" value={ownerId} onChange={(e) => { setOwnerId(e.target.value); setPage(0); }}>
        <option value="">All owners</option>
        {owners.map(([id, name]) => <option key={id} value={id}>{name}</option>)}
      </select>
    </>
  );

  return (
    <>
      <PageHeader title="All hostels" icon="bi-houses" subtitle="Every hostel on the platform (summary information only)"
        breadcrumb={saCrumbs({ label: 'All Hostels' })} />
      {error ? <ErrorState error={error} onRetry={reload} /> : (
        <DataTable tableId="sa-hostels" columns={columns} rows={rows} serial serialStart={current * size} toolbar={toolbar}
          empty={loading && !data ? <LoadingState /> : <EmptyState icon="bi-houses" title={q || ownerId ? 'No matching hostels' : 'No hostels yet'} />}
          footer={<Pagination page={current} totalPages={totalPages} totalElements={filtered.length} size={size} noun="hostels"
            onChange={setPage} onSizeChange={(s) => { setSize(s); setPage(0); }} />} />
      )}
    </>
  );
}
