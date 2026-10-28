import { useEffect, useState } from 'react';
import PageHeader from '../components/common/PageHeader';
import { useNavigate } from 'react-router-dom';
import useApi from '../hooks/useApi';
import { notificationApi } from '../services/endpoints';
import { errorMessage } from '../services/apiClient';
import { useToast } from '../context/ToastContext';
import { useHostels } from '../context/HostelContext';
import { EmptyState, ErrorState, LoadingState } from '../components/common/States';
import Pagination from '../components/common/Pagination';
import HostelFilter from '../components/common/HostelFilter';
import { NOTIF_ICONS } from '../components/layout/NotificationPanel';
import { dateTime, fromNow, label } from '../utils/format';
import Icon from '../components/common/Icon';

export default function NotificationsPage() {
  const navigate = useNavigate();
  const { notify } = useToast();
  const { selectedHostelId } = useHostels();
  const [unreadOnly, setUnreadOnly] = useState(false);
  const [type, setType] = useState('ALL');
  const [hostelId, setHostelId] = useState(selectedHostelId);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(10);
  const { data, setData, loading, error, reload } = useApi(
    () => notificationApi.list({ unreadOnly, type, hostelId, page, size }), [unreadOnly, type, hostelId, page, size]);

  useEffect(() => { document.title = 'Notifications · HostelHub'; }, []);
  useEffect(() => { setPage(0); }, [unreadOnly, type, hostelId]);

  const open = async (n) => {
    if (!n.read) {
      try {
        await notificationApi.read(n.id);
        setData((d) => (d ? { ...d, content: d.content.map((x) => (x.id === n.id ? { ...x, read: true } : x)) } : d));
      } catch (e) { /* still navigate */ }
    }
    if (n.link) navigate(n.link);
  };
  const readAll = async () => {
    try {
      const r = await notificationApi.readAll();
      notify(`${r?.updated ?? 0} notification(s) marked as read`);
      reload();
    } catch (e) { notify(errorMessage(e), 'danger'); }
  };
  const rows = data?.content || [];

  return (
    <>
      <PageHeader title="Notifications" icon="bi-bell" subtitle="Alerts about payments, checkouts, admissions, enquiries and maintenance."
        actions={<button type="button" className="btn btn-light" onClick={readAll}><Icon className="bi bi-check2-all me-1" />Mark all read</button>} />
      <div className="row g-3">
        <div className="col-12">
          {error ? <ErrorState error={error} onRetry={reload} /> : (
            <div className="hh-table-card" style={{ opacity: loading && data ? 0.6 : 1, transition: 'opacity .15s' }}>
              <div className="hh-table-toolbar" role="search">
                <div style={{ minWidth: 170 }}><HostelFilter value={hostelId} onChange={setHostelId} id="notif-hostel" /></div>
                <select className="form-select w-auto" aria-label="Type" value={type} onChange={(e) => setType(e.target.value)}>
                  <option value="ALL">All types</option>
                  {Object.keys(NOTIF_ICONS).map((t) => <option key={t} value={t}>{label(t)}</option>)}
                </select>
                <div className="form-check form-switch ms-1 mb-0">
                  <input className="form-check-input" type="checkbox" role="switch" id="unread-only" checked={unreadOnly} onChange={(e) => setUnreadOnly(e.target.checked)} />
                  <label className="form-check-label small" htmlFor="unread-only">Unread only</label>
                </div>
              </div>
              {loading && !data ? <LoadingState /> : rows.length === 0 ? (
                <EmptyState icon="bi-bell-slash" title={unreadOnly ? 'No unread notifications' : 'No notifications'} message="You are all caught up." />
              ) : (
                <ul className="list-unstyled mb-0">
                  {rows.map((n) => {
                    const [icon, tone] = NOTIF_ICONS[n.type] || ['bi-bell', 'tone-orange'];
                    return (
                      <li key={n.id}>
                        <button type="button" className={`hh-feed-item w-100 text-start bg-transparent border-0 border-bottom px-3 ${n.read ? 'opacity-75' : ''}`}
                          onClick={() => open(n)} aria-label={`${n.read ? '' : 'Unread: '}${n.title}`}>
                          <span className={`hh-feed-icon ${tone}`}><span className="hh-summary-icon" style={{ width: 36, height: 36, fontSize: '1rem' }}><Icon className={`bi ${icon}`} /></span></span>
                          <span className="min-w-0 flex-grow-1">
                            <span className="d-block hh-cell-primary">
                              {!n.read && <span className="badge bg-danger rounded-pill me-1" style={{ fontSize: '.6rem' }}>NEW</span>}{n.title}
                            </span>
                            <span className="hh-cell-sub">{n.message}</span>
                            <span className="hh-cell-sub" title={dateTime(n.createdAt)}>{label(n.type)} · {fromNow(n.createdAt)}</span>
                          </span>
                          {n.link && <Icon className="bi bi-chevron-right text-muted-hh" aria-hidden="true" />}
                        </button>
                      </li>
                    );
                  })}
                </ul>
              )}
              {data && <Pagination page={data.page || 0} totalPages={data.totalPages} totalElements={data.totalElements} size={data.size}
                noun="notifications" onChange={setPage} onSizeChange={(s) => { setSize(s); setPage(0); }} />}
            </div>
          )}
        </div>
      </div>
    </>
  );
}