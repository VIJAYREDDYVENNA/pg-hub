import AuthImage from '../common/AuthImage';
import { HostelPhoto } from '../common/Illustrations';
import { number, percent } from '../../utils/format';

export default function HostelCard({ hostel, active, onSelect }) {
  const s = hostel.stats;
  return (
    <button type="button" className={`hh-hostel-card ${active ? 'active' : ''}`} onClick={() => onSelect(hostel.id)} aria-pressed={active}
      aria-label={`${hostel.name}, ${percent(s.occupancyPercent)} occupancy`}>
      <span className="hh-hostel-thumb">
        {hostel.imageUrl ? <AuthImage src={hostel.imageUrl} alt="" fallback={<HostelPhoto seed={hostel.id} />} /> : <HostelPhoto seed={hostel.id} />}
      </span>
      <span className="min-w-0">
        <span className="hh-hostel-name d-block text-truncate">{hostel.name}</span>
        <span className="hh-hostel-meta d-block text-truncate">{number(s.totalRooms)} Rooms &nbsp;|&nbsp; {number(s.occupiedBeds)} Residents</span>
        <span className="hh-hostel-occ d-block">{percent(s.occupancyPercent)} Occupancy</span>
      </span>
    </button>
  );
}
