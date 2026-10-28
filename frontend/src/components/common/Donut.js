/** SVG donut chart. segments: [{ value, color, label }]. */
export default function Donut({ segments, size = 120, thickness = 16, children, label }) {
  const total = segments.reduce((s, x) => s + (Number(x.value) || 0), 0);
  const r = (size - thickness) / 2;
  const c = 2 * Math.PI * r;
  let offset = 0;
  return (
    <div style={{ position: 'relative', width: size, height: size, flexShrink: 0 }} role="img" aria-label={label}>
      <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`}>
        <circle cx={size / 2} cy={size / 2} r={r} fill="none" stroke="var(--hh-row)" strokeWidth={thickness} />
        {total > 0 && segments.filter((s) => s.value > 0).map((s) => {
          const len = (s.value / total) * c;
          const el = (
            <circle key={s.label} cx={size / 2} cy={size / 2} r={r} fill="none" stroke={s.color} strokeWidth={thickness}
              strokeDasharray={`${len} ${c - len}`} strokeDashoffset={-offset} transform={`rotate(-90 ${size / 2} ${size / 2})`} />
          );
          offset += len;
          return el;
        })}
      </svg>
      <div style={{ position: 'absolute', inset: 0, display: 'grid', placeItems: 'center', textAlign: 'center' }}>{children}</div>
    </div>
  );
}

/** Single-value progress ring (e.g. occupancy %). */
export function Ring({ percent = 0, size = 64, thickness = 8, color = 'var(--hh-primary)' }) {
  const p = Math.max(0, Math.min(100, Number(percent) || 0));
  return (
    <Donut size={size} thickness={thickness} label={`${p}%`}
      segments={[{ value: p, color, label: 'done' }, { value: 100 - p, color: 'transparent', label: 'rest' }]}>
      <span style={{ fontWeight: 700, fontSize: size > 70 ? '1.05rem' : '.9rem' }}>{Math.round(p)}%</span>
    </Donut>
  );
}
