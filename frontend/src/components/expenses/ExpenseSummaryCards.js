import Icon from '../common/Icon';
import { money, number } from '../../utils/format';

/** "12% from previous period" with a green down arrow (spending fell) or red up arrow (spending rose). */
function Trend({ pct, suffix }) {
  if (pct === null || pct === undefined) return <span className="hh-exp-trend muted">No earlier data to compare</span>;
  const rounded = Math.round(Math.abs(pct));
  if (rounded === 0) return <span className="hh-exp-trend muted">No change {suffix}</span>;
  const down = pct < 0;
  return (
    <span className={`hh-exp-trend ${down ? 'good' : 'bad'}`}>
      <Icon className={`bi ${down ? 'bi-arrow-down' : 'bi-arrow-up'}`} aria-hidden="true" />
      <strong>{rounded}%</strong> {suffix}
    </span>
  );
}

function Card({ tone, icon, title, value, children, loading }) {
  return (
    <div className="hh-exp-stat">
      <span className={`hh-exp-stat-icon ${tone}`} aria-hidden="true"><Icon className={`bi ${icon}`} /></span>
      <div className="min-w-0">
        <div className="hh-exp-stat-label">{title}</div>
        <div className="hh-exp-stat-value">{loading ? <span className="placeholder col-8" /> : value}</div>
        <div className="hh-exp-stat-sub">{loading ? <span className="placeholder col-10" /> : children}</div>
      </div>
    </div>
  );
}

/** `s` comes from ExpensesPage: totals and comparisons computed from the live data. */
export default function ExpenseSummaryCards({ s, loading }) {
  return (
    <div className="hh-exp-stats" role="list" aria-label="Expense summary">
      <Card tone="orange" icon="bi-wallet2" title="Total Expenses" value={s && money(s.total)} loading={loading}>
        {s && <Trend pct={s.totalChange} suffix="from previous period" />}
      </Card>
      <Card tone="purple" icon="bi-file-earmark-text" title="This Month" value={s && money(s.thisMonth)} loading={loading}>
        {s && <Trend pct={s.monthChange} suffix="from last month" />}
      </Card>
      <Card tone="blue" icon="bi-buildings" title="No. of Expenses" value={s && number(s.count)} loading={loading}>
        {s && <span className="hh-exp-trend up"><Icon className="bi bi-arrow-up" aria-hidden="true" /><strong>{s.newThisMonth}</strong> new this month</span>}
      </Card>
      <Card tone="red" icon="bi-credit-card-2-front" title="Avg. per Expense" value={s && money(s.average)} loading={loading}>
        {s && <Trend pct={s.averageChange} suffix="from previous period" />}
      </Card>
    </div>
  );
}
