import AuthImage from './AuthImage';

const COLORS = [
  ['#FFE4D3', '#C2410C'], ['#DBEAFE', '#1D4ED8'], ['#DCFCE7', '#15803D'], ['#FCE7F3', '#BE185D'],
  ['#EDE9FE', '#6D28D9'], ['#FEF3C7', '#B45309'], ['#CFFAFE', '#0E7490'], ['#FFE4E6', '#BE123C'],
];

export function initials(name) {
  return String(name || '?').trim().split(/\s+/).map((p) => p[0]).slice(0, 2).join('').toUpperCase();
}

function colorFor(name) {
  let h = 0;
  for (const ch of String(name || '')) h = (h * 31 + ch.charCodeAt(0)) % 9973;
  return COLORS[h % COLORS.length];
}

/** Circular avatar: uploaded photo when available, otherwise coloured initials. */
export default function Avatar({ name, photoUrl, size = '', title }) {
  const [bg, fg] = colorFor(name);
  const fallback = <span className={`hh-avatar ${size}`} style={{ background: bg, color: fg }} title={title || name}>{initials(name)}</span>;
  if (!photoUrl) return fallback;
  return <span className={`hh-avatar ${size}`} title={title || name}><AuthImage src={photoUrl} alt="" fallback={initials(name)} /></span>;
}

export function AvatarStack({ names = [], max = 3 }) {
  const shown = names.slice(0, max);
  const more = names.length - shown.length;
  if (names.length === 0) return <span className="small text-muted-hh">No residents</span>;
  return (
    <span className="hh-avatar-stack" aria-label={names.join(', ')}>
      {shown.map((n, i) => <Avatar key={`${n}-${i}`} name={n} size="xs" />)}
      {more > 0 && <span className="hh-avatar-more">+{more}</span>}
    </span>
  );
}
