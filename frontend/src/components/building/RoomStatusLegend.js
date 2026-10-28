import { CATEGORY_META } from './roomDisplay';

const ORDER = ['occupied', 'partial', 'vacant', 'soon', 'maintenance'];

export default function RoomStatusLegend({ warningDays }) {
  return (
    <div className="hh-legend" aria-label="Legend">
      {ORDER.map((k) => (
        <span key={k}>
          <i className="sw" style={{ background: CATEGORY_META[k].color }} />
          {CATEGORY_META[k].label}
          {k === 'occupied' && <span className="opacity-75">(All beds filled)</span>}
          {k === 'vacant' && <span className="opacity-75">(No occupants)</span>}
          {k === 'soon' && warningDays && <span className="opacity-75">(Within {warningDays} days)</span>}
        </span>
      ))}
    </div>
  );
}
