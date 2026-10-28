import { useMemo, useState } from 'react';
import useApi from '../../hooks/useApi';
import { roomApi } from '../../services/endpoints';
import { AsyncContent } from '../common/States';
import { money } from '../../utils/format';
import Icon from '../common/Icon';

/**
 * Lists rooms with free beds in a hostel (grouped by building / floor) and lets the user pick one AVAILABLE bed.
 * onSelect({ bed, room }) — room is the RoomView from the API (rent, deposit, location).
 */
export default function BedPicker({ hostelId, selectedBedId, onSelect, excludeBedId, initialRoomId }) {
  const [q, setQ] = useState('');
  const { data, loading, error, reload } = useApi(
    () => (hostelId ? roomApi.list({ hostelId, status: 'HAS_AVAILABLE', size: 200 }) : Promise.resolve(null)),
    [hostelId],
  );

  const groups = useMemo(() => {
    const rooms = (data?.content || []).filter((r) => !q.trim() || r.roomNumber.toLowerCase().includes(q.trim().toLowerCase())
      || (r.roomType || '').toLowerCase().includes(q.trim().toLowerCase()));
    const map = new Map();
    rooms.forEach((r) => {
      const key = `${r.buildingId}-${r.floorId}`;
      if (!map.has(key)) map.set(key, { key, title: `${r.buildingName} · ${r.floorName}`, level: r.floorLevel, building: r.buildingName, rooms: [] });
      map.get(key).rooms.push(r);
    });
    return [...map.values()].sort((a, b) => a.building.localeCompare(b.building) || a.level - b.level);
  }, [data, q]);

  if (!hostelId) return <div className="text-muted-hh small py-3">Select a hostel to see free beds.</div>;
  const total = data?.content?.length || 0;

  return (
    <AsyncContent loading={loading} error={error} onRetry={reload} compact empty={data && total === 0}
      emptyProps={{ icon: 'bi-door-closed', title: 'No free beds in this hostel', message: 'Every bed is occupied, reserved or under maintenance.' }}>
      {total > 6 && (
        <div className="hh-search mb-3" style={{ maxWidth: 320 }}>
          <Icon className="bi bi-search" aria-hidden="true" />
          <input type="search" className="form-control" placeholder="Filter by room number or type" aria-label="Filter rooms" value={q} onChange={(e) => setQ(e.target.value)} />
        </div>
      )}
      {groups.length === 0 && total > 0 && <div className="small text-muted-hh">No rooms match "{q}".</div>}
      {groups.map((g) => (
        <fieldset key={g.key} className="mb-3">
          <legend className="small fw-bold text-muted-hh text-uppercase mb-2" style={{ fontSize: '.75rem', letterSpacing: '.04em' }}>
            <Icon className="bi bi-layers me-1" aria-hidden="true" />{g.title}
          </legend>
          <div className="row g-2">
            {g.rooms.map((r) => {
              const freeBeds = r.beds.filter((b) => b.state === 'AVAILABLE' && b.id !== excludeBedId);
              const highlight = initialRoomId && Number(initialRoomId) === r.id;
              return (
                <div className="col-12 col-sm-6 col-xl-4" key={r.id}>
                  <div className={`hh-list-card h-100 mb-0 ${highlight ? 'border-primary' : ''}`}>
                    <div className="d-flex justify-content-between align-items-start gap-2">
                      <div>
                        <div className="fw-bold">Room {r.roomNumber}</div>
                        <div className="small text-muted-hh">{r.roomType} · {r.occupiedBeds}/{r.totalBeds} occupied</div>
                      </div>
                      <div className="text-end small">
                        <div className="fw-semibold">{money(r.monthlyRentPerBed)}<span className="text-muted-hh fw-normal">/mo</span></div>
                        <div className="text-muted-hh">Deposit {money(r.securityDeposit)}</div>
                      </div>
                    </div>
                    <div className="d-flex flex-wrap gap-2 mt-2" role="radiogroup" aria-label={`Free beds in room ${r.roomNumber}`}>
                      {freeBeds.length === 0 && <span className="small text-muted-hh">No other free bed</span>}
                      {freeBeds.map((b) => {
                        const active = selectedBedId === b.id;
                        return (
                          <button key={b.id} type="button" role="radio" aria-checked={active}
                            className={`btn btn-sm ${active ? 'btn-primary' : 'btn-light'}`} style={{ minWidth: 64 }}
                            onClick={() => onSelect({ bed: b, room: r })}
                            title={`Bed ${b.label} · ${money(b.rent)} / month`}>
                            <Icon className={`bi ${active ? 'bi-check-circle-fill' : 'bi-circle'} me-1`} aria-hidden="true" />Bed {b.label}
                            {String(b.rent) !== String(r.monthlyRentPerBed) && Number(b.rent) !== Number(r.monthlyRentPerBed) && <div className="small">{money(b.rent)}</div>}
                          </button>
                        );
                      })}
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        </fieldset>
      ))}
    </AsyncContent>
  );
}
