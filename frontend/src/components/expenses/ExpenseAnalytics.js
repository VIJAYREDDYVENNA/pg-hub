import { useMemo } from 'react';
import Icon from '../common/Icon';
import Donut from '../common/Donut';
import { date, label, money } from '../../utils/format';
import { CATEGORY_ICONS, CATEGORY_STYLE, HOSTEL_COLORS } from './expenseUtils';

const sumBy = (rows, keyFn) => {
  const m = new Map();
  rows.forEach((r) => m.set(keyFn(r), (m.get(keyFn(r)) || 0) + Math.round(Number(r.amount) * 100)));
  return [...m.entries()].map(([key, paise]) => ({ key, amount: paise / 100 })).sort((a, b) => b.amount - a.amount);
};

const Range = ({ from, to }) => <div className="hh-exp-range-text">{date(from)} – {date(to)}</div>;

/** Horizontal category bars, from the same filtered rows as the table. */
export function CategoryCard({ rows, from, to }) {
  const data = useMemo(() => sumBy(rows, (r) => r.category), [rows]);
  const max = data[0]?.amount || 0;
  return (
    <section className="hh-card hh-exp-side" aria-labelledby="exp-cat-title">
      <div className="hh-exp-side-head">
        <div className="d-flex gap-2 min-w-0">
          <Icon className="bi bi-bar-chart-line text-primary-hh mt-1" aria-hidden="true" />
          <div className="min-w-0"><h2 id="exp-cat-title">Expenses by category</h2><Range from={from} to={to} /></div>
        </div>
      </div>
      {data.length === 0 ? <div className="text-muted-hh small py-3">No expenses in this selection.</div> : (
        <ul className="hh-exp-bars" aria-label="Expenses by category">
          {data.map((d) => (
            <li key={d.key}>
              <span className="hh-exp-bar-label"><Icon className={`bi ${CATEGORY_ICONS[d.key] || 'bi-tag'}`} style={{ color: (CATEGORY_STYLE[d.key] || CATEGORY_STYLE.OTHER).fg }} aria-hidden="true" />{label(d.key)}</span>
              <span className="hh-exp-bar-track" role="presentation"><span style={{ width: `${max ? Math.max(4, (d.amount / max) * 100) : 0}%` }} /></span>
              <span className="hh-exp-bar-amount">{money(d.amount)}</span>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

/** Donut of spend per hostel with a legend, from the same filtered rows. */
export function HostelCard({ rows, hostelName, from, to }) {
  const data = useMemo(() => sumBy(rows, (r) => r.hostelId), [rows]);
  const total = data.reduce((s, d) => s + d.amount, 0);
  const segments = data.map((d, i) => ({ label: hostelName(d.key), value: d.amount, color: HOSTEL_COLORS[i % HOSTEL_COLORS.length] }));
  return (
    <section className="hh-card hh-exp-side" aria-labelledby="exp-host-title">
      <div className="hh-exp-side-head">
        <div className="d-flex gap-2 min-w-0">
          <Icon className="bi bi-buildings text-primary-hh mt-1" aria-hidden="true" />
          <div className="min-w-0"><h2 id="exp-host-title">Expenses by hostel</h2><Range from={from} to={to} /></div>
        </div>
      </div>
      {data.length === 0 ? <div className="text-muted-hh small py-3">No expenses in this selection.</div> : (
        <div className="hh-exp-donut">
          <Donut size={150} thickness={22} segments={segments} label={`Expenses by hostel, total ${money(total)}`}>
            <div className="text-center"><div className="fw-bold">{money(total)}</div><div className="small text-muted-hh">Total</div></div>
          </Donut>
          <ul className="hh-exp-legend">
            {segments.map((s) => (
              <li key={s.label}>
                <span className="dot" style={{ background: s.color }} />
                <span className="min-w-0 flex-grow-1"><span className="d-block text-truncate">{s.label}</span><span className="small text-muted-hh">{total ? Math.round((s.value / total) * 100) : 0}%</span></span>
                <span className="amt">{money(s.value)}</span>
              </li>
            ))}
          </ul>
        </div>
      )}
    </section>
  );
}
