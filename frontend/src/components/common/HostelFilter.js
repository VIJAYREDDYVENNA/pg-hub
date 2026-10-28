import { useHostels } from '../../context/HostelContext';

/** Hostel dropdown bound to the global hostel selection. */
export default function HostelFilter({ allowAll = true, value, onChange, className = '', id = 'hostel-filter' }) {
  const { hostels, selectedHostelId, selectHostel } = useHostels();
  const current = value !== undefined ? value : selectedHostelId;
  const change = onChange || selectHostel;
  return (
    <select id={id} className={`form-select ${className}`} aria-label="Hostel" value={current || ''}
      onChange={(e) => change(e.target.value ? Number(e.target.value) : null)}>
      {allowAll && <option value="">All hostels</option>}
      {!allowAll && !current && <option value="">Select hostel…</option>}
      {hostels.map((h) => <option key={h.id} value={h.id}>{h.name}</option>)}
    </select>
  );
}
