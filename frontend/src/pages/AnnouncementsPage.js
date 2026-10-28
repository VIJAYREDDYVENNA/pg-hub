import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import useApi from '../hooks/useApi';
import { announcementApi, whatsappApi } from '../services/endpoints';
import { errorMessage } from '../services/apiClient';
import { useAuth } from '../context/AuthContext';
import { useHostels } from '../context/HostelContext';
import { useToast } from '../context/ToastContext';
import PageHeader from '../components/common/PageHeader';
import { EmptyState, ErrorState, LoadingState } from '../components/common/States';
import DataTable from '../components/common/DataTable';
import Pagination from '../components/common/Pagination';
import FormField, { applyServerErrors } from '../components/common/FormField';
import SimulatedBadge from '../components/operations/SimulatedBadge';
import { dateTime, fromNow } from '../utils/format';
import Icon from '../components/common/Icon';

function AnnouncementForm({ waStatus, onCreated }) {
  const { isOwner } = useAuth();
  const { hostels, selectedHostelId } = useHostels();
  const { notify } = useToast();
  const [serverError, setServerError] = useState(null);
  const [result, setResult] = useState(null);
  const { register, handleSubmit, reset, watch, setError, formState: { errors, isSubmitting } } = useForm({
    defaultValues: { hostelId: selectedHostelId || (isOwner ? '' : hostels[0]?.id || ''), title: '', body: '', sendWhatsApp: false },
  });
  const sendWhatsApp = watch('sendWhatsApp');
  const simulated = !!waStatus?.simulated;

  const onSubmit = async (v) => {
    setServerError(null);
    setResult(null);
    try {
      const a = await announcementApi.create({
        hostelId: v.hostelId ? Number(v.hostelId) : null, title: v.title.trim(), body: v.body.trim(), sendWhatsApp: !!v.sendWhatsApp,
      });
      setResult({ ...a, simulated: a.sendWhatsapp && simulated });
      notify('Announcement published');
      reset({ hostelId: v.hostelId, title: '', body: '', sendWhatsApp: false });
      onCreated();
    } catch (e) {
      if (!applyServerErrors(e, setError)) setServerError(errorMessage(e, 'Could not publish the announcement.'));
    }
  };

  return (
    <form className="hh-card" onSubmit={handleSubmit(onSubmit)} noValidate aria-labelledby="ann-form-title">
      <h2 className="hh-card-title mb-3" id="ann-form-title"><Icon className="bi bi-megaphone me-2" />New announcement</h2>
      {serverError && <div className="alert alert-danger py-2">{serverError}</div>}
      {result && (
        <div className="alert alert-success py-2" role="status">
          <div className="fw-semibold"><Icon className="bi bi-check-circle me-1" />Published to {result.hostelName || 'the hostel'}</div>
          {result.sendWhatsapp ? (
            <div className="small mt-1 d-flex flex-wrap align-items-center gap-2">
              <span><Icon className="bi bi-whatsapp me-1" />{result.whatsappQueued ?? 0} WhatsApp message(s) {result.simulated ? 'simulated' : 'queued'}</span>
              <span>· {result.whatsappSkipped ?? 0} skipped (no consent)</span>
              <SimulatedBadge simulated={result.simulated} />
            </div>
          ) : <div className="small">In-app notification only (no WhatsApp).</div>}
        </div>
      )}
      <FormField label="Send to" name="hostelId" as="select" register={register} errors={errors}
        rules={isOwner ? {} : { required: 'Select a hostel' }}
        options={[...(isOwner ? [{ value: '', label: 'All hostels' }] : [{ value: '', label: 'Select hostel…' }]),
          ...hostels.map((h) => ({ value: h.id, label: h.name }))]} />
      <FormField label="Title" name="title" register={register} errors={errors}
        rules={{ required: 'Enter a title', maxLength: { value: 200, message: 'Too long' } }} />
      <FormField label="Message" name="body" as="textarea" register={register} errors={errors} rows={5}
        rules={{ required: 'Enter the message', maxLength: { value: 2000, message: 'Maximum 2000 characters' } }} />
      <div className="form-check mb-1">
        <input className="form-check-input" type="checkbox" id="f-sendWhatsApp" {...register('sendWhatsApp')} />
        <label className="form-check-label" htmlFor="f-sendWhatsApp"><Icon className="bi bi-whatsapp text-success me-1" />Also send on WhatsApp</label>
      </div>
      <div className="form-text mb-3">
        Sent only to active residents who opted in to WhatsApp; others are skipped.
        {simulated && <> <strong>Mock mode simulates</strong> the messages — nothing reaches WhatsApp.</>}
      </div>
      {sendWhatsApp && simulated && (
        <div className="alert alert-warning py-2 small"><Icon className="bi bi-cone-striped me-1" />Development mock mode: WhatsApp messages will be SIMULATED.</div>
      )}
      <button type="submit" className="btn btn-primary w-100" disabled={isSubmitting}>
        {isSubmitting && <span className="spinner-border spinner-border-sm me-2" />}<Icon className="bi bi-send me-1" />Publish
      </button>
    </form>
  );
}

