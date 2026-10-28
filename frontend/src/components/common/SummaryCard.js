import { Link } from 'react-router-dom';
import Icon from './Icon';

/** Dashboard metric card: label, big value, icon and status colour; navigates when "to" is given. */
export default function SummaryCard({ label, value, icon, tone = 'orange', hint, to, loading }) {
  const body = (
    <div className={`hh-summary-card tone-${tone}`}>
      <div className="hh-summary-icon" aria-hidden="true"><Icon className={`bi ${icon}`} /></div>
      <div className="min-w-0">
        <div className="hh-summary-label">{label}</div>
        <div className="hh-summary-value">{loading ? <span className="placeholder col-6" /> : value}</div>
        {hint && <div className="hh-summary-hint text-truncate">{hint}</div>}
      </div>
      {to && <Icon className="bi bi-chevron-right hh-summary-go" aria-hidden="true" />}
    </div>
  );
  return to ? <Link to={to} className="text-reset text-decoration-none d-block h-100" aria-label={`${label}: ${value}`}>{body}</Link> : body;
}
