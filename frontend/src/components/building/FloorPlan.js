import RoomTile from './RoomTile';
import Icon from '../common/Icon';

export const DEFAULT_LAYOUT = { planLayout: 'CORRIDOR', hasLift: true, hasStairs: true };

/**
 * Splits rooms into the two sides of the corridor. Rooms pinned to TOP/BOTTOM stay there; AUTO rooms fill the
 * shorter side. Order within a side follows sortOrder (then the incoming order).
 */
export function splitRows(rooms, planLayout = 'CORRIDOR') {
  const ordered = rooms.map((r, i) => ({ r, i }))
    .sort((a, b) => (a.r.sortOrder ?? 0) - (b.r.sortOrder ?? 0) || a.i - b.i).map((x) => x.r);
  if (planLayout !== 'CORRIDOR') return { top: ordered, bottom: [] };
  const top = ordered.filter((r) => r.planRow === 'TOP');
  const bottom = ordered.filter((r) => r.planRow === 'BOTTOM');
  ordered.filter((r) => !r.planRow || r.planRow === 'AUTO').forEach((r) => {
    (top.length <= bottom.length ? top : bottom).push(r);
  });
  const byOrder = (a, b) => ordered.indexOf(a) - ordered.indexOf(b);
  return { top: top.sort(byOrder), bottom: bottom.sort(byOrder) };
}

/**
 * Architectural floor plan. Layouts: CORRIDOR (rooms on both sides of a corridor), SINGLE_ROW (one row facing a
 * corridor) or GRID (open grid). Stairs and lift blocks are optional per floor. On phones it becomes a room grid.
 */
export default function FloorPlan({ rooms, floorName, onOpen, layout = DEFAULT_LAYOUT }) {
  const { planLayout = 'CORRIDOR', hasLift = true, hasStairs = true } = layout || DEFAULT_LAYOUT;
  const { top, bottom } = splitRows(rooms, planLayout);
  const h = (i, n) => (n <= 2 ? 'center' : i === 0 ? 'left' : i === n - 1 ? 'right' : 'center');
  const plants = Math.max(2, Math.min(5, top.length));
  const mainCol = hasStairs ? 2 : 1;
  const cols = `${hasStairs ? '54px ' : ''}minmax(0, 1fr)${hasLift ? ' 70px' : ''}`;
  const sideRows = planLayout === 'CORRIDOR' ? 3 : planLayout === 'SINGLE_ROW' ? 2 : 1;
  const span = { gridRow: `1 / span ${sideRows}` };

  return (
    <div className="hh-plan">
      <div className="hh-plan-badge"><strong>{floorName}</strong><span>{rooms.length} Room{rooms.length === 1 ? '' : 's'}</span></div>
      <div className="hh-compass" aria-hidden="true"><b>N</b><Icon className="bi bi-compass" /></div>
      <div className="hh-plan-frame" style={{ gridTemplateColumns: cols }}>
        {hasStairs && <div className="hh-plan-stairs" style={span} aria-hidden="true" title="Stairs" />}
        {planLayout === 'GRID' ? (
          <div className="hh-plan-grid" style={{ gridColumn: mainCol, gridRow: 1 }}>
            {top.map((r, i) => <RoomTile key={r.id} room={r} onOpen={onOpen} placement={{ v: 'down', h: h(i % 4, 4) }} />)}
          </div>
        ) : (
          <>
            <div className="hh-plan-row top" style={{ gridColumn: mainCol, gridRow: 1 }}>
              {top.map((r, i) => <RoomTile key={r.id} room={r} onOpen={onOpen} placement={{ v: 'down', h: h(i, top.length) }} />)}
            </div>
            <div className="hh-plan-corridor" style={{ gridColumn: mainCol, gridRow: 2 }} aria-hidden="true">
              {Array.from({ length: plants }).map((_, i) => <span key={i} className="hh-plant" />)}
            </div>
            {planLayout === 'CORRIDOR' && (
              <div className="hh-plan-row bottom" style={{ gridColumn: mainCol, gridRow: 3 }}>
                {bottom.map((r, i) => <RoomTile key={r.id} room={r} onOpen={onOpen} placement={{ v: 'up', h: h(i, bottom.length) }} />)}
                {bottom.length < top.length && <div className="hh-room-cell hh-plan-void" aria-hidden="true" />}
              </div>
            )}
          </>
        )}
        {hasLift && (
          <div className="hh-plan-service" style={{ gridColumn: mainCol + 1, ...span, gridTemplateRows: sideRows === 1 ? '1fr' : '1fr 1.2fr 1fr' }} aria-hidden="true">
            {sideRows > 1 && <div className="hh-plan-plant"><span className="hh-plant" /></div>}
            <div className="hh-plan-lift"><span><Icon className="bi bi-arrow-down-up me-1" />Lift</span></div>
            {sideRows > 1 && <div className="hh-plan-plant"><span className="hh-plant" /></div>}
          </div>
        )}
      </div>
      <div className="hh-room-grid hh-plan-mobile" style={{ display: 'none' }}>
        {[...top, ...bottom].map((r) => <RoomTile key={r.id} room={r} onOpen={onOpen} />)}
      </div>
    </div>
  );
}
