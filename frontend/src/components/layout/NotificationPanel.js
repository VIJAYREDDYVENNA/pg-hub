import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import Drawer from '../common/Drawer';
import { notificationApi } from '../../services/endpoints';
import { AsyncContent } from '../common/States';
import { fromNow } from '../../utils/format';
import Icon from '../common/Icon';

export const NOTIF_ICONS = {
  PAYMENT_DUE: ['bi-calendar-event', 'tone-yellow'], PAYMENT_OVERDUE: ['bi-exclamation-circle', 'tone-red'],
  PAYMENT_RECEIVED: ['bi-cash-coin', 'tone-green'], UPCOMING_CHECKOUT: ['bi-box-arrow-right', 'tone-yellow'],
  NEW_ADMISSION: ['bi-person-plus', 'tone-green'], CHECKOUT: ['bi-door-open', 'tone-gray'], MAINTENANCE: ['bi-tools', 'tone-gray'],
  NEW_ENQUIRY: ['bi-chat-dots', 'tone-blue'], ENQUIRY_FOLLOW_UP: ['bi-telephone-outbound', 'tone-blue'], ANNOUNCEMENT: ['bi-megaphone', 'tone-orange'],
};

export default function NotificationPanel({ show, onClose, onCountChange }) {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const navigate = useNavigate();

  const load = async () => {
    setLoading(true); setError(null);
    try { setData(await notificationApi.list({ size: 30 })); } catch (e) { setError(e); } finally { setLoading(false); }
  };
  useEffect(() => { if (show) load(); }, [show]);

  const open = async (n) => {
    if (!n.read) {
      await notificationApi.read(n.id).catch(() => {});
      notificationApi.unreadCount().then((r) => onCountChange(r.count)).catch(() => {});
    }
    onClose();
    if (n.link) navigate(n.link);
  };
  const readAll = async () => { await notificationApi.readAll(); onCountChange(0); load(); };

  return (
    <Drawer show={show} title="Notifications" onClose={onClose} width={440}
      footer={<>
        <button type="button" className="btn btn-light" onClick={readAll}><Icon className="bi bi-check2-all me-1" />Mark all read</button>
        <button type="button" className="btn btn-soft ms-auto" onClick={() => { onClose(); navigate('/notifications'); }}>View all</button>
      </>}>
      <AsyncContent loading={loading} error={error} onRetry={load} empty={data && data.content.length === 0}
        emptyProps={{ icon: 'bi-bell-slash', title: 'No notifications', message: 'You are all caught up.' }}>
        {data?.content.map((n) => {
          const [icon, tone] = NOTIF_ICONS[n.type] || ['bi-bell', 'tone-orange'];
          return (
            <button type="button" key={n.id} className={`hh-feed-item w-100 text-start bg-transparent border-0 border-bottom ${n.read ? 'opacity-75' : ''}`} onClick={() => open(n)}>
              <span className={`hh-feed-icon ${tone}`}><span className="hh-summary-icon" style={{ width: 36, height: 36, fontSize: '1rem' }}><Icon className={`bi ${icon}`} /></span></span>
              <span className="min-w-0 flex-grow-1">
                <span className="d-block fw-semibold">{!n.read && <span className="badge bg-danger rounded-pill me-1" style={{ fontSize: '.55rem' }}>NEW</span>}{n.title}</span>
                <span className="d-block small text-muted-hh">{n.message}</span>
                <span className="d-block small text-muted-hh">{fromNow(n.createdAt)}</span>
              </span>
            </button>
          );
        })}
      </AsyncContent>
    </Drawer>
  );
}
