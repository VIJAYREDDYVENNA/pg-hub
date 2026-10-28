import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { dashboardApi, notificationApi } from '../../services/endpoints';
import useDebounce from '../../hooks/useDebounce';
import useTheme from '../../hooks/useTheme';
import Avatar from '../common/Avatar';
import NotificationPanel from './NotificationPanel';
import { label } from '../../utils/format';
import Icon from '../common/Icon';

const HIT_ICONS = { HOSTEL: 'bi-houses', ROOM: 'bi-door-closed', RESIDENT: 'bi-person', PAYMENT: 'bi-cash-coin', INVOICE: 'bi-receipt' };

function GlobalSearch() {
  const [q, setQ] = useState('');
  const [hits, setHits] = useState([]);
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(-1);
  const debounced = useDebounce(q, 250);
  const navigate = useNavigate();
  const ref = useRef(null);

  useEffect(() => {
    if (debounced.trim().length < 2) { setHits([]); return undefined; }
    let cancelled = false;
    dashboardApi.search(debounced.trim()).then((h) => { if (!cancelled) { setHits(h); setActive(-1); } }).catch(() => setHits([]));
    return () => { cancelled = true; };
  }, [debounced]);

  useEffect(() => {
    const close = (e) => { if (ref.current && !ref.current.contains(e.target)) setOpen(false); };
    document.addEventListener('mousedown', close);
    return () => document.removeEventListener('mousedown', close);
  }, []);

  const go = (hit) => { setOpen(false); setQ(''); navigate(hit.link); };
  const onKey = (e) => {
    if (e.key === 'ArrowDown') { e.preventDefault(); setActive((a) => Math.min(a + 1, hits.length - 1)); }
    if (e.key === 'ArrowUp') { e.preventDefault(); setActive((a) => Math.max(a - 1, 0)); }
    if (e.key === 'Enter' && hits[active]) go(hits[active]);
    if (e.key === 'Escape') setOpen(false);
  };

  return (
    <div className="hh-search" ref={ref}>
      <Icon className="bi bi-search" aria-hidden="true" />
      <input type="search" className="form-control" placeholder="Search hostel, room, resident, payment..." aria-label="Global search"
        value={q} onChange={(e) => { setQ(e.target.value); setOpen(true); }} onFocus={() => setOpen(true)} onKeyDown={onKey} />
      {open && q.trim().length >= 2 && (
        <div className="hh-search-results" role="listbox">
          {hits.length === 0 ? <div className="p-3 small text-muted-hh">No matches for “{q}”</div> : hits.map((h, i) => (
            <a key={`${h.type}-${h.id}`} href={h.link} className={i === active ? 'active' : ''} role="option" aria-selected={i === active}
              onClick={(e) => { e.preventDefault(); go(h); }}>
              <Icon className={`bi ${HIT_ICONS[h.type] || 'bi-search'} text-primary-hh`} />
              <span className="min-w-0"><span className="d-block fw-semibold text-truncate">{h.title}</span>
                <span className="d-block small text-muted-hh text-truncate">{h.subtitle}</span></span>
            </a>
          ))}
        </div>
      )}
    </div>
  );
}

export default function Topbar({ onMenu }) {
  const { user, logout, isOwner } = useAuth();
  const { theme, toggle } = useTheme();
  const [menu, setMenu] = useState(false);
  const [notifOpen, setNotifOpen] = useState(false);
  const [unread, setUnread] = useState(0);
  const navigate = useNavigate();
  const menuRef = useRef(null);

  useEffect(() => {
    let alive = true;
    const load = () => notificationApi.unreadCount().then((r) => alive && setUnread(r.count)).catch(() => {});
    load();
    const t = setInterval(load, 60000);
    return () => { alive = false; clearInterval(t); };
  }, []);

  useEffect(() => {
    const close = (e) => { if (menuRef.current && !menuRef.current.contains(e.target)) setMenu(false); };
    document.addEventListener('mousedown', close);
    return () => document.removeEventListener('mousedown', close);
  }, []);

  const role = user?.roles?.[0];

  return (
    <header className="hh-topbar">
      <button type="button" className="btn border-0 p-1 fs-4 lh-1 text-reset" aria-label="Open menu" onClick={onMenu}>
        <Icon className="bi bi-list d-lg-none" /><Icon className="bi bi-text-left d-none d-lg-inline opacity-75" />
      </button>
      <GlobalSearch />
      <div className="ms-auto d-flex align-items-center gap-2 gap-md-3">
        <button type="button" className={`hh-icon-btn ${theme === 'light' ? 'sun' : ''}`} onClick={toggle}
          aria-label={theme === 'light' ? 'Switch to dark theme' : 'Switch to light theme'} title="Toggle light / dark theme">
          <Icon className={`bi ${theme === 'light' ? 'bi-brightness-high-fill' : 'bi-moon-stars-fill'} fs-5`} />
        </button>
        <button type="button" className="hh-icon-btn" aria-label={`Notifications (${unread} unread)`} onClick={() => setNotifOpen(true)}>
          <Icon className="bi bi-bell-fill fs-5" style={{ color: '#5B6577' }} />{unread > 0 && <span className="hh-dot">{unread > 99 ? '99+' : unread}</span>}
        </button>
        <div className="position-relative" ref={menuRef}>
          <button type="button" className="hh-profile-chip" aria-haspopup="menu" aria-expanded={menu} onClick={() => setMenu((m) => !m)}>
            <Avatar name={user?.fullName} />
            <span className="who text-start lh-sm d-none d-md-block">
              <span className="d-block fw-semibold" style={{ fontSize: '.92rem' }}>{user?.fullName}</span>
              <span className="d-block text-muted-hh" style={{ fontSize: '.75rem' }}>{label(role)}</span>
            </span>
            <Icon className="bi bi-chevron-down small d-none d-md-inline" />
          </button>
          {menu && (
            <div className="hh-dropdown" role="menu">
              <div className="px-2 py-1 small text-muted-hh">{user?.ownerName}<br />{user?.email}</div>
              <hr className="my-1" />
              <Link to="/profile" role="menuitem" onClick={() => setMenu(false)}><Icon className="bi bi-person" />My profile</Link>
              {isOwner && <Link to="/managers" role="menuitem" onClick={() => setMenu(false)}><Icon className="bi bi-person-gear" />Managers</Link>}
              <Link to="/settings" role="menuitem" onClick={() => setMenu(false)}><Icon className="bi bi-gear" />Settings</Link>
              <button type="button" role="menuitem" onClick={async () => { await logout(); navigate('/login'); }}>
                <Icon className="bi bi-box-arrow-right" />Sign out
              </button>
            </div>
          )}
        </div>
      </div>
      <NotificationPanel show={notifOpen} onClose={() => setNotifOpen(false)} onCountChange={setUnread} />
    </header>
  );
}
