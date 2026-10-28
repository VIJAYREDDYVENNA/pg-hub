import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import PageHeader from '../components/common/PageHeader';
import HostelFilter from '../components/common/HostelFilter';
import DataTable, { RowAction, RowActions } from '../components/common/DataTable';
import Pagination from '../components/common/Pagination';
import StatusBadge from '../components/common/StatusBadge';
import { EmptyState, ErrorState, LoadingState } from '../components/common/States';
import RoomDetailsPanel from '../components/building/RoomDetailsPanel';
import useApi from '../hooks/useApi';
import useDebounce from '../hooks/useDebounce';
import { useHostels } from '../context/HostelContext';
import { roomApi } from '../services/endpoints';
import { ROOM_STATUS } from '../utils/status';
import { date, money } from '../utils/format';
import Icon from '../components/common/Icon';
import FloorDesigner from '../components/rooms/FloorDesigner';

const STATUS_FILTERS = [
  { value: 'ALL', label: 'All rooms' },
  { value: 'HAS_AVAILABLE', label: 'Has free beds' },
  { value: 'FULLY_OCCUPIED', label: 'Fully occupied' },
  { value: 'PARTIALLY_OCCUPIED', label: 'Partially occupied' },
  { value: 'VACANT', label: 'Vacant' },
  { value: 'VACATING_SOON', label: 'Vacating soon' },
  { value: 'UNDER_MAINTENANCE', label: 'Under maintenance' },
];
const PAGE_SIZE = 10;

