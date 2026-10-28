import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import Drawer from '../common/Drawer';
import StatusBadge from '../common/StatusBadge';
import ContactButtons from '../common/WhatsAppButtons';
import { AsyncContent } from '../common/States';
import { roomApi } from '../../services/endpoints';
import { BED_STATE, ROOM_STATUS } from '../../utils/status';
import { date, money } from '../../utils/format';
import { useAuth } from '../../context/AuthContext';
import CheckoutDialog from '../residents/CheckoutDialog';
import Icon from '../common/Icon';

const PAY = { PAID_UP: { label: 'Paid up', cls: 'success', icon: 'bi-check-circle' }, DUE: { label: 'Due', cls: 'warning', icon: 'bi-hourglass' },
  OVERDUE: { label: 'Overdue', cls: 'danger', icon: 'bi-exclamation-circle' } };

/** Room details: bed-by-bed status, residents, contact shortcuts and actions. */
export default function RoomDetailsPanel({ roomId, onClose, onChanged }) {
  const [room, setRoom] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [checkout, setCheckout] = useState(null);
  const { can } = useAuth();

  const load = async () => {
    setLoading(true); setError(null);
    try { setRoom(await roomApi.get(roomId)); } catch (e) { setError(e); } finally { setLoading(false); }
  };
  useEffect(() => { if (roomId) load(); }, [roomId]); // eslint-disable-line react-hooks/exhaustive-deps

  return (
    <Drawer show={!!roomId} onClose={onClose} width={560}
      title={room ? `Room ${room.roomNumber}` : 'Room details'}
      subtitle={room && `${room.hostelName} · ${room.buildingName} · ${room.floorName}`}
      footer={room && <>
        {can('MAINTENANCE_MANAGE') && <Link className="btn btn-light" to={`/maintenance?roomId=${room.id}&hostelId=${room.hostelId}&new=1`}><Icon className="bi bi-tools me-1" />Report issue</Link>}
      </>}>
      <AsyncContent loading={loading} error={error} onRetry={load}>
        {room && (
          <>
            <div className="d-flex flex-wrap gap-2 mb-3">
              <StatusBadge map={ROOM_STATUS} value={room.status} />
              {room.vacatingSoon && <StatusBadge map={ROOM_STATUS} value="VACATING_SOON" />}
              {room.roomUnderMaintenance && room.status !== 'UNDER_MAINTENANCE' && <span className="badge hh-badge hh-badge-maintenance"><Icon className="bi bi-tools me-1" />Maintenance open</span>}
            </div>
            <div className="row g-2 mb-3 text-center">
              {[['Beds', room.totalBeds], ['Occupied', room.occupiedBeds], ['Available', room.availableBeds], ['Reserved', room.reservedBeds]].map(([l, v]) => (
                <div className="col-3" key={l}><div className="hh-card p-2"><div className="fw-bold fs-5">{v}</div><div className="small text-muted-hh">{l}</div></div></div>
              ))}
            </div>
            <dl className="hh-kv mb-3">
              <dt>Room type</dt><dd>{room.roomType}</dd>
              <dt>Rent per bed</dt><dd>{money(room.monthlyRentPerBed)} / month</dd>
              <dt>Security deposit</dt><dd>{money(room.securityDeposit)}</dd>
              {room.amenities && <><dt>Amenities</dt><dd>{room.amenities}</dd></>}
            </dl>
            <h6 className="fw-bold mb-2">Beds</h6>
            {room.beds.map((b) => {
              const meta = BED_STATE[b.state];
              const r = b.resident;
              return (
                <div key={b.id} className={`hh-bed-card ${meta.cls}`}>
                  <div className="d-flex justify-content-between align-items-center gap-2">
                    <div className="fw-bold">Bed {b.label}</div>
                    <StatusBadge map={BED_STATE} value={b.state} />
                  </div>
                  {r ? (
                    <div className="mt-2">
                      <div className="d-flex justify-content-between align-items-center gap-2 flex-wrap">
                        <div>
                          <Link to={`/residents/${r.residentId}`} className="fw-semibold">{r.name}</Link>
                          <div className="small text-muted-hh">{r.mobile}</div>
                        </div>
                        <ContactButtons mobile={r.mobile} text={`Hi ${r.name}, `} />
                      </div>
                      <dl className="hh-kv mt-2 small">
                        <dt>Admitted</dt><dd>{date(r.admissionDate)}</dd>
                        <dt>Expected checkout</dt>
                        <dd>{r.expectedCheckoutDate ? <>{date(r.expectedCheckoutDate)} <span className="text-muted-hh">({r.checkoutConfirmed ? 'confirmed' : 'estimated'})</span>
                          {r.checkoutOverdue && <span className="badge hh-badge hh-badge-danger ms-1">Overdue</span>}
                          {!r.checkoutOverdue && r.vacatingSoon && <span className="badge hh-badge hh-badge-soon ms-1">in {r.daysToCheckout} day{r.daysToCheckout === 1 ? '' : 's'}</span>}</> : 'Not set'}</dd>
                        <dt>Monthly rent</dt><dd>{money(r.monthlyRent)}</dd>
                        {r.paymentStatus && <><dt>Payment</dt><dd><StatusBadge map={PAY} value={r.paymentStatus} />{Number(r.outstandingAmount) > 0 && <span className="ms-2">{money(r.outstandingAmount)} due</span>}</dd></>}
                      </dl>
                      <div className="d-flex flex-wrap gap-2">
                        <Link to={`/residents/${r.residentId}`} className="btn btn-sm btn-light"><Icon className="bi bi-person-vcard me-1" />Profile</Link>
                        {can('PAYMENT_COLLECT') && <Link to={`/payments/collect?residentId=${r.residentId}`} className="btn btn-sm btn-soft"><Icon className="bi bi-cash-coin me-1" />Collect payment</Link>}
                        {can('RESIDENT_MANAGE') && <button type="button" className="btn btn-sm btn-light" onClick={() => setCheckout(r)}><Icon className="bi bi-box-arrow-right me-1" />Vacate bed</button>}
                      </div>
                    </div>
                  ) : b.reservation ? (
                    <div className="small mt-2"><Icon className="bi bi-bookmark me-1" />Reserved for <strong>{b.reservation.guestName}</strong> from {date(b.reservation.expectedAdmissionDate)}</div>
                  ) : b.state === 'AVAILABLE' ? (
                    <div className="d-flex justify-content-between align-items-center mt-2">
                      <span className="small text-muted-hh">Free · {money(b.rent)} / month</span>
                    </div>
                  ) : <div className="small text-muted-hh mt-2"><Icon className="bi bi-tools me-1" />Unavailable due to maintenance</div>}
                </div>
              );
            })}
          </>
        )}
      </AsyncContent>
      <CheckoutDialog resident={checkout} onClose={() => setCheckout(null)}
        onDone={() => { setCheckout(null); load(); onChanged?.(); }} />
    </Drawer>
  );
}
