import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import PageHeader from '../../components/common/PageHeader';
import ConfirmDialog from '../../components/common/ConfirmDialog';
import DataTable, { RowAction, RowActions } from '../../components/common/DataTable';
import { EmptyState, ErrorState, LoadingState } from '../../components/common/States';
import HostelFormModal from '../../components/hostels/HostelFormModal';
import { BuildingModal, FloorModal, RoomModal, ROOM_CONFIG_STATUS } from '../../components/hostels/StructureModals';
import RoomBedsModal from '../../components/hostels/RoomBedsModal';
import { useAuth } from '../../context/AuthContext';
import { useHostels } from '../../context/HostelContext';
import { useToast } from '../../context/ToastContext';
import { buildingApi, floorApi, hostelApi, roomApi } from '../../services/endpoints';
import { errorMessage } from '../../services/apiClient';
import { money, number, percent } from '../../utils/format';
import Icon from '../../components/common/Icon';
import { UsageChip, SubscriptionNotice, bedAddStatus, useSubscription } from '../../components/subscription/UsagePanel';

const ROOM_STATUS_META = {
  AVAILABLE: { cls: 'success', icon: 'bi-check-circle' },
  MAINTENANCE: { cls: 'maintenance', icon: 'bi-tools' },
  INACTIVE: { cls: 'secondary', icon: 'bi-slash-circle' },
};

function ConfigStatus({ value }) {
  const meta = ROOM_STATUS_META[value] || ROOM_STATUS_META.AVAILABLE;
  const text = ROOM_CONFIG_STATUS.find((s) => s.value === value)?.label || value;
  return <span className={`badge hh-badge hh-badge-${meta.cls}`}><Icon className={`bi ${meta.icon} me-1`} />{text.replace(' (hidden)', '')}</span>;
}

