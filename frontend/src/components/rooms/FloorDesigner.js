import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { floorApi, hostelApi, roomApi } from '../../services/endpoints';
import { errorMessage } from '../../services/apiClient';
import { useAuth } from '../../context/AuthContext';
import { useHostels } from '../../context/HostelContext';
import { useToast } from '../../context/ToastContext';
import HostelFilter from '../common/HostelFilter';
import ConfirmDialog from '../common/ConfirmDialog';
import Icon from '../common/Icon';
import { AsyncContent, EmptyState, LoadingState } from '../common/States';
import { BuildingModal, FloorModal, RoomModal } from '../hostels/StructureModals';
import RoomBedsModal from '../hostels/RoomBedsModal';
import FloorPlan, { splitRows } from '../building/FloorPlan';
import { money } from '../../utils/format';

const LAYOUTS = [
  { value: 'CORRIDOR', label: 'Corridor (2 sides)', icon: 'bi-layout-split' },
  { value: 'SINGLE_ROW', label: 'Single row', icon: 'bi-layout-three-columns' },
  { value: 'GRID', label: 'Open grid', icon: 'bi-grid-3x3-gap' },
];

/** Converts the editable lanes back to placements: TOP/BOTTOM rows with a running order. */
function placements(lanes, planLayout) {
  if (planLayout !== 'CORRIDOR') return lanes.top.map((r, i) => ({ roomId: r.id, planRow: 'AUTO', sortOrder: i }));
  return [
    ...lanes.top.map((r, i) => ({ roomId: r.id, planRow: 'TOP', sortOrder: i * 2 })),
    ...lanes.bottom.map((r, i) => ({ roomId: r.id, planRow: 'BOTTOM', sortOrder: i * 2 + 1 })),
  ];
}

/**
 * Floors & rooms designer: add / rename / reorder / delete floors, add / edit / delete rooms, and arrange each
 * floor's plan (layout style, lift & stairs, which side of the corridor each room is on and in what order).
 */