export default function RoomsPage() {
  const [params, setParams] = useSearchParams();
  const { selectedHostelId, selectHostel } = useHostels();
  const status = params.get('status') || 'ALL';
  const page = Number(params.get('page') || 0);
  const size = Number(params.get('size') || PAGE_SIZE);
  const roomId = params.get('roomId') ? Number(params.get('roomId')) : null;
  const urlHostel = params.get('hostelId');
  const hostelId = urlHostel ? Number(urlHostel) : selectedHostelId;
  const [q, setQ] = useState(params.get('q') || '');
  const debouncedQ = useDebounce(q.trim(), 350);
  const tab = params.get('tab') === 'designer' ? 'designer' : 'rooms';
  const setTab = (t2) => { const n = new URLSearchParams(params); if (t2 === 'rooms') n.delete('tab'); else n.set('tab', t2); setParams(n, { replace: true }); };

  useEffect(() => { document.title = 'Rooms · HostelHub'; }, []);

  const update = (changes, resetPage = true) => {
    const next = new URLSearchParams(params);
    Object.entries(changes).forEach(([k, v]) => {
      if (v === null || v === undefined || v === '' || (k === 'status' && v === 'ALL')) next.delete(k); else next.set(k, String(v));
    });
    if (resetPage && !('page' in changes)) next.delete('page');
    setParams(next, { replace: true });
  };

  // Push the debounced search text into the URL (resets to page 1).
  useEffect(() => {
    if ((params.get('q') || '') !== debouncedQ) update({ q: debouncedQ });
  }, [debouncedQ]); // eslint-disable-line react-hooks/exhaustive-deps

  const { data, loading, error, reload } = useApi(
    () => roomApi.list({ hostelId, status, q: debouncedQ || undefined, page, size }),
    [hostelId, status, debouncedQ, page, size],
  );

  const changeHostel = (id) => {
    selectHostel(id);
    update({ hostelId: id || null });
  };

  const openRoom = (r) => update({ roomId: r.id }, false);

  const columns = [
    { key: 'room', label: 'Room', required: true, sortValue: (r) => r.roomNumber, render: (r) => (
      <div>
        <span className="hh-cell-primary">Room {r.roomNumber}</span>
        {r.roomType && <span className="hh-cell-sub">{r.roomType}</span>}
      </div>
    ) },
    { key: 'location', label: 'Location', sortValue: (r) => `${r.hostelName || ''} ${r.buildingName || ''} ${r.floorName || ''}`, render: (r) => (
      <div>
        {!hostelId && <span className="hh-cell-primary">{r.hostelName}</span>}
        <span className="hh-cell-sub">{r.buildingName} · {r.floorName}</span>
      </div>
    ) },
    { key: 'beds', label: 'Beds', sortValue: (r) => r.availableBeds, render: (r) => (
      <div>
        <span><strong>{r.occupiedBeds}</strong>/{r.totalBeds} occupied</span>
        <span className="hh-cell-sub">
          <span className={r.availableBeds > 0 ? 'text-danger fw-semibold' : ''}>{r.availableBeds} free</span>
          {r.reservedBeds > 0 && <> · {r.reservedBeds} reserved</>}
          {r.maintenanceBeds > 0 && <> · {r.maintenanceBeds} maint.</>}
        </span>
      </div>
    ) },
    { key: 'status', label: 'Status', align: 'center', sortValue: (r) => r.status, render: (r) => (
      <div className="d-flex flex-wrap gap-1 justify-content-end justify-content-md-center">
        <StatusBadge map={ROOM_STATUS} value={r.status} />
        {r.vacatingSoon && <StatusBadge map={ROOM_STATUS} value="VACATING_SOON" />}
      </div>
    ) },
    { key: 'rent', label: 'Rent / bed', align: 'end', hideOnMobile: true, sortValue: (r) => Number(r.monthlyRentPerBed), render: (r) => money(r.monthlyRentPerBed) },
    { key: 'next', label: 'Next checkout', hideOnMobile: true, sortValue: (r) => r.nextCheckoutDate || '',
      render: (r) => (r.nextCheckoutDate ? date(r.nextCheckoutDate) : <span className="hh-dash">—</span>) },
    { key: 'actions', label: 'Actions', align: 'center', sortable: false, required: true, hideOnMobile: true, render: (r) => (
      <RowActions>
        <RowAction variant="view" label={`Open room ${r.roomNumber}`} onClick={() => openRoom(r)} />
      </RowActions>
    ) },
  ];

  const rows = data?.content || [];
  const statusLabel = STATUS_FILTERS.find((s) => s.value === status)?.label;
  const filtered = status !== 'ALL' || debouncedQ;

  const toolbar = (
    <>
      <div className="hh-search-input" role="search">
        <Icon className="bi bi-search" aria-hidden="true" />
        <input type="search" className="form-control" placeholder="Room number or resident name" aria-label="Search rooms"
          value={q} onChange={(e) => setQ(e.target.value)} />
      </div>
      <div style={{ minWidth: 170 }}><HostelFilter value={hostelId} onChange={changeHostel} id="rooms-hostel" /></div>
      <select className="form-select w-auto" aria-label="Room status" value={status} onChange={(e) => update({ status: e.target.value })}>
        {STATUS_FILTERS.map((s) => <option key={s.value} value={s.value}>{s.label}</option>)}
      </select>
    </>
  );

  return (
    <>
      <PageHeader title="Rooms & Floors" icon="bi-door-closed" subtitle="Live occupancy, and a designer to customise floors, rooms and floor plans" />
      <ul className="nav nav-tabs mb-3" role="tablist">
        <li className="nav-item"><button type="button" role="tab" aria-selected={tab === 'rooms'} className={`nav-link ${tab === 'rooms' ? 'active' : ''}`} onClick={() => setTab('rooms')}><Icon className="bi bi-door-closed me-1" />Rooms</button></li>
        <li className="nav-item"><button type="button" role="tab" aria-selected={tab === 'designer'} className={`nav-link ${tab === 'designer' ? 'active' : ''}`} onClick={() => setTab('designer')}><Icon className="bi bi-pencil-square me-1" />Customise floors &amp; rooms</button></li>
      </ul>
      {tab === 'designer' ? <FloorDesigner /> : (<>

      {error ? <ErrorState error={error} onRetry={reload} /> : (
        <div style={{ opacity: loading && data ? 0.6 : 1, transition: 'opacity .15s' }}>
          <DataTable tableId="rooms" columns={columns} rows={rows} serial serialStart={(data?.page || 0) * (data?.size || size)}
            onRowClick={openRoom} toolbar={toolbar}
            empty={loading && !data ? <LoadingState /> : (
              <EmptyState icon="bi-door-closed" title="No rooms found"
                message={filtered ? `No rooms match "${statusLabel}"${debouncedQ ? ` and "${debouncedQ}"` : ''}.` : 'Add buildings, floors and rooms from the Hostels page.'} />
            )}
            footer={data && <Pagination page={data.page} totalPages={data.totalPages} totalElements={data.totalElements} size={data.size}
              noun="rooms" onChange={(p) => update({ page: p || null }, false)} onSizeChange={(s) => update({ size: s === PAGE_SIZE ? null : s })} />} />
        </div>
      )}

      <RoomDetailsPanel roomId={roomId} onClose={() => update({ roomId: null }, false)} onChanged={reload} />
      </>)}
    </>
  );
}
