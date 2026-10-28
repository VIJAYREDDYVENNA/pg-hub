import { useEffect, useState } from 'react';
import DataTable from '../common/DataTable';
import Pagination from '../common/Pagination';
import StatusBadge from '../common/StatusBadge';
import { EmptyState, ErrorState, LoadingState } from '../common/States';
import useApi from '../../hooks/useApi';
import { whatsappApi } from '../../services/endpoints';
import { errorMessage } from '../../services/apiClient';
import { useAuth } from '../../context/AuthContext';
import { useToast } from '../../context/ToastContext';
import { MESSAGE_STATUS } from '../../utils/status';
import { dateTime, label } from '../../utils/format';
import Icon from '../common/Icon';

/** WhatsApp message log for a resident plus a send form (template or custom text). */
export default function MessagesTab({ resident }) {
  const { can } = useAuth();
  const { notify } = useToast();
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(15);
  const msgs = useApi(() => whatsappApi.messages({ residentId: resident.id, page, size }), [resident.id, page, size]);
  const canSend = can('NOTIFICATION_SEND');
  const [templates, setTemplates] = useState([]);
  const [templateCode, setTemplateCode] = useState('');
  const [customMessage, setCustom] = useState('');
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState(null);

  useEffect(() => {
    if (!canSend) return undefined;
    let alive = true;
    whatsappApi.templates()
      .then((list) => {
        if (!alive) return;
        const active = (list || []).filter((t) => t.active !== false);
        setTemplates(active);
        setTemplateCode((cur) => cur || (active.some((t) => t.code === 'CUSTOM') ? 'CUSTOM' : active[0]?.code || 'CUSTOM'));
      })
      .catch(() => { if (alive) { setTemplates([]); setTemplateCode('CUSTOM'); } });
    return () => { alive = false; };
  }, [canSend]);

  const selected = templates.find((t) => t.code === templateCode);
  const isCustom = templateCode === 'CUSTOM';

  const send = async (e) => {
    e.preventDefault();
    if (isCustom && !customMessage.trim()) { setErr('Enter the message to send.'); return; }
    setBusy(true); setErr(null);
    try {
      const m = await whatsappApi.send({ residentId: resident.id, templateCode, customMessage: isCustom ? customMessage.trim() : null });
      if (m.status === 'SKIPPED_NO_CONSENT') notify('Not sent: the resident has not opted in to WhatsApp messages.', 'warning');
      else if (m.status === 'FAILED') notify(`Message failed: ${m.errorMessage || 'provider error'}`, 'danger');
      else notify(m.simulated ? 'Message recorded (simulated – WhatsApp is in development mode).' : 'Message sent.');
      setCustom('');
      if (page !== 0) setPage(0); else msgs.reload();
    } catch (ex) {
      setErr(errorMessage(ex));
    } finally { setBusy(false); }
  };

  const options = templates.some((t) => t.code === 'CUSTOM') ? templates : [...templates, { code: 'CUSTOM', name: 'Custom message' }];

  const columns = [
    { key: 'templateCode', label: 'Message', required: true, width: '50%', sortValue: (m) => m.templateCode || '', render: (m) => (
      <div className="min-w-0">
        <span className="hh-cell-primary">{label(m.templateCode)}</span>
        <span className="hh-cell-sub" style={{ whiteSpace: 'pre-wrap' }}>{m.body}</span>
        {m.errorMessage && <span className="hh-cell-sub text-danger"><Icon className="bi bi-x-octagon me-1" />{m.errorMessage}</span>}
      </div>
    ) },
    { key: 'toPhone', label: 'To', hideOnMobile: true, sortValue: (m) => m.toPhone || '',
      render: (m) => (m.toPhone ? <span className="hh-contact-line"><Icon className="bi bi-telephone" />{m.toPhone}</span> : <span className="hh-dash">—</span>) },
    { key: 'status', label: 'Status', align: 'center', sortValue: (m) => m.status, render: (m) => (
      <div className="d-flex flex-wrap gap-1 justify-content-end justify-content-md-center">
        <StatusBadge map={MESSAGE_STATUS} value={m.status} />
        {m.simulated && <span className="badge hh-badge hh-badge-simulated"><Icon className="bi bi-cone-striped me-1" />SIMULATED</span>}
      </div>
    ) },
    { key: 'createdAt', label: 'Sent', sortValue: (m) => m.createdAt || '', render: (m) => dateTime(m.createdAt) },
  ];

  return (
    <>
      {canSend && (
        <section className="hh-card mb-3 no-print">
          <h2 className="hh-card-title mb-3"><Icon className="bi bi-whatsapp me-2 text-success" />Send a WhatsApp message</h2>
          {!resident.whatsappOptIn && (
            <div className="alert alert-warning py-2 small"><Icon className="bi bi-shield-exclamation me-1" />
              {resident.fullName} has not opted in to WhatsApp messages, so nothing will be delivered. Record their consent on the Overview tab first.</div>
          )}
          <form onSubmit={send}>
            {err && <div className="alert alert-danger py-2">{err}</div>}
            <div className="mb-3">
              <label className="form-label" htmlFor="wa-template">Template</label>
              <select id="wa-template" className="form-select" value={templateCode} onChange={(e) => setTemplateCode(e.target.value)}>
                {options.map((t) => <option key={t.code} value={t.code}>{t.name || label(t.code)}</option>)}
              </select>
              {selected && !isCustom && selected.body && <div className="form-text" style={{ whiteSpace: 'pre-wrap' }}>Preview: {selected.body}</div>}
            </div>
            {isCustom && (
              <div className="mb-3">
                <label className="form-label" htmlFor="wa-custom">Message</label>
                <textarea id="wa-custom" className="form-control" rows={3} maxLength={1000} value={customMessage} onChange={(e) => setCustom(e.target.value)} />
                <div className="form-text">{customMessage.length}/1000</div>
              </div>
            )}
            <button type="submit" className="btn btn-whatsapp" disabled={busy || !templateCode}>
              {busy ? <span className="spinner-border spinner-border-sm me-2" /> : <Icon className="bi bi-send me-1" />}Send
            </button>
          </form>
        </section>
      )}

      <section className="mb-3" aria-label="Message history">
        {msgs.error ? <ErrorState compact error={msgs.error} onRetry={msgs.reload} /> : (
          <DataTable tableId="resident-messages" columns={columns} rows={msgs.data?.content || []} serial
            serialStart={(msgs.data?.page || 0) * (msgs.data?.size || size)}
            toolbar={<>
              <span className="fw-semibold"><Icon className="bi bi-chat-dots me-2 text-primary-hh" aria-hidden="true" />Message history</span>
            </>}
            empty={msgs.loading && !msgs.data ? <LoadingState compact /> : <EmptyState icon="bi-chat" title="No messages yet" />}
            footer={msgs.data && <Pagination page={msgs.data.page} totalPages={msgs.data.totalPages} totalElements={msgs.data.totalElements} size={msgs.data.size}
              noun="messages" sizes={[15, 30, 50]} onChange={setPage} onSizeChange={(s) => { setSize(s); setPage(0); }} />} />
        )}
      </section>
    </>
  );
}