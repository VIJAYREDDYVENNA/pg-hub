import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import Modal from '../common/Modal';
import StatusBadge from '../common/StatusBadge';
import ConfirmDialog from '../common/ConfirmDialog';
import { AsyncContent } from '../common/States';
import { BedModal } from './StructureModals';
import { bedApi, roomApi } from '../../services/endpoints';
import { errorMessage } from '../../services/apiClient';
import { useToast } from '../../context/ToastContext';
import { BED_STATE, ROOM_STATUS } from '../../utils/status';
import { money } from '../../utils/format';
import Icon from '../common/Icon';

/** Bed management for one room: list beds with live state, edit label/status/rent override, add and delete. */
export default function RoomBedsModal({ roomId, canManage, onClose, onChanged }) {
  const { notify } = useToast();
  const [room, setRoom] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [editing, setEditing] = useState(undefined); // undefined closed, null new, bed edit
  const [deleting, setDeleting] = useState(null);

  const load = useCallback(async () => {
    if (!roomId) return;
    setLoading(true); setError(null);
    try { setRoom(await roomApi.get(roomId)); } catch (e) { setError(e); } finally { setLoading(false); }
  }, [roomId]);

  useEffect(() => { setRoom(null); load(); }, [load]);

  const changed = () => { load(); onChanged?.(); };

  const remove = async () => {
    try {
      await bedApi.remove(deleting.id);
      notify(`Bed ${deleting.label} removed.`);
      setDeleting(null);
      changed();
    } catch (e) {
      notify(errorMessage(e), 'danger');
    }
  };

  const beds = (room?.beds || []).filter((b) => b.id);

  return (
    <Modal show={!!roomId} onClose={onClose} size="lg"
      title={room ? `Beds · Room ${room.roomNumber}` : 'Beds'}
      footer={<>
        <button type="button" className="btn btn-light" onClick={onClose}>Close</button>
        {canManage && room && <button type="button" className="btn btn-primary" onClick={() => setEditing(null)}><Icon className="bi bi-plus-lg me-1" />Add bed</button>}
      </>}>
      {room && <div className="small text-muted-hh mb-2">{room.buildingName} · {room.floorName} · {money(room.monthlyRentPerBed)} per bed</div>}
      <AsyncContent loading={loading && !room} error={error} onRetry={load}
        empty={room && beds.length === 0} emptyProps={{ icon: 'bi-grid-3x2-gap', title: 'No active beds', message: 'Add a bed to start allocating residents.' }}>
        {room && (
          <>
            <div className="d-flex flex-wrap gap-2 mb-3">
              <StatusBadge map={ROOM_STATUS} value={room.status} />
              <span className="small text-muted-hh align-self-center">{room.occupiedBeds}/{room.totalBeds} occupied · {room.availableBeds} free</span>
            </div>
            <p className="small text-muted-hh">Inactive beds are hidden from this list. Occupied or reserved beds cannot be removed.</p>
            {beds.map((b) => {
              const meta = BED_STATE[b.state] || {};
              const locked = b.state === 'OCCUPIED' || b.state === 'RESERVED';
              return (
                <div key={b.id} className={`hh-bed-card ${meta.cls || ''}`}>
                  <div className="d-flex justify-content-between align-items-center gap-2 flex-wrap">
                    <div>
                      <div className="fw-bold">Bed {b.label}</div>
                      <div className="small text-muted-hh">{money(b.rent)} / month{String(b.rent) !== String(room.monthlyRentPerBed) && Number(b.rent) !== Number(room.monthlyRentPerBed) ? ' (custom rent)' : ''}</div>
                    </div>
                    <StatusBadge map={BED_STATE} value={b.state} />
                  </div>
                  {b.resident && (
                    <div className="small mt-2"><Icon className="bi bi-person me-1" /><Link to={`/residents/${b.resident.residentId}`}>{b.resident.name}</Link></div>
                  )}
                  {b.reservation && (
                    <div className="small mt-2"><Icon className="bi bi-bookmark me-1" />Reserved for {b.reservation.guestName}</div>
                  )}
                  {canManage && (
                    <div className="d-flex gap-2 mt-2">
                      <button type="button" className="btn btn-sm btn-light" onClick={() => setEditing(b)}><Icon className="bi bi-pencil me-1" />Edit</button>
                      <button type="button" className="btn btn-sm btn-light text-danger" disabled={locked} onClick={() => setDeleting(b)}
                        title={locked ? 'Occupied or reserved beds cannot be removed' : `Remove bed ${b.label}`}>
                        <Icon className="bi bi-trash me-1" />Remove
                      </button>
                    </div>
                  )}
                </div>
              );
            })}
          </>
        )}
      </AsyncContent>
      {room && (
        <BedModal show={editing !== undefined} roomId={room.id} roomRent={room.monthlyRentPerBed} bed={editing || null}
          onClose={() => setEditing(undefined)} onSaved={() => { setEditing(undefined); changed(); }} />
      )}
      <ConfirmDialog show={!!deleting} title="Remove bed" variant="danger" confirmLabel="Remove bed"
        message={deleting && `Remove bed ${deleting.label}? The room's bed count will go down by one.`}
        onConfirm={remove} onClose={() => setDeleting(null)} />
    </Modal>
  );
}