export default function FloorDesigner() {
  const { can } = useAuth();
  const { notify } = useToast();
  const { hostels, selectedHostelId } = useHostels();
  const editable = can('ROOM_MANAGE');
  const [hostelId, setHostelId] = useState(selectedHostelId || hostels[0]?.id || null);
  const [structure, setStructure] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [buildingId, setBuildingId] = useState(null);
  const [floorId, setFloorId] = useState(null);
  const [view, setView] = useState(null);
  const [draft, setDraft] = useState(null);
  const [dirty, setDirty] = useState(false);
  const [saving, setSaving] = useState(false);
  const [modal, setModal] = useState(null);
  const [drag, setDrag] = useState(null);

  useEffect(() => { if (!hostelId && hostels[0]) setHostelId(hostels[0].id); }, [hostels, hostelId]);

  const loadStructure = async (keepFloor = floorId) => {
    if (!hostelId) return;
    setLoading(true); setError(null);
    try {
      const s = await hostelApi.structure(hostelId);
      setStructure(s);
      const b = s.find((x) => x.id === buildingId) || s[0];
      setBuildingId(b?.id || null);
      const floorsSorted = [...(b?.floors || [])].sort((x, y) => y.sortOrder - x.sortOrder || y.levelNumber - x.levelNumber);
      const f = floorsSorted.find((x) => x.id === keepFloor) || floorsSorted[floorsSorted.length - 1];
      setFloorId(f?.id || null);
    } catch (e) { setError(e); } finally { setLoading(false); }
  };
  useEffect(() => { setBuildingId(null); setFloorId(null); setStructure(null); loadStructure(null); }, [hostelId]); // eslint-disable-line react-hooks/exhaustive-deps

  const building = structure?.find((b) => b.id === buildingId);
  const floors = useMemo(() => [...(building?.floors || [])]
    .sort((x, y) => y.sortOrder - x.sortOrder || y.levelNumber - x.levelNumber), [building]);
  const floor = floors.find((f) => f.id === floorId);

  // Live room views (real occupancy colours) for the selected floor, used for the editor and preview.
  const loadFloor = async () => {
    if (!hostelId || !buildingId || !floorId) { setView(null); setDraft(null); return; }
    try {
      const v = await hostelApi.buildingView(hostelId, buildingId, floorId);
      setView(v);
      const fl = floors.find((f) => f.id === floorId);
      const layout = fl?.planLayout || 'CORRIDOR';
      setDraft({ planLayout: layout, hasLift: fl?.hasLift ?? true, hasStairs: fl?.hasStairs ?? true, ...splitRows(v.rooms, layout) });
      setDirty(false);
    } catch (e) { notify(errorMessage(e), 'danger'); }
  };
  useEffect(() => { loadFloor(); }, [floorId, buildingId, structure]); // eslint-disable-line react-hooks/exhaustive-deps

  const change = (patch) => { setDraft((d) => ({ ...d, ...patch })); setDirty(true); };
  const setLayout = (planLayout) => {
    const all = [...draft.top, ...draft.bottom];
    change(planLayout === 'CORRIDOR' ? { planLayout, ...splitRows(all.map((r) => ({ ...r, planRow: 'AUTO' })), 'CORRIDOR') }
      : { planLayout, top: all, bottom: [] });
  };
  const move = (lane, index, delta) => {
    const list = [...draft[lane]];
    const j = index + delta;
    if (j < 0 || j >= list.length) return;
    [list[index], list[j]] = [list[j], list[index]];
    change({ [lane]: list });
  };
  const switchSide = (lane, index) => {
    const from = [...draft[lane]];
    const [room] = from.splice(index, 1);
    const other = lane === 'top' ? 'bottom' : 'top';
    change({ [lane]: from, [other]: [...draft[other], room] });
  };
  const dropOn = (lane, index) => {
    if (!drag) return;
    const next = { top: [...draft.top], bottom: [...draft.bottom] };
    const [room] = next[drag.lane].splice(drag.index, 1);
    let at = index === null ? next[lane].length : index;
    if (drag.lane === lane && drag.index < at) at -= 1;
    next[lane].splice(at, 0, room);
    setDrag(null);
    change(next);
  };

  const saveLayout = async () => {
    setSaving(true);
    try {
      await floorApi.saveLayout(floorId, { planLayout: draft.planLayout, hasLift: draft.hasLift, hasStairs: draft.hasStairs,
        rooms: placements(draft, draft.planLayout) });
      notify('Floor layout saved. The building view now uses this arrangement.');
      await loadStructure(floorId);
    } catch (e) { notify(errorMessage(e), 'danger'); } finally { setSaving(false); }
  };

  const moveFloor = async (index, delta) => {
    const ids = floors.map((f) => f.id);
    const j = index + delta;
    if (j < 0 || j >= ids.length) return;
    [ids[index], ids[j]] = [ids[j], ids[index]];
    try { await floorApi.reorder(buildingId, ids); await loadStructure(floorId); } catch (e) { notify(errorMessage(e), 'danger'); }
  };

  const removeFloor = async () => {
    try { await floorApi.remove(modal.floor.id); notify('Floor removed.'); setModal(null); await loadStructure(null); } catch (e) { notify(errorMessage(e), 'danger'); }
  };
  const removeRoom = async () => {
    try { await roomApi.remove(modal.room.id); notify(`Room ${modal.room.roomNumber} removed.`); setModal(null); await loadStructure(floorId); } catch (e) { notify(errorMessage(e), 'danger'); }
  };

  const structureRoom = (id) => floor?.rooms.find((r) => r.id === id);
  const floorOptions = floors.map((f) => ({ id: f.id, label: f.name }));
  const nextLevel = floors.length ? Math.max(...floors.map((f) => f.levelNumber)) + 1 : 0;
  const previewRooms = draft ? placements(draft, draft.planLayout).map((p) => {
    const r = view?.rooms.find((x) => x.id === p.roomId);
    return r ? { ...r, planRow: p.planRow, sortOrder: p.sortOrder } : null;
  }).filter(Boolean) : [];

  const RoomChip = ({ room, lane, index, count }) => {
    const s = structureRoom(room.id);
    return (
      <div className={`hh-design-chip ${drag?.id === room.id ? 'dragging' : ''}`} draggable={editable}
        onDragStart={() => setDrag({ lane, index, id: room.id })} onDragEnd={() => setDrag(null)}
        onDragOver={(e) => e.preventDefault()} onDrop={(e) => { e.preventDefault(); e.stopPropagation(); dropOn(lane, index); }}>
        {editable && <Icon className="bi bi-grip-vertical hh-design-grip" />}
        <div className="min-w-0 flex-grow-1">
          <div className="fw-semibold">Room {room.roomNumber}</div>
          <div className="small text-muted-hh text-truncate">{room.roomType} · {room.totalBeds} bed{room.totalBeds === 1 ? '' : 's'} · {money(room.monthlyRentPerBed)}</div>
        </div>
        {editable && (
          <div className="hh-design-chip-actions">
            <button type="button" className="hh-row-action more" title="Move earlier" aria-label="Move earlier" disabled={index === 0} onClick={() => move(lane, index, -1)}><Icon className="bi bi-chevron-left" /></button>
            <button type="button" className="hh-row-action more" title="Move later" aria-label="Move later" disabled={index === count - 1} onClick={() => move(lane, index, 1)}><Icon className="bi bi-chevron-right" /></button>
            {draft.planLayout === 'CORRIDOR' && <button type="button" className="hh-row-action remind" title="Move to the other side" aria-label="Move to the other side" onClick={() => switchSide(lane, index)}><Icon className="bi bi-arrow-down-up" /></button>}
            <button type="button" className="hh-row-action view" title="Beds" aria-label={`Manage beds of room ${room.roomNumber}`} onClick={() => setModal({ type: 'beds', roomId: room.id })}><Icon className="bi bi-grid-3x2-gap" /></button>
            <button type="button" className="hh-row-action edit" title="Edit room" aria-label={`Edit room ${room.roomNumber}`} onClick={() => setModal({ type: 'room', room: s })}><Icon className="bi bi-pencil-square" /></button>
            <button type="button" className="hh-row-action delete" title="Delete room" aria-label={`Delete room ${room.roomNumber}`} onClick={() => setModal({ type: 'deleteRoom', room: s })}><Icon className="bi bi-trash3" /></button>
          </div>
        )}
      </div>
    );
  };

  const Lane = ({ lane, title }) => (
    <div className={`hh-design-lane ${drag ? 'droppable' : ''}`} onDragOver={(e) => e.preventDefault()} onDrop={(e) => { e.preventDefault(); dropOn(lane, null); }}>
      <div className="hh-design-lane-title">{title} <span className="text-muted-hh">({draft[lane].length})</span></div>
      {draft[lane].length === 0 ? <div className="hh-design-empty">{editable ? 'Drag rooms here' : 'No rooms'}</div>
        : draft[lane].map((r, i) => <RoomChip key={r.id} room={r} lane={lane} index={i} count={draft[lane].length} />)}
    </div>
  );

  if (!hostels.length) return <EmptyState icon="bi-houses" title="No hostels yet" action={<Link className="btn btn-primary" to="/hostels?new=1">Add hostel</Link>} />;

  return (
    <div className="hh-designer">
      <div className="hh-table-card mb-3">
        <div className="hh-table-toolbar">
          <div style={{ minWidth: 200 }}><HostelFilter allowAll={false} value={hostelId} onChange={(id) => setHostelId(id)} id="designer-hostel" /></div>
          {structure && structure.length > 0 && (
            <select className="form-select w-auto" aria-label="Building" value={buildingId || ''} onChange={(e) => { setBuildingId(Number(e.target.value)); setFloorId(null); setTimeout(() => loadStructure(null), 0); }}>
              {structure.map((b) => <option key={b.id} value={b.id}>{b.name}</option>)}
            </select>
          )}
          {editable && can('HOSTEL_MANAGE') && <button type="button" className="btn btn-light btn-sm" onClick={() => setModal({ type: 'building' })}><Icon className="bi bi-building-add me-1" />Add building</button>}
          {!editable && <span className="small text-muted-hh ms-auto"><Icon className="bi bi-lock me-1" />View only — you need room management permission to make changes.</span>}
        </div>
      </div>

      <AsyncContent loading={loading && !structure} error={error} onRetry={() => loadStructure()}
        empty={structure && structure.length === 0}
        emptyProps={{ icon: 'bi-building-add', title: 'No buildings in this hostel', message: 'Add a building to start designing floors.',
          action: editable && can('HOSTEL_MANAGE') && <button type="button" className="btn btn-primary" onClick={() => setModal({ type: 'building' })}>Add building</button> }}>
        {building && (
          <div className="row g-3">
            <div className="col-lg-4 col-xl-3">
              <div className="hh-card">
                <div className="hh-card-header">
                  <h3 className="hh-card-title">Floors <span className="text-muted-hh fw-normal">({floors.length})</span></h3>
                  {editable && <button type="button" className="btn btn-sm btn-primary" onClick={() => setModal({ type: 'floor' })}><Icon className="bi bi-plus-lg me-1" />Add floor</button>}
                </div>
                <div className="small text-muted-hh mb-2">Top of the list = top of the building.</div>
                {floors.length === 0 ? <div className="hh-design-empty">No floors yet</div> : floors.map((f, i) => (
                  <div key={f.id} className={`hh-design-floor ${f.id === floorId ? 'active' : ''}`}>
                    <button type="button" className="hh-design-floor-main" onClick={() => setFloorId(f.id)} aria-pressed={f.id === floorId}>
                      <strong>{f.name}</strong><small>Level {f.levelNumber} · {f.rooms.length} room{f.rooms.length === 1 ? '' : 's'}</small>
                    </button>
                    {editable && (
                      <div className="hh-row-actions">
                        <button type="button" className="hh-row-action more" aria-label={`Move ${f.name} up`} title="Move up" disabled={i === 0} onClick={() => moveFloor(i, -1)}><Icon className="bi bi-arrow-up" /></button>
                        <button type="button" className="hh-row-action more" aria-label={`Move ${f.name} down`} title="Move down" disabled={i === floors.length - 1} onClick={() => moveFloor(i, 1)}><Icon className="bi bi-arrow-down" /></button>
                        <button type="button" className="hh-row-action edit" aria-label={`Edit ${f.name}`} title="Rename / edit" onClick={() => setModal({ type: 'floor', floor: f })}><Icon className="bi bi-pencil-square" /></button>
                        <button type="button" className="hh-row-action delete" aria-label={`Delete ${f.name}`} title="Delete floor" onClick={() => setModal({ type: 'deleteFloor', floor: f })}><Icon className="bi bi-trash3" /></button>
                      </div>
                    )}
                  </div>
                ))}
              </div>
            </div>

            <div className="col-lg-8 col-xl-9 min-w-0">
              {!floor ? <div className="hh-card"><EmptyState icon="bi-layers" title="Add a floor to start" /></div> : !draft ? <LoadingState /> : (
                <>
                  <div className="hh-card mb-3">
                    <div className="hh-card-header">
                      <h3 className="hh-card-title">{floor.name} — layout &amp; rooms</h3>
                      {editable && (
                        <div className="d-flex gap-2 flex-wrap">
                          <button type="button" className="btn btn-sm btn-light" onClick={() => setModal({ type: 'room', room: null })}><Icon className="bi bi-plus-lg me-1" />Add room</button>
                          <button type="button" className="btn btn-sm btn-light" disabled={!dirty || saving} onClick={loadFloor}>Reset</button>
                          <button type="button" className="btn btn-sm btn-primary" disabled={!dirty || saving} onClick={saveLayout}>
                            {saving ? <span className="spinner-border spinner-border-sm me-1" /> : <Icon className="bi bi-check2 me-1" />}Save layout
                          </button>
                        </div>
                      )}
                    </div>
                    <div className="d-flex flex-wrap gap-3 align-items-center mb-3">
                      <div className="hh-seg" role="group" aria-label="Plan style">
                        {LAYOUTS.map((l) => (
                          <button key={l.value} type="button" disabled={!editable} className={draft.planLayout === l.value ? 'active' : ''} aria-pressed={draft.planLayout === l.value} onClick={() => setLayout(l.value)}>
                            <Icon className={`bi ${l.icon}`} />{l.label}
                          </button>
                        ))}
                      </div>
                      <div className="form-check form-switch m-0">
                        <input className="form-check-input" type="checkbox" id="has-lift" checked={draft.hasLift} disabled={!editable} onChange={(e) => change({ hasLift: e.target.checked })} />
                        <label className="form-check-label small" htmlFor="has-lift">Show lift</label>
                      </div>
                      <div className="form-check form-switch m-0">
                        <input className="form-check-input" type="checkbox" id="has-stairs" checked={draft.hasStairs} disabled={!editable} onChange={(e) => change({ hasStairs: e.target.checked })} />
                        <label className="form-check-label small" htmlFor="has-stairs">Show stairs</label>
                      </div>
                      {dirty && <span className="hh-badge hh-badge-warning"><Icon className="bi bi-exclamation-circle me-1" />Unsaved changes</span>}
                    </div>
                    {editable && <div className="small text-muted-hh mb-2"><Icon className="bi bi-info-circle me-1" />Drag rooms to reorder or move them across the corridor. On touch screens use the arrow buttons.</div>}
                    {draft.planLayout === 'CORRIDOR' ? (
                      <div className="row g-3">
                        <div className="col-md-6"><Lane lane="top" title="Top side of corridor" /></div>
                        <div className="col-md-6"><Lane lane="bottom" title="Bottom side of corridor" /></div>
                      </div>
                    ) : <Lane lane="top" title={draft.planLayout === 'GRID' ? 'Rooms (grid order)' : 'Rooms (left to right)'} />}
                  </div>
                  <div className="hh-card">
                    <h3 className="hh-card-title mb-3">Preview</h3>
                    {previewRooms.length === 0 ? <EmptyState icon="bi-door-closed" title="No rooms on this floor yet" />
                      : <FloorPlan rooms={previewRooms} floorName={floor.name} layout={draft} onOpen={() => {}} />}
                  </div>
                </>
              )}
            </div>
          </div>
        )}
      </AsyncContent>

      <BuildingModal show={modal?.type === 'building'} hostelId={hostelId} building={null} onClose={() => setModal(null)}
        onSaved={() => { setModal(null); loadStructure(null); }} />
      <FloorModal show={modal?.type === 'floor'} buildingId={buildingId} buildingName={building?.name} floor={modal?.floor || null}
        nextLevel={nextLevel} onClose={() => setModal(null)} onSaved={() => { setModal(null); loadStructure(floorId); }} />
      <RoomModal show={modal?.type === 'room'} floorId={floorId} floors={floorOptions} room={modal?.room || null}
        onClose={() => setModal(null)} onSaved={() => { setModal(null); loadStructure(floorId); }} />
      {modal?.type === 'beds' && <RoomBedsModal roomId={modal.roomId} canManage={editable} onClose={() => setModal(null)} onChanged={() => loadStructure(floorId)} />}
      <ConfirmDialog show={modal?.type === 'deleteFloor'} title={`Delete ${modal?.floor?.name || 'floor'}?`} variant="danger" confirmLabel="Delete floor"
        message="The floor and its rooms are removed from the plan. Floors with residents cannot be deleted; history is kept." onConfirm={removeFloor} onClose={() => setModal(null)} />
      <ConfirmDialog show={modal?.type === 'deleteRoom'} title={`Delete room ${modal?.room?.roomNumber || ''}?`} variant="danger" confirmLabel="Delete room"
        message="Rooms with residents or live reservations cannot be deleted; history is kept." onConfirm={removeRoom} onClose={() => setModal(null)} />
    </div>
  );
}