export default function AnnouncementsPage() {
  const { can } = useAuth();
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(15);
  const { data, loading, error, reload } = useApi(() => announcementApi.list({ page, size }), [page, size]);
  const { data: waStatus } = useApi(() => whatsappApi.status(), []);
  const canSend = can('NOTIFICATION_SEND');
  useEffect(() => { document.title = 'Announcements · HostelHub'; }, []);
  const rows = data?.content || [];

  const columns = [
    { key: 'title', label: 'Announcement', required: true, width: '50%', sortValue: (a) => a.title, render: (a) => (
      <div className="min-w-0">
        <span className="hh-cell-primary">{a.title}</span>
        <span className="hh-cell-sub" style={{ whiteSpace: 'pre-wrap' }}>{a.body}</span>
      </div>
    ) },
    { key: 'hostel', label: 'Sent to', sortValue: (a) => a.hostelName || '', render: (a) => (
      <span className="badge hh-badge hh-badge-secondary"><Icon className="bi bi-building me-1" />{a.hostelName || 'All hostels'}</span>
    ) },
    { key: 'channel', label: 'Channel', align: 'center', hideOnMobile: true, sortValue: (a) => (a.sendWhatsapp ? 1 : 0), render: (a) => (a.sendWhatsapp
      ? <span className="badge hh-badge hh-badge-success" title="WhatsApp to opted-in residents"><Icon className="bi bi-whatsapp me-1" />In-app + WhatsApp</span>
      : <span className="badge hh-badge hh-badge-info"><Icon className="bi bi-bell me-1" />In-app</span>) },
    { key: 'createdAt', label: 'Posted', sortValue: (a) => a.createdAt || '', render: (a) => <span title={dateTime(a.createdAt)}>{fromNow(a.createdAt)}</span> },
  ];

  return (
    <>
      <PageHeader title="Announcements" icon="bi-megaphone" subtitle="Share notices with your team and residents." />
      <div className="row g-3">
        {canSend && <div className="col-lg-5 order-lg-2"><AnnouncementForm waStatus={waStatus} onCreated={() => (page === 0 ? reload() : setPage(0))} /></div>}
        <div className={canSend ? 'col-lg-7 order-lg-1' : 'col-12'}>
          {error ? <ErrorState error={error} onRetry={reload} /> : (
            <div style={{ opacity: loading && data ? 0.6 : 1, transition: 'opacity .15s' }}>
              <DataTable tableId="announcements" columns={columns} rows={rows} serial serialStart={(data?.page || 0) * (data?.size || size)}
                toolbar={<span className="fw-semibold"><Icon className="bi bi-megaphone me-2 text-primary-hh" aria-hidden="true" />Recent announcements</span>}
                empty={loading && !data ? <LoadingState /> : (
                  <EmptyState icon="bi-megaphone" title="No announcements yet" message={canSend ? 'Publish your first notice using the form.' : undefined} />
                )}
                footer={data && <Pagination page={data.page || 0} totalPages={data.totalPages} totalElements={data.totalElements} size={data.size}
                  noun="announcements" sizes={[15, 30, 50, 100]} onChange={setPage} onSizeChange={(s) => { setSize(s); setPage(0); }} />} />
            </div>
          )}
        </div>
      </div>
    </>
  );
}