import Icon from '../common/Icon';
/** Pill tabs; scroll horizontally on narrow screens. tabs: [{ key, label, icon?, count? }] */
export default function Tabs({ tabs, active, onChange, className = 'mb-3' }) {
  return (
    <div className={`nav nav-pills gap-1 flex-nowrap overflow-auto pb-1 ${className}`} role="tablist">
      {tabs.map((t) => (
        <button key={t.key} type="button" role="tab" aria-selected={active === t.key}
          className={`nav-link text-nowrap ${active === t.key ? 'active' : ''}`} onClick={() => onChange(t.key)}>
          {t.icon && <Icon className={`bi ${t.icon} me-1`} aria-hidden="true" />}{t.label}
          {t.count !== undefined && t.count !== null && <span className="badge rounded-pill bg-light text-dark ms-1">{t.count}</span>}
        </button>
      ))}
    </div>
  );
}
