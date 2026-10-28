import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { hostelApi, roomApi } from '../../services/endpoints';
import { AsyncContent, EmptyState } from '../common/States';
import RoomStatusLegend from './RoomStatusLegend';
import RoomDetailsPanel from './RoomDetailsPanel';
import FloorPlan from './FloorPlan';
import RoomTile from './RoomTile';
import StatusBadge from '../common/StatusBadge';
import DataTable, { RowAction, RowActions } from '../common/DataTable';
import { ROOM_STATUS } from '../../utils/status';
import { roomCategory } from './roomDisplay';
import { useAuth } from '../../context/AuthContext';
import { date } from '../../utils/format';
import Icon from '../common/Icon';

const ALL = 'ALL';

/**
 * Building panel for one hostel: floor list, building/list toggle, floor plan with interactive rooms,
 * "All floors" overview. Reports the visible rooms through onRooms(rooms, label) for side summaries.
 */
export default function BuildingView({ hostel, onRooms }) {
  const hostelId = hostel?.id;
  const [view, setView] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [buildingId, setBuildingId] = useState(null);
  const [floorId, setFloorId] = useState(null);
  const [allRooms, setAllRooms] = useState(null);
  const [mode, setMode] = useState('plan');
  const [openRoom, setOpenRoom] = useState(null);
  const { can } = useAuth();

  const load = async (b = buildingId, f = floorId) => {
    if (!hostelId) return;
    setLoading(true); setError(null);
    try {
      if (f === ALL) {
        const v = view && view.selectedBuildingId === b ? view : await hostelApi.buildingView(hostelId, b, null);
        const page = await roomApi.list({ hostelId, buildingId: v.selectedBuildingId, size: 200 });
        setView(v);
        setBuildingId(v.selectedBuildingId);
        setFloorId(ALL);
        setAllRooms(page.content);
      } else {
        const v = await hostelApi.buildingView(hostelId, b, f);
        setView(v);
        setBuildingId(v.selectedBuildingId);
        setFloorId(v.selectedFloorId);
        setAllRooms(null);
      }
    } catch (e) { setError(e); } finally { setLoading(false); }
  };

  useEffect(() => { setView(null); setBuildingId(null); setFloorId(null); load(null, null); }, [hostelId]); // eslint-disable-line react-hooks/exhaustive-deps

  const building = view?.buildings.find((b) => b.id === buildingId);
  const floors = useMemo(() => [...(building?.floors || [])].sort((a, b) => (b.sortOrder ?? 0) - (a.sortOrder ?? 0) || b.levelNumber - a.levelNumber), [building]);
  const floor = floors.find((f) => f.id === floorId);
  const rooms = floorId === ALL ? (allRooms || []) : (view?.rooms || []);
  const totalRooms = floors.reduce((s, f) => s + f.roomCount, 0);

  useEffect(() => {
    if (view) onRooms?.(rooms, floorId === ALL ? 'All Floors' : floor?.name, view.vacancyWarningDays);
  }, [view, allRooms, floorId]); // eslint-disable-line react-hooks/exhaustive-deps

  const byFloor = useMemo(() => {
    if (floorId !== ALL || !allRooms) return [];
    return floors.map((f) => ({ floor: f, rooms: allRooms.filter((r) => r.floorId === f.id) })).filter((g) => g.rooms.length);
  }, [floorId, allRooms, floors]);

  const residentNames = (r) => (r.beds || []).filter((b) => b.resident).map((b) => b.resident.name).join(', ');
  const listColumns = [
    { key: 'roomNumber', label: 'Room', required: true, sortValue: (r) => r.roomNumber, render: (r) => (
      <span className="hh-cell-primary">
        <span className="d-inline-block rounded me-2 align-middle" aria-hidden="true" style={{ width: 10, height: 10, background: `var(--hh-${{ occupied: 'occupied', partial: 'partial', vacant: 'vacant', soon: 'soon', maintenance: 'maint' }[roomCategory(r)]})` }} />
        {r.roomNumber}
      </span>
    ) },
    { key: 'floorName', label: 'Floor', hideOnMobile: true },
    { key: 'roomType', label: 'Type', hideOnMobile: true },
    { key: 'occupancy', label: 'Occupancy', sortValue: (r) => r.availableBeds, render: (r) => (
      <div>{r.occupiedBeds}/{r.totalBeds}<span className="hh-cell-sub">{r.availableBeds} free</span></div>
    ) },
    { key: 'status', label: 'Status', align: 'center', sortValue: (r) => r.status, render: (r) => (
      <span className="d-inline-flex flex-wrap gap-1 justify-content-center">
        <StatusBadge map={ROOM_STATUS} value={r.status} />{r.vacatingSoon && <StatusBadge map={ROOM_STATUS} value="VACATING_SOON" />}
      </span>
    ) },
    { key: 'residents', label: 'Residents', sortValue: residentNames, render: (r) => residentNames(r) || <span className="hh-dash">—</span> },
    { key: 'nextCheckoutDate', label: 'Next checkout', hideOnMobile: true, sortValue: (r) => r.nextCheckoutDate || '',
      render: (r) => (r.nextCheckoutDate ? date(r.nextCheckoutDate) : <span className="hh-dash">—</span>) },
    { key: 'actions', label: 'Actions', align: 'center', sortable: false, required: true, render: (r) => (
      <RowActions><RowAction variant="view" label={`Open room ${r.roomNumber}`} onClick={() => setOpenRoom(r.id)} /></RowActions>
    ) },
  ];

  if (!hostelId) return <div className="hh-card"><EmptyState icon="bi-building" title="Select a hostel" /></div>;

  return (
    <div className="hh-card h-100">
      <div className="hh-bp-head">
        <div className="hh-bp-title">
          <span className="hh-bp-logo"><Icon className="bi bi-buildings-fill" /></span>
          <div className="min-w-0">
            <div className="hh-bp-name text-truncate">{hostel.name}</div>
            <div className="small text-muted-hh"><Icon className="bi bi-geo-alt-fill text-primary-hh me-1" />{hostel.city}{building && view.buildings.length > 1 ? ` · ${building.name}` : ''}</div>
          </div>
        </div>
        <div className="d-flex gap-2 flex-wrap">
          <div className="hh-seg" role="group" aria-label="View mode">
            <button type="button" className={mode === 'plan' ? 'active' : ''} aria-pressed={mode === 'plan'} onClick={() => setMode('plan')}><Icon className="bi bi-building" />Building View</button>
            <button type="button" className={mode === 'list' ? 'active' : ''} aria-pressed={mode === 'list'} onClick={() => setMode('list')}><Icon className="bi bi-list-ul" />List View</button>
          </div>
          {view && view.buildings.length > 1 && (
            <select className="form-select w-auto" aria-label="Building" value={buildingId || ''} onChange={(e) => load(Number(e.target.value), null)}>
              {view.buildings.map((b) => <option key={b.id} value={b.id}>{b.name}</option>)}
            </select>
          )}
          <select className="form-select w-auto" aria-label="Floor" value={floorId || ''} onChange={(e) => load(buildingId, e.target.value === ALL ? ALL : Number(e.target.value))}>
            <option value={ALL}>All Floors</option>
            {floors.map((f) => <option key={f.id} value={f.id}>{f.name}</option>)}
          </select>
        </div>
      </div>
      <RoomStatusLegend warningDays={view?.vacancyWarningDays} />

      <AsyncContent loading={loading && !view} error={error} onRetry={() => load()}
        empty={view && view.buildings.length === 0}
        emptyProps={{ icon: 'bi-building-add', title: 'No buildings yet', message: 'Add buildings, floors and rooms to see the floor plan.',
          action: can('HOSTEL_MANAGE') && <Link className="btn btn-primary" to={`/hostels/${hostelId}`}>Set up building</Link> }}>
        {view && (
          <div className="hh-bp-body">
            <div className="hh-floor-list" role="tablist" aria-label="Floors">
              <button type="button" role="tab" aria-selected={floorId === ALL} className={`hh-floor-btn ${floorId === ALL ? 'active' : ''}`} onClick={() => load(buildingId, ALL)}>
                <strong>All Floors</strong><small>{totalRooms} Rooms</small>
              </button>
              {floors.map((f) => (
                <button key={f.id} type="button" role="tab" aria-selected={f.id === floorId} className={`hh-floor-btn ${f.id === floorId ? 'active' : ''}`} onClick={() => load(buildingId, f.id)}>
                  <strong>{f.name.replace(' Floor', '') === f.name ? f.name : `Floor ${f.levelNumber === 0 ? 'G' : f.levelNumber}`}</strong>
                  <small>{f.roomCount} Rooms · {f.occupiedBeds}/{f.totalBeds}</small>
                </button>
              ))}
            </div>
            <div className="min-w-0" style={{ opacity: loading ? 0.6 : 1, transition: 'opacity .15s' }}>
              {rooms.length === 0 ? (
                <EmptyState icon="bi-door-closed" title="No rooms on this floor" message={can('ROOM_MANAGE') ? 'Add rooms from Hostels → Set up.' : undefined} />
              ) : mode === 'list' ? (
                <DataTable tableId="building-rooms" columns={listColumns} rows={rooms} serial onRowClick={(r) => setOpenRoom(r.id)} />
              ) : floorId === ALL ? (
                <div className="d-flex flex-column gap-3">
                  {byFloor.map(({ floor: f, rooms: rs }) => (
                    <div key={f.id}>
                      <div className="hh-floor-strip-title"><span>{f.name}</span><span className="small text-muted-hh">{rs.length} rooms · {f.occupiedBeds}/{f.totalBeds} beds</span></div>
                      <div className="hh-room-grid">{rs.map((r) => <RoomTile key={r.id} room={r} onOpen={(x) => setOpenRoom(x.id)} />)}</div>
                    </div>
                  ))}
                </div>
              ) : (
                <FloorPlan rooms={rooms} floorName={floor?.name || ''} layout={floor} onOpen={(x) => setOpenRoom(x.id)} />
              )}
            </div>
          </div>
        )}
      </AsyncContent>
      <RoomDetailsPanel roomId={openRoom} onClose={() => setOpenRoom(null)} onChanged={() => load()} />
    </div>
  );
}
