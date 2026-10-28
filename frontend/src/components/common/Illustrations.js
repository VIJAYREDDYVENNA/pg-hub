/*
 * Lightweight SVG illustrations (no external image downloads, work offline and in dark mode).
 */

const PALETTES = [
  { sky: ['#FFE3CF', '#FFF6EE'], wall: '#F4A261', wall2: '#E76F51', trim: '#FFF4EA', win: '#7FB8E6' },
  { sky: ['#DCEBFF', '#F4F8FF'], wall: '#E9C46A', wall2: '#D19A2E', trim: '#FFF8E8', win: '#6FA8DC' },
  { sky: ['#FFE0E0', '#FFF5F2'], wall: '#E5989B', wall2: '#B5838D', trim: '#FFF1F1', win: '#8EC5E8' },
  { sky: ['#DFF5E8', '#F3FBF6'], wall: '#C9A27E', wall2: '#9C7653', trim: '#FFF7EF', win: '#79B6E0' },
  { sky: ['#EDE4FF', '#F8F4FF'], wall: '#F2B880', wall2: '#D98E4A', trim: '#FFF6EC', win: '#8AB6E8' },
];

/** Stylised apartment-block "photo" used for hostels without an uploaded image. */
export function HostelPhoto({ seed = 0, className }) {
  const p = PALETTES[Math.abs(seed) % PALETTES.length];
  const id = `hp${seed}`;
  const windows = [];
  for (let r = 0; r < 4; r += 1) {
    for (let c = 0; c < 4; c += 1) {
      windows.push(<rect key={`a${r}${c}`} x={22 + c * 13} y={22 + r * 14} width="8" height="9" rx="1.5" fill={p.win} opacity={(r + c) % 3 === 0 ? 0.65 : 1} />);
    }
  }
  for (let r = 0; r < 3; r += 1) {
    for (let c = 0; c < 3; c += 1) {
      windows.push(<rect key={`b${r}${c}`} x={82 + c * 12} y={38 + r * 14} width="7" height="8" rx="1.5" fill={p.win} />);
    }
  }
  return (
    <svg className={className} viewBox="0 0 130 100" preserveAspectRatio="xMidYMid slice" aria-hidden="true">
      <defs>
        <linearGradient id={`${id}s`} x1="0" y1="0" x2="0" y2="1"><stop offset="0" stopColor={p.sky[0]} /><stop offset="1" stopColor={p.sky[1]} /></linearGradient>
      </defs>
      <rect width="130" height="100" fill={`url(#${id}s)`} />
      <circle cx="108" cy="16" r="9" fill="#FFD27A" opacity=".8" />
      <rect x="14" y="14" width="62" height="74" rx="3" fill={p.wall} />
      <rect x="14" y="14" width="62" height="6" fill={p.wall2} />
      <rect x="74" y="30" width="44" height="58" rx="3" fill={p.wall2} />
      <rect x="74" y="30" width="44" height="5" fill={p.trim} opacity=".5" />
      {windows}
      <rect x="38" y="74" width="12" height="14" rx="2" fill={p.trim} />
      <rect x="0" y="86" width="130" height="14" fill="#9BD08F" />
      <circle cx="8" cy="84" r="7" fill="#5FAE5A" /><circle cx="122" cy="84" r="8" fill="#5FAE5A" /><circle cx="64" cy="88" r="5" fill="#6CBF66" />
    </svg>
  );
}

/** Large cityscape for the login hero. */
export function LoginCityscape() {
  const block = (x, w, h, fill, cols, rows, key) => {
    const wins = [];
    for (let r = 0; r < rows; r += 1) {
      for (let c = 0; c < cols; c += 1) {
        const lit = (r * 7 + c * 3 + key) % 4 !== 0;
        wins.push(<rect key={`${key}-${r}-${c}`} x={x + 10 + c * ((w - 20) / cols)} y={400 - h + 22 + r * 26}
          width={(w - 20) / cols - 8} height="14" rx="2" fill={lit ? '#FFE7C2' : 'rgba(255,255,255,.25)'} />);
      }
    }
    return (
      <g key={key}>
        <rect x={x} y={400 - h} width={w} height={h} rx="6" fill={fill} />
        <rect x={x} y={400 - h} width={w} height="8" rx="4" fill="rgba(255,255,255,.25)" />
        {wins}
      </g>
    );
  };
  return (
    <svg viewBox="0 0 800 400" preserveAspectRatio="xMidYMax slice" aria-hidden="true">
      <defs>
        <linearGradient id="lcFade" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stopColor="#000" stopOpacity="0" /><stop offset="1" stopColor="#7A2A00" stopOpacity=".35" /></linearGradient>
      </defs>
      {block(20, 120, 210, 'rgba(255,255,255,.16)', 3, 7, 1)}
      {block(150, 150, 300, 'rgba(255,255,255,.22)', 4, 10, 2)}
      {block(310, 120, 240, 'rgba(255,255,255,.14)', 3, 8, 3)}
      {block(440, 170, 330, 'rgba(255,255,255,.24)', 4, 11, 4)}
      {block(620, 150, 230, 'rgba(255,255,255,.16)', 4, 7, 5)}
      <rect x="0" y="380" width="800" height="20" fill="rgba(255,255,255,.18)" />
      {[60, 290, 600, 770].map((cx) => <g key={cx}><rect x={cx - 2} y="352" width="4" height="30" fill="rgba(70,30,0,.35)" /><circle cx={cx} cy="348" r="18" fill="rgba(140,220,140,.55)" /></g>)}
      <rect width="800" height="400" fill="url(#lcFade)" />
    </svg>
  );
}
