import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useParams, useSearchParams } from 'react-router-dom';
import SummaryCard from '../../components/common/SummaryCard';
import StatusBadge from '../../components/common/StatusBadge';
import ContactButtons from '../../components/common/WhatsAppButtons';
import ConfirmDialog from '../../components/common/ConfirmDialog';
import { ErrorState, LoadingState } from '../../components/common/States';
import { RESIDENT_STATUS, ResidentAvatar, GENDERS, ID_TYPES } from '../../components/residents/ResidentBits';
import StayTab from '../../components/residents/StayTab';
import PaymentsTab from '../../components/residents/PaymentsTab';
import MessagesTab from '../../components/residents/MessagesTab';
import DocumentsTab from '../../components/residents/DocumentsTab';
import { useAuth } from '../../context/AuthContext';
import { useToast } from '../../context/ToastContext';
import { residentApi } from '../../services/endpoints';
import { errorMessage } from '../../services/apiClient';
import { date, dateTime, label, money } from '../../utils/format';
import Icon from '../../components/common/Icon';

const optLabel = (list, v) => list.find((o) => o.value === v)?.label || label(v);

function Kv({ items }) {
  return (
    <dl className="hh-kv">
      {items.filter(([, v]) => v !== undefined).map(([k, v]) => (
        <div key={k} style={{ display: 'contents' }}><dt>{k}</dt><dd>{v || <span className="text-muted-hh">—</span>}</dd></div>
      ))}
    </dl>
  );
}

function Overview({ p, canManage, onConsent, consentBusy }) {
  return (
    <div className="row g-3">
      <div className="col-lg-6">
        <section className="hh-card h-100">
          <h2 className="hh-card-title mb-3"><Icon className="bi bi-person me-2 text-primary-hh" />Personal</h2>
          <Kv items={[
            ['Full name', p.fullName], ['Date of birth', p.dateOfBirth && date(p.dateOfBirth)], ['Gender', p.gender && optLabel(GENDERS, p.gender)],
            ['Occupation', p.occupation], ['Hostel', p.hostelName], ['Registered on', dateTime(p.createdAt)],
          ]} />
        </section>
      </div>
      <div className="col-lg-6">
        <section className="hh-card h-100">
          <h2 className="hh-card-title mb-3"><Icon className="bi bi-telephone me-2 text-primary-hh" />Contact</h2>
          <Kv items={[
            ['Mobile', p.mobile && <span className="d-inline-flex gap-2 align-items-center">{p.mobile}<span className="no-print"><ContactButtons mobile={p.mobile} text={`Hi ${p.fullName}, `} /></span></span>],
            ['Alternate mobile', p.alternateMobile], ['Email', p.email && <a href={`mailto:${p.email}`}>{p.email}</a>],
            ['Address', p.address && <span style={{ whiteSpace: 'pre-wrap' }}>{p.address}</span>],
          ]} />
        </section>
      </div>
      <div className="col-lg-6">
        <section className="hh-card h-100">
          <h2 className="hh-card-title mb-3"><Icon className="bi bi-shield-plus me-2 text-primary-hh" />Emergency &amp; guardian</h2>
          <Kv items={[
            ['Emergency contact', p.emergencyContactName], ['Emergency phone', p.emergencyContactPhone && <a href={`tel:${p.emergencyContactPhone}`}>{p.emergencyContactPhone}</a>],
            ['Guardian', p.guardianName && `${p.guardianName}${p.guardianRelation ? ` (${p.guardianRelation})` : ''}`],
            ['Guardian phone', p.guardianPhone && <a href={`tel:${p.guardianPhone}`}>{p.guardianPhone}</a>],
          ]} />
        </section>
      </div>
      <div className="col-lg-6">
        <section className="hh-card h-100">
          <h2 className="hh-card-title mb-3"><Icon className="bi bi-person-vcard me-2 text-primary-hh" />ID &amp; consent</h2>
          <Kv items={[
            ['ID document', p.idDocumentType && optLabel(ID_TYPES, p.idDocumentType)], ['Document number', p.idDocumentNumber],
          ]} />
          <div className="border-top mt-3 pt-3">
            <div className="d-flex justify-content-between align-items-start gap-3">
              <div>
                <div className="fw-semibold"><Icon className="bi bi-whatsapp text-success me-1" />WhatsApp messages</div>
                <div className="small text-muted-hh">
                  {p.whatsappOptIn
                    ? <>Opted in{p.whatsappOptInAt ? ` on ${dateTime(p.whatsappOptInAt)}` : ''}. Receives confirmations, reminders and receipts.</>
                    : 'Not opted in. No WhatsApp messages will be sent.'}
                </div>
              </div>
              {canManage ? (
                <div className="form-check form-switch m-0 no-print">
                  <input className="form-check-input" type="checkbox" role="switch" id="wa-consent" style={{ width: '2.6em', height: '1.4em' }}
                    checked={p.whatsappOptIn} disabled={consentBusy} onChange={(e) => onConsent(e.target.checked)}
                    aria-label="Resident agrees to receive WhatsApp messages" />
                </div>
              ) : (
                <span className={`badge hh-badge ${p.whatsappOptIn ? 'hh-badge-success' : 'hh-badge-secondary'}`}>
                  <Icon className={`bi ${p.whatsappOptIn ? 'bi-check-circle' : 'bi-x-circle'} me-1`} />{p.whatsappOptIn ? 'Opted in' : 'Not opted in'}</span>
              )}
            </div>
          </div>
        </section>
      </div>
      {p.notes && (
        <div className="col-12">
          <section className="hh-card"><h2 className="hh-card-title mb-2"><Icon className="bi bi-journal-text me-2 text-primary-hh" />Notes</h2>
            <div style={{ whiteSpace: 'pre-wrap' }}>{p.notes}</div></section>
        </div>
      )}
    </div>
  );
}

