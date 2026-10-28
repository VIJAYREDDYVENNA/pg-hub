import { useEffect, useRef, useState } from 'react';
import { roomCategory } from './roomDisplay';
import Avatar from '../common/Avatar';
import { date } from '../../utils/format';
import { useAuth } from '../../context/AuthContext';
import Icon from '../common/Icon';

const PEOPLE = {
  OCCUPIED: ['bi-person-fill', 'p-occ', 'occupied'],
  AVAILABLE: ['bi-person', 'p-free', 'free'],
  RESERVED: ['bi-person-fill-check', 'p-res', 'reserved'],
  MAINTENANCE: ['bi-person-fill-slash', 'p-mnt', 'unavailable'],
};

/**
 * Room tile for the floor plan. Hover (or keyboard focus) opens an interactive card with residents,
 * contact numbers, checkout dates and actions; click/tap opens full room details.
 * placement: { v: 'up' | 'down', h: 'left' | 'right' | 'center' }
 */
export default function RoomTile({ room, onOpen, placement = { v: 'up', h: 'center' } }) {
  const [hover, setHover] = useState(false);
  const timer = useRef(null);
  const { can } = useAuth();
  const cat = roomCategory(room);
  const residents = room.beds.filter((b) => b.resident);
  const free = room.availableBeds;

  const show = () => { clearTimeout(timer.current); timer.current = setTimeout(() => setHover(true), 120); };
  const hide = () => { clearTimeout(timer.current); timer.current = setTimeout(() => setHover(false), 180); };
  useEffect(() => () => clearTimeout(timer.current), []);

  return (
    <div className="hh-room-cell" onMouseEnter={show} onMouseLeave={hide}>
      <button type="button" className={`hh-room-tile ${cat}`} onClick={() => onOpen(room)} onFocus={show} onBlur={hide}
        aria-label={`Room ${room.roomNumber}: ${room.occupiedBeds} of ${room.totalBeds} beds occupied${room.vacatingSoon ? ', vacating soon' : ''}${cat === 'maintenance' ? ', under maintenance' : ''}`}>
        {room.reservedBeds > 0 && <span className="hh-room-tag">Reserved</span>}
        {cat === 'maintenance' && <span className="hh-room-tag"><Icon className="bi bi-tools" /></span>}
        <span className="hh-room-no">{room.roomNumber}</span>
        <span className="hh-room-occ">{room.occupiedBeds}/{room.totalBeds}</span>
        <span className="hh-room-people" aria-hidden="true">
          {room.beds.map((b) => {
            const [icon, cls] = PEOPLE[b.state] || PEOPLE.AVAILABLE;
            return <Icon key={b.id} className={`bi ${icon} ${cls}`} />;
          })}
        </span>
      </button>
      {hover && (
        <div className={`hh-room-pop ${placement.v} ${placement.h}`} role="dialog" aria-label={`Room ${room.roomNumber} summary`}
          onMouseEnter={show} onMouseLeave={hide}>
          <div className="d-flex align-items-center justify-content-between mb-1">
            <strong>Room {room.roomNumber} ({room.occupiedBeds}/{room.totalBeds})</strong>
            {free > 0 && <span className="chip">{free} Vacant Bed{free > 1 ? 's' : ''}</span>}
          </div>
          <div className="small text-muted-hh mb-1">{room.roomType} · {room.floorName}</div>
          {residents.map((b) => (
            <div className="hh-pop-res" key={b.id}>
              <Avatar name={b.resident.name} size="sm" />
              <div className="min-w-0 flex-grow-1">
                <div className="d-flex justify-content-between gap-2">
                  <span className="fw-semibold small text-truncate">{b.resident.name}</span>
                  <span className="small text-muted-hh">{b.resident.mobile}</span>
                </div>
                <div className={`small ${b.resident.vacatingSoon ? 'text-warning fw-semibold' : 'text-muted-hh'}`}>
                  {b.resident.expectedCheckoutDate ? `Staying till ${date(b.resident.expectedCheckoutDate)}` : `Bed ${b.label} · no checkout date`}
                </div>
              </div>
            </div>
          ))}
          {free > 0 && <div className="small mt-1"><Icon className="bi bi-person text-danger me-1" />{free} vacant bed{free > 1 ? 's' : ''} available</div>}
          {room.reservedBeds > 0 && <div className="small"><Icon className="bi bi-bookmark-fill text-primary me-1" />{room.reservedBeds} reserved</div>}
          {cat === 'maintenance' && <div className="small"><Icon className="bi bi-tools me-1" />Under maintenance</div>}
          <div className="hh-pop-actions">
            <button type="button" className="btn btn-sm btn-light" onClick={() => onOpen(room)}>View Details</button>
            <span />
          </div>
        </div>
      )}
    </div>
  );
}