export default function HostelSetupPage() {
  const { id } = useParams();
  const hostelId = Number(id);
  const navigate = useNavigate();
  const { can } = useAuth();
  const { reload: reloadHostels, selectHostel } = useHostels();
  const { notify } = useToast();
  const canHostel = can('HOSTEL_MANAGE');
  const canRoom = can('ROOM_MANAGE');
  const { summary: sub, reload: reloadSub } = useSubscription();
  const bedStatus = bedAddStatus(sub, hostelId);
  const bedsUsed = sub?.usage.hostelBeds.find((h) => h.hostelId === Number(hostelId))?.beds ?? 0;

  const [hostel, setHostel] = useState(null);
  const [structure, setStructure] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [collapsed, setCollapsed] = useState({});
  // Dialog state: { type: 'hostel'|'building'|'floor'|'room', ...context }
  const [dialog, setDialog] = useState(null);
  const [confirm, setConfirm] = useState(null);
  const [bedsRoomId, setBedsRoomId] = useState(null);

  const load = useCallback(async (quiet = false) => {
    if (!quiet) setLoading(true);
    setError(null);
    try {
      const [h, s] = await Promise.all([hostelApi.get(hostelId), hostelApi.structure(hostelId)]);
      setHostel(h);
      setStructure(s);
      reloadSub();
    } catch (e) {
      setError(e);
    } finally {
      setLoading(false);
    }
  }, [hostelId, reloadSub]);

  useEffect(() => { load(); }, [load]);
  useEffect(() => { document.title = `${hostel ? hostel.name : 'Hostel'} setup · HostelHub`; }, [hostel]);

  const refresh = () => { load(true); reloadHostels(); };
  const closeDialog = () => setDialog(null);
  const saved = () => { setDialog(null); refresh(); };

  const allFloors = useMemo(() => (structure || []).flatMap((b) => b.floors.map((f) => ({ id: f.id, label: `${b.name} · ${f.name}` }))), [structure]);

  const doDelete = async () => {
    const { kind, item } = confirm;
    try {
      if (kind === 'building') await buildingApi.remove(item.id);
      else if (kind === 'floor') await floorApi.remove(item.id);
      else if (kind === 'room') await roomApi.remove(item.id);
      notify(`${kind === 'room' ? `Room ${item.roomNumber}` : item.name} removed.`);
      setConfirm(null);
      refresh();
    } catch (e) {
      notify(errorMessage(e), 'danger');
    }
  };

  const toggle = (key) => setCollapsed((c) => ({ ...c, [key]: !c[key] }));

  if (loading && !structure) return <LoadingState label="Loading property structure…" />;
  if (error && !structure) {
    return (
      <>
        <PageHeader title="Hostel setup" icon="bi-diagram-3" actions={<Link to="/hostels" className="btn btn-light"><Icon className="bi bi-arrow-left me-1" />All hostels</Link>} />
        <ErrorState error={error} onRetry={() => load()} />
      </>
    );
  }

  const s = hostel?.stats || {};
  const roomColumns = (floor) => [
    { key: 'roomNumber', label: 'Room', required: true, sortValue: (r) => r.roomNumber, render: (r) => <span className="hh-cell-primary">{r.roomNumber}</span> },
    { key: 'roomType', label: 'Type' },
    { key: 'totalBeds', label: 'Beds', align: 'center', sortValue: (r) => r.totalBeds, render: (r) => number(r.totalBeds) },
    { key: 'rent', label: 'Rent / bed', align: 'end', sortValue: (r) => Number(r.monthlyRentPerBed), render: (r) => money(r.monthlyRentPerBed) },
    { key: 'deposit', label: 'Deposit', align: 'end', hideOnMobile: true, sortValue: (r) => Number(r.securityDeposit), render: (r) => money(r.securityDeposit) },
    { key: 'amenities', label: 'Amenities', hideOnMobile: true, sortValue: (r) => r.amenities || '',
      render: (r) => (r.amenities ? <span className="text-muted-hh">{r.amenities}</span> : <span className="hh-dash">—</span>) },
    { key: 'status', label: 'Status', align: 'center', sortValue: (r) => r.status, render: (r) => <ConfigStatus value={r.status} /> },
    {
      key: 'actions', label: 'Actions', align: 'center', sortable: false, required: true,
      render: (r) => (
        <RowActions>
          <RowAction variant="view" icon="bi-grid-3x2-gap" disabled={r.status === 'INACTIVE'}
            label={r.status === 'INACTIVE' ? 'Reactivate the room to manage its beds' : 'Manage beds'} onClick={() => setBedsRoomId(r.id)} />
          {canRoom && <RowAction variant="edit" label={`Edit room ${r.roomNumber}`} onClick={() => setDialog({ type: 'room', floorId: floor.id, room: r })} />}
          {canRoom && <RowAction variant="delete" label={`Delete room ${r.roomNumber}`} onClick={() => setConfirm({ kind: 'room', item: r })} />}
        </RowActions>
      ),
    },
  ];

  return (
    <>
      <PageHeader title={hostel?.name || 'Hostel setup'} icon="bi-diagram-3"
        subtitle={hostel && `${hostel.address}, ${hostel.city} · ${number(s.totalRooms)} rooms · ${number(s.totalBeds)} beds · ${percent(s.occupancyPercent)} occupied`}
        actions={<>
          <Link to="/hostels" className="btn btn-light"><Icon className="bi bi-arrow-left me-1" />All hostels</Link>
          {canHostel && <button type="button" className="btn btn-light" onClick={() => setDialog({ type: 'hostel' })}><Icon className="bi bi-pencil me-1" />Edit hostel</button>}
          {canHostel && <button type="button" className="btn btn-primary" onClick={() => setDialog({ type: 'building' })}><Icon className="bi bi-plus-lg me-1" />Add building</button>}
        </>} />
      {sub && (
        <div className="d-flex flex-wrap align-items-center gap-2 mb-3">
          <UsageChip label="Beds in this hostel" used={bedsUsed} limit={sub.limits.maxBedsPerHostel} warnPercent={sub.warningPercent} />
          {bedStatus.blocked && <span className="small text-muted-hh">{bedStatus.message}</span>}
        </div>
      )}
      <SubscriptionNotice summary={sub} only={(w) => /^Beds in|subscription/i.test(w)} />

      {structure.length === 0 ? (
        <div className="hh-card">
          <EmptyState icon="bi-building-add" title="No buildings yet"
            message="Start by adding a building. You can create its floors at the same time, then add rooms and beds."
            action={canHostel && <button type="button" className="btn btn-primary" onClick={() => setDialog({ type: 'building' })}><Icon className="bi bi-plus-lg me-1" />Add building</button>} />
        </div>
      ) : structure.map((b) => {
        const bKey = `b${b.id}`;
        const roomCount = b.floors.reduce((n, f) => n + f.rooms.length, 0);
        const bedCount = b.floors.reduce((n, f) => n + f.rooms.reduce((m, r) => m + r.totalBeds, 0), 0);
        return (
          <section key={b.id} className="hh-card mb-3" aria-label={b.name}>
            <div className="hh-card-header">
              <button type="button" className="btn btn-link p-0 text-reset text-decoration-none text-start min-w-0" onClick={() => toggle(bKey)} aria-expanded={!collapsed[bKey]}>
                <h2 className="hh-card-title">
                  <Icon className={`bi ${collapsed[bKey] ? 'bi-chevron-right' : 'bi-chevron-down'} me-2`} aria-hidden="true" />
                  <Icon className="bi bi-building me-2 text-primary-hh" aria-hidden="true" />{b.name}
                  {b.status === 'INACTIVE' && <span className="badge hh-badge hh-badge-secondary ms-2"><Icon className="bi bi-pause-circle me-1" />Inactive</span>}
                </h2>
                <div className="small text-muted-hh">{b.floors.length} floor{b.floors.length === 1 ? '' : 's'} · {roomCount} rooms · {bedCount} beds{b.description ? ` · ${b.description}` : ''}</div>
              </button>
              <div className="d-flex flex-wrap gap-1">
                {canRoom && <button type="button" className="btn btn-sm btn-soft"
                  onClick={() => setDialog({ type: 'floor', building: b, nextLevel: b.floors.length ? Math.max(...b.floors.map((f) => f.levelNumber)) + 1 : 0 })}>
                  <Icon className="bi bi-plus-lg me-1" />Floor</button>}
                {canHostel && <button type="button" className="btn btn-sm btn-light" aria-label={`Edit ${b.name}`} onClick={() => setDialog({ type: 'building', building: b })}><Icon className="bi bi-pencil" /></button>}
                {canHostel && <button type="button" className="btn btn-sm btn-light text-danger" aria-label={`Delete ${b.name}`} onClick={() => setConfirm({ kind: 'building', item: b })}><Icon className="bi bi-trash" /></button>}
              </div>
            </div>
            {!collapsed[bKey] && (b.floors.length === 0 ? (
              <EmptyState icon="bi-layers" title="No floors in this building"
                action={canRoom && <button type="button" className="btn btn-sm btn-primary" onClick={() => setDialog({ type: 'floor', building: b, nextLevel: 0 })}>Add floor</button>} />
            ) : b.floors.map((f) => (
              <div key={f.id} className="border rounded-3 p-2 p-md-3 mb-2" style={{ borderColor: 'var(--hh-border)' }}>
                <div className="d-flex justify-content-between align-items-center flex-wrap gap-2 mb-2">
                  <div className="min-w-0">
                    <div className="fw-bold"><Icon className="bi bi-layers me-2 text-muted-hh" aria-hidden="true" />{f.name}
                      <span className="small text-muted-hh fw-normal ms-2">Level {f.levelNumber} · {f.rooms.length} room{f.rooms.length === 1 ? '' : 's'}</span></div>
                    {f.description && <div className="small text-muted-hh">{f.description}</div>}
                  </div>
                  {canRoom && (
                    <div className="d-flex gap-1">
                      <button type="button" className="btn btn-sm btn-primary" onClick={() => setDialog({ type: 'room', floorId: f.id })}><Icon className="bi bi-plus-lg me-1" />Room</button>
                      <button type="button" className="btn btn-sm btn-light" aria-label={`Edit ${f.name}`} onClick={() => setDialog({ type: 'floor', building: b, floor: f })}><Icon className="bi bi-pencil" /></button>
                      <button type="button" className="btn btn-sm btn-light text-danger" aria-label={`Delete ${f.name}`} onClick={() => setConfirm({ kind: 'floor', item: f })}><Icon className="bi bi-trash" /></button>
                    </div>
                  )}
                </div>
                <DataTable columns={roomColumns(f)} rows={f.rooms} serial
                  empty={<div className="small text-muted-hh py-2"><Icon className="bi bi-door-closed me-1" />No rooms on this floor yet.</div>} />
              </div>
            )))}
          </section>
        );
      })}

      <HostelFormModal show={dialog?.type === 'hostel'} hostel={hostel} onClose={closeDialog} onSaved={saved} />
      <BuildingModal show={dialog?.type === 'building'} hostelId={hostelId} building={dialog?.building || null} onClose={closeDialog} onSaved={saved} />
      <FloorModal show={dialog?.type === 'floor'} buildingId={dialog?.building?.id} buildingName={dialog?.building?.name}
        floor={dialog?.floor || null} nextLevel={dialog?.nextLevel ?? 0} onClose={closeDialog} onSaved={saved} />
      <RoomModal show={dialog?.type === 'room'} floorId={dialog?.floorId} floors={allFloors} room={dialog?.room || null} onClose={closeDialog} onSaved={saved} />
      {bedsRoomId && <RoomBedsModal roomId={bedsRoomId} canManage={canRoom} onClose={() => setBedsRoomId(null)} onChanged={refresh} />}
      <ConfirmDialog show={!!confirm} variant="danger" confirmLabel="Delete"
        title={confirm && `Delete ${confirm.kind}`}
        message={confirm && (confirm.kind === 'room'
          ? `Delete room ${confirm.item.roomNumber} and all its beds? Rooms with residents or live reservations cannot be deleted.`
          : `Delete ${confirm.item.name}? Everything inside it (${confirm.kind === 'building' ? 'floors, rooms and beds' : 'rooms and beds'}) will be removed. This is blocked while residents are allocated.`)}
        onConfirm={doDelete} onClose={() => setConfirm(null)} />
    </>
  );
}
