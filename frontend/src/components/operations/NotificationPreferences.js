import { useState } from 'react';
import useApi from '../../hooks/useApi';
import { notificationApi } from '../../services/endpoints';
import { errorMessage } from '../../services/apiClient';
import { useToast } from '../../context/ToastContext';
import { AsyncContent } from '../common/States';
import { NOTIF_ICONS } from '../layout/NotificationPanel';
import { label } from '../../utils/format';
import Icon from '../common/Icon';

export default function NotificationPreferences() {
  const { notify } = useToast();
  const { data, setData, loading, error, reload } = useApi(() => notificationApi.preferences(), []);
  const [saving, setSaving] = useState(null);
  const toggle = async (p) => {
    setSaving(p.type);
    try {
      setData(await notificationApi.updatePreferences([{ type: p.type, inApp: !p.inApp }]));
      notify(`${label(p.type)} notifications ${p.inApp ? 'turned off' : 'turned on'}`);
    } catch (e) { notify(errorMessage(e), 'danger'); } finally { setSaving(null); }
  };
  return (
    <section className="hh-card" aria-labelledby="pref-title">
      <div className="hh-card-header">
        <h2 className="hh-card-title" id="pref-title"><Icon className="bi bi-sliders me-2" />Notification preferences</h2>
      </div>
      <p className="small text-muted-hh">Choose which in-app notifications you receive. This affects only your account.</p>
      <AsyncContent loading={loading && !data} error={error} onRetry={reload} compact empty={data && data.length === 0}
        emptyProps={{ icon: 'bi-sliders', title: 'No notification types' }}>
        <ul className="list-unstyled mb-0">
          {(data || []).map((p) => {
            const [icon] = NOTIF_ICONS[p.type] || ['bi-bell'];
            return (
              <li key={p.type} className="d-flex align-items-center justify-content-between py-2 border-bottom gap-2">
                <label className="d-flex align-items-center gap-2 mb-0 flex-grow-1" htmlFor={`pref-${p.type}`}>
                  <Icon className={`bi ${icon} text-primary-hh`} aria-hidden="true" />{label(p.type)}
                </label>
                <div className="form-check form-switch mb-0">
                  {saving === p.type && <span className="spinner-border spinner-border-sm me-2" />}
                  <input id={`pref-${p.type}`} className="form-check-input" type="checkbox" role="switch" checked={p.inApp}
                    disabled={!!saving} onChange={() => toggle(p)} style={{ width: '2.6em', height: '1.4em' }} />
                </div>
              </li>
            );
          })}
        </ul>
      </AsyncContent>
    </section>
  );
}