const TABS = [
  { key: 'overview', label: 'Overview', icon: 'bi-person-lines-fill' },
  { key: 'stay', label: 'Stay', icon: 'bi-house-door' },
  { key: 'payments', label: 'Payments', icon: 'bi-cash-coin', perm: 'PAYMENT_VIEW' },
  { key: 'messages', label: 'Messages', icon: 'bi-whatsapp' },
  { key: 'documents', label: 'Documents', icon: 'bi-folder2-open' },
];

export default function ResidentProfilePage() {
  const { id } = useParams();
  const { can } = useAuth();
  const { notify } = useToast();
  const [params, setParams] = useSearchParams();
  const [p, setP] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [photoBusy, setPhotoBusy] = useState(false);
  const [consentBusy, setConsentBusy] = useState(false);
  const [consentAsk, setConsentAsk] = useState(null);
  const photoRef = useRef(null);
  const canManage = can('RESIDENT_MANAGE');
  const tabs = TABS.filter((t) => !t.perm || can(t.perm));
  const tab = tabs.some((t) => t.key === params.get('tab')) ? params.get('tab') : 'overview';

  const load = useCallback(async (quiet = false) => {
    if (!quiet) setLoading(true);
    setError(null);
    try { setP(await residentApi.get(id)); } catch (e) { setError(e); } finally { setLoading(false); }
  }, [id]);

  useEffect(() => { load(); }, [load]);
  useEffect(() => { document.title = `${p ? p.fullName : 'Resident'} · HostelHub`; }, [p]);

  const setTab = (key) => {
    const next = new URLSearchParams(params);
    if (key === 'overview') next.delete('tab'); else next.set('tab', key);
    setParams(next, { replace: true });
  };

  const uploadPhoto = async (e) => {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;
    setPhotoBusy(true);
    try { await residentApi.uploadPhoto(p.id, file); notify('Photo updated.'); load(true); } catch (ex) { notify(errorMessage(ex), 'danger'); } finally { setPhotoBusy(false); }
  };

  const saveConsent = async (optIn) => {
    setConsentBusy(true);
    try {
      await residentApi.consent(p.id, optIn);
      notify(optIn ? 'WhatsApp consent recorded.' : 'Resident opted out of WhatsApp messages.');
      setConsentAsk(null);
      load(true);
    } catch (ex) { notify(errorMessage(ex), 'danger'); } finally { setConsentBusy(false); }
  };

  if (loading && !p) return <LoadingState label="Loading resident…" />;
  if (error && !p) return <ErrorState error={error} onRetry={() => load()} />;

  const b = p.balance || {};
  const a = p.currentAdmission;
  const bed = a?.currentBed;
  const overdue = Number(b.overdue) > 0;

  return (
    <div>
      <div className="d-flex align-items-center gap-2 mb-3 small no-print">
        <Link to="/residents"><Icon className="bi bi-arrow-left me-1" />Residents</Link>
      </div>

      <header className="hh-card mb-3">
        <div className="d-flex flex-column flex-md-row gap-3 align-items-md-center">
          <div className="d-flex gap-3 align-items-center flex-grow-1 min-w-0">
            <div className="position-relative">
              <ResidentAvatar name={p.fullName} photoUrl={p.photoUrl} size={76} />
              {canManage && (
                <>
                  <button type="button" className="btn btn-sm btn-light rounded-circle position-absolute bottom-0 end-0 p-0 no-print"
                    style={{ width: 30, height: 30, minHeight: 30 }} onClick={() => photoRef.current?.click()} disabled={photoBusy}
                    aria-label="Upload photo" title="Upload photo">
                    {photoBusy ? <span className="spinner-border spinner-border-sm" /> : <Icon className="bi bi-camera" />}
                  </button>
                  <input ref={photoRef} type="file" accept="image/*" className="d-none" onChange={uploadPhoto} />
                </>
              )}
            </div>
            <div className="min-w-0">
              <h1 className="hh-page-title text-truncate">{p.fullName}</h1>
              <div className="d-flex flex-wrap gap-2 align-items-center">
                <StatusBadge map={RESIDENT_STATUS} value={p.status} />
                {p.whatsappOptIn
                  ? <span className="badge hh-badge hh-badge-success"><Icon className="bi bi-whatsapp me-1" />WhatsApp opted in</span>
                  : <span className="badge hh-badge hh-badge-secondary"><Icon className="bi bi-whatsapp me-1" />No WhatsApp consent</span>}
              </div>
              <div className="small text-muted-hh mt-1">
                {bed ? <><Icon className="bi bi-geo-alt me-1" />{bed.hostelName} · {bed.buildingName} · Room {bed.roomNumber} · Bed {bed.bedLabel}</> : p.hostelName}
                {p.mobile && <> · {p.mobile}</>}
              </div>
            </div>
          </div>
          <div className="d-flex flex-wrap gap-2 no-print">
            <ContactButtons mobile={p.mobile} text={`Hi ${p.fullName}, `} size="" />
            {can('PAYMENT_COLLECT') && a && <Link to={`/payments/collect?residentId=${p.id}`} className="btn btn-soft"><Icon className="bi bi-cash-coin me-1" />Collect</Link>}
            {canManage && <Link to={`/residents/${p.id}/edit`} className="btn btn-light"><Icon className="bi bi-pencil me-1" />Edit</Link>}
            <button type="button" className="btn btn-light" onClick={() => window.print()}><Icon className="bi bi-printer me-1" />Print admission record</button>
          </div>
        </div>
      </header>

      <div className="row g-3 mb-3">
        <div className="col-6 col-lg-3"><SummaryCard label="Outstanding" value={money(b.outstanding)} icon="bi-wallet2" tone={Number(b.outstanding) > 0 ? 'orange' : 'green'} /></div>
        <div className="col-6 col-lg-3"><SummaryCard label="Overdue" value={money(b.overdue)} icon="bi-exclamation-octagon" tone={overdue ? 'red' : 'gray'} hint={overdue ? 'Past due date' : 'Nothing overdue'} /></div>
        <div className="col-6 col-lg-3"><SummaryCard label="Advance / credit" value={money(b.credit)} icon="bi-piggy-bank" tone="blue" /></div>
        <div className="col-6 col-lg-3"><SummaryCard label="Deposit held" value={money(b.depositHeld)} icon="bi-safe" tone="purple" /></div>
      </div>

      <ul className="nav nav-pills gap-1 mb-3 flex-nowrap overflow-auto no-print" role="tablist" style={{ paddingBottom: 2 }}>
        {tabs.map((t) => (
          <li className="nav-item" key={t.key} role="presentation">
            <button type="button" role="tab" aria-selected={tab === t.key} className={`nav-link text-nowrap ${tab === t.key ? 'active' : ''}`} onClick={() => setTab(t.key)}>
              <Icon className={`bi ${t.icon} me-1`} aria-hidden="true" />{t.label}
            </button>
          </li>
        ))}
      </ul>

      {/* Printing always shows overview + stay (the admission record), regardless of the open tab. */}
      <div className={tab === 'overview' ? '' : 'd-none d-print-block'}>
        <Overview p={p} canManage={canManage} consentBusy={consentBusy} onConsent={(v) => setConsentAsk(v)} />
      </div>
      <div className={tab === 'stay' ? 'mt-0' : 'd-none d-print-block mt-3'}>
        <StayTab profile={p} onChanged={() => load(true)} />
      </div>
      <div className="no-print">
        {tab === 'payments' && <PaymentsTab residentId={p.id} />}
        {tab === 'messages' && <MessagesTab resident={p} />}
        {tab === 'documents' && <DocumentsTab resident={p} onChanged={() => load(true)} />}
      </div>

      <div className="d-none d-print-block small text-muted-hh mt-4">Printed on {dateTime(new Date())}</div>

      <ConfirmDialog show={consentAsk !== null} title={consentAsk ? 'Record WhatsApp consent' : 'Opt out of WhatsApp'}
        confirmLabel={consentAsk ? 'Resident agrees' : 'Opt out'} variant={consentAsk ? 'primary' : 'danger'}
        message={consentAsk
          ? `Confirm that ${p.fullName} has agreed to receive WhatsApp messages (admission confirmation, rent reminders, receipts and announcements) on ${p.mobile}.`
          : `${p.fullName} will stop receiving all WhatsApp messages from the hostel.`}
        onConfirm={() => saveConsent(consentAsk)} onClose={() => setConsentAsk(null)} />
    </div>
  );
}
