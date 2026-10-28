import { useState } from 'react';
import { Link } from 'react-router-dom';
import StatusBadge from '../common/StatusBadge';
import { EmptyState } from '../common/States';
import CheckoutDialog from './CheckoutDialog';
import { EditAdmissionModal, TransferModal } from './AdmissionDialogs';
import { ADMISSION_STATUS } from './ResidentBits';
import { useAuth } from '../../context/AuthContext';
import { date, label, money, today } from '../../utils/format';
import Icon from '../common/Icon';

const daysBetween = (a, b) => Math.round((new Date(b) - new Date(a)) / 86400000);
const CYCLE = { MONTHLY: 'Monthly', QUARTERLY: 'Quarterly', HALF_YEARLY: 'Half-yearly', YEARLY: 'Yearly', CUSTOM: 'Custom' };

function AllocationTable({ allocations }) {
  if (!allocations?.length) return <div className="small text-muted-hh">No bed allocations recorded.</div>;
  return (
    <div className="table-responsive">
      <table className="table table-sm hh-table align-middle mb-0">
        <thead><tr><th>Bed</th><th>From</th><th>To</th><th>Reason</th></tr></thead>
        <tbody>
          {allocations.map((al) => (
            <tr key={al.id}>
              <td>
                <div className="fw-semibold">Room {al.roomNumber} · Bed {al.bedLabel}</div>
                <div className="small text-muted-hh">{al.hostelName} · {al.buildingName} · {al.floorName}</div>
              </td>
              <td className="text-nowrap">{date(al.startDate)}</td>
              <td className="text-nowrap">{al.endDate ? date(al.endDate) : <span className="badge hh-badge hh-badge-success"><Icon className="bi bi-house-check me-1" />Current</span>}</td>
              <td className="small">{al.endReason ? label(al.endReason) : '—'}{al.notes && <div className="text-muted-hh">{al.notes}</div>}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

/** Current admission (with actions) and admission history. */
export default function StayTab({ profile, onChanged }) {
  const { can } = useAuth();
  const [editing, setEditing] = useState(null);
  const [transferring, setTransferring] = useState(null);
  const [checkout, setCheckout] = useState(null);
  const a = profile.currentAdmission;
  const bed = a?.currentBed;
  const canManage = can('RESIDENT_MANAGE');
  const history = (profile.admissionHistory || []).filter((h) => !a || h.id !== a.id);

  let checkoutInfo = null;
  if (a?.expectedCheckoutDate) {
    const d = daysBetween(today(), a.expectedCheckoutDate);
    checkoutInfo = d < 0
      ? <span className="badge hh-badge hh-badge-danger ms-1"><Icon className="bi bi-exclamation-circle me-1" />Overdue by {-d} day{d === -1 ? '' : 's'}</span>
      : <span className="badge hh-badge hh-badge-soon ms-1"><Icon className="bi bi-hourglass-split me-1" />{d === 0 ? 'Today' : `in ${d} day${d === 1 ? '' : 's'}`}</span>;
  }

  return (
    <>
      <section className="hh-card mb-3">
        <div className="hh-card-header">
          <h2 className="hh-card-title"><Icon className="bi bi-house-door me-2 text-primary-hh" />Current stay</h2>
          {a && canManage && (
            <div className="d-flex flex-wrap gap-2 no-print">
              <button type="button" className="btn btn-sm btn-light" onClick={() => setEditing(a)}><Icon className="bi bi-pencil me-1" />Edit stay</button>
              <button type="button" className="btn btn-sm btn-light" onClick={() => setTransferring(a)}><Icon className="bi bi-arrow-left-right me-1" />Transfer bed</button>
              <button type="button" className="btn btn-sm btn-outline-primary" onClick={() => setCheckout({ admissionId: a.id, name: profile.fullName })}>
                <Icon className="bi bi-box-arrow-right me-1" />Check out</button>
            </div>
          )}
        </div>
        {a ? (
          <dl className="hh-kv">
            <dt>Bed</dt>
            <dd>{bed ? <><strong>Room {bed.roomNumber} · Bed {bed.bedLabel}</strong><div className="small text-muted-hh">{bed.hostelName} · {bed.buildingName} · {bed.floorName} (since {date(bed.startDate)})</div></> : '—'}</dd>
            <dt>Admitted on</dt><dd>{date(a.admissionDate)}</dd>
            <dt>Expected checkout</dt>
            <dd>{a.expectedCheckoutDate
              ? <>{date(a.expectedCheckoutDate)}{' '}
                {a.checkoutConfirmed
                  ? <span className="badge hh-badge hh-badge-success"><Icon className="bi bi-check2-circle me-1" />Confirmed</span>
                  : <span className="badge hh-badge hh-badge-secondary"><Icon className="bi bi-question-circle me-1" />Estimated</span>}
                {checkoutInfo}</>
              : <span className="text-muted-hh">Open-ended</span>}</dd>
            <dt>Monthly rent</dt><dd>{money(a.monthlyRent)}</dd>
            <dt>Security deposit</dt><dd>{money(a.securityDeposit)}</dd>
            <dt>Billing cycle</dt><dd>{CYCLE[a.billingCycle] || label(a.billingCycle)}{a.billingCycle === 'CUSTOM' || a.cycleMonths > 1 ? ` (every ${a.cycleMonths} month${a.cycleMonths === 1 ? '' : 's'})` : ''}</dd>
            {a.notes && <><dt>Notes</dt><dd style={{ whiteSpace: 'pre-wrap' }}>{a.notes}</dd></>}
          </dl>
        ) : (
          <EmptyState icon="bi-door-closed" title="Not staying in a bed right now"
            />
        )}
        {a && (
          <>
            <h3 className="h6 fw-bold mt-4 mb-2">Bed history for this stay</h3>
            <AllocationTable allocations={a.allocations} />
          </>
        )}
      </section>

      <section className="hh-card mb-3">
        <h2 className="hh-card-title mb-3"><Icon className="bi bi-clock-history me-2 text-primary-hh" />Previous stays</h2>
        {history.length === 0 ? <div className="small text-muted-hh">No previous stays.</div> : history.map((h) => (
          <div key={h.id} className="hh-list-card">
            <div className="d-flex justify-content-between flex-wrap gap-2">
              <div>
                <div className="fw-semibold">{h.hostelName}</div>
                <div className="small text-muted-hh">{date(h.admissionDate)} → {h.actualCheckoutDate ? date(h.actualCheckoutDate) : '—'} · {money(h.monthlyRent)} / month</div>
              </div>
              <StatusBadge map={ADMISSION_STATUS} value={h.status} />
            </div>
            {h.checkoutNotes && <div className="small mt-1"><Icon className="bi bi-chat-left-text me-1" />{h.checkoutNotes}</div>}
            <details className="mt-2">
              <summary className="small">Bed allocations ({h.allocations?.length || 0})</summary>
              <div className="mt-2"><AllocationTable allocations={h.allocations} /></div>
            </details>
          </div>
        ))}
      </section>

      <EditAdmissionModal admission={editing} onClose={() => setEditing(null)} onDone={() => { setEditing(null); onChanged(); }} />
      <TransferModal admission={transferring} onClose={() => setTransferring(null)} onDone={() => { setTransferring(null); onChanged(); }} />
      <CheckoutDialog resident={checkout} onClose={() => setCheckout(null)} onDone={() => { setCheckout(null); onChanged(); }} />
    </>
  );
}
