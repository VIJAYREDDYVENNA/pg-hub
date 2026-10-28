import { useEffect, useRef, useState } from 'react';
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import useTheme from '../hooks/useTheme';
import Avatar from '../components/common/Avatar';
import Icon from '../components/common/Icon';

const NAV = [
  { to: '/super-admin/dashboard', icon: 'bi-speedometer2', label: 'Dashboard' },
  { to: '/super-admin/owners', icon: 'bi-people-fill', label: 'Owners' },
  { to: '/super-admin/plans', icon: 'bi-tags-fill', label: 'Subscription Plans' },
  { to: '/super-admin/hostels', icon: 'bi-houses', label: 'All Hostels' },
  { to: '/super-admin/reports', icon: 'bi-file-earmark-bar-graph', label: 'Platform Reports' },
  { to: '/super-admin/activity', icon: 'bi-clock-history', label: 'Activity Logs' },
  { to: '/super-admin/settings', icon: 'bi-gear-fill', label: 'Application Settings' },
];

/** Separate shell for the platform administrator: platform pages only, no hostel operations. */
export default function SuperAdminLayout() {
  const { user, logout } = useAuth();
  const { theme, toggle } = useTheme();
  const navigate = useNavigate();
  const location = useLocation();
  const [menuOpen, setMenuOpen] = useState(false);
  const [collapsed, setCollapsed] = useState(false);
  const [profileOpen, setProfileOpen] = useState(false);
  const profileRef = useRef(null);

  useEffect(() => { setMenuOpen(false); window.scrollTo(0, 0); }, [location.pathname]);
  useEffect(() => {
    const close = (e) => { if (profileRef.current && !profileRef.current.contains(e.target)) setProfileOpen(false); };
    document.addEventListener('mousedown', close);
    return () => document.removeEventListener('mousedown', close);
  }, []);

  const onMenu = () => (window.matchMedia('(min-width: 992px)').matches ? setCollapsed((c) => !c) : setMenuOpen(true));
  const signOut = async () => { await logout(); navigate('/super-admin/login', { replace: true }); };

  return (
    <div className={`hh-shell ${collapsed ? 'collapsed' : ''}`}>
      <aside className={`hh-sidebar ${menuOpen ? 'open' : ''}`} aria-label="Super admin navigation">
        <div className="hh-brand">
          <span className="hh-brand-logo"><Icon className="bi bi-shield-lock-fill" /></span>
          <span className="min-w-0">
            <span className="hh-brand-name d-block">HostelHub</span>
            <span className="hh-brand-tag d-block">Platform administration</span>
          </span>
          <button type="button" className="hh-close light ms-auto d-lg-none position-relative" style={{ zIndex: 2 }}
            aria-label="Close menu" onClick={() => setMenuOpen(false)}><Icon className="bi bi-x-lg" /></button>
        </div>
        <nav className="hh-nav">
          {NAV.map((i) => (
            <NavLink key={i.to} to={i.to}><Icon className={`bi ${i.icon}`} aria-hidden="true" />{i.label}</NavLink>
          ))}
          <div className="hh-nav-section">Session</div>
          <a href="#logout" onClick={(e) => { e.preventDefault(); signOut(); }}>
            <Icon className="bi bi-box-arrow-right" aria-hidden="true" />Logout
          </a>
        </nav>
      </aside>
      <div className={`hh-offcanvas-backdrop ${menuOpen ? 'open' : ''}`} onClick={() => setMenuOpen(false)} />
      <div className="hh-main">
        <header className="hh-topbar">
          <button type="button" className="btn border-0 p-1 fs-4 lh-1 text-reset" aria-label="Open menu" onClick={onMenu}>
            <Icon className="bi bi-list d-lg-none" /><Icon className="bi bi-text-left d-none d-lg-inline opacity-75" />
          </button>
          <span className="badge hh-badge hh-badge-warning d-none d-sm-inline-flex"><Icon className="bi bi-shield-lock-fill me-1" />Super Admin</span>
          <div className="ms-auto d-flex align-items-center gap-2 gap-md-3">
            <button type="button" className={`hh-icon-btn ${theme === 'light' ? 'sun' : ''}`} onClick={toggle}
              aria-label={theme === 'light' ? 'Switch to dark theme' : 'Switch to light theme'}>
              <Icon className={`bi ${theme === 'light' ? 'bi-brightness-high-fill' : 'bi-moon-stars-fill'} fs-5`} />
            </button>
            <div className="position-relative" ref={profileRef}>
              <button type="button" className="hh-profile-chip" aria-haspopup="menu" aria-expanded={profileOpen}
                onClick={() => setProfileOpen((o) => !o)}>
                <Avatar name={user?.fullName} />
                <span className="who text-start lh-sm d-none d-md-block">
                  <span className="d-block fw-semibold" style={{ fontSize: '.92rem' }}>{user?.fullName}</span>
                  <span className="d-block text-muted-hh" style={{ fontSize: '.75rem' }}>Super Admin</span>
                </span>
                <Icon className="bi bi-chevron-down small d-none d-md-inline" />
              </button>
              {profileOpen && (
                <div className="hh-dropdown" role="menu">
                  <div className="px-2 py-1 small text-muted-hh">{user?.email}</div>
                  <hr className="my-1" />
                  <Link to="/super-admin/profile" role="menuitem" onClick={() => setProfileOpen(false)}><Icon className="bi bi-person" />My profile</Link>
                  <button type="button" role="menuitem" onClick={signOut}><Icon className="bi bi-box-arrow-right" />Sign out</button>
                </div>
              )}
            </div>
          </div>
        </header>
        <main className="hh-content" id="main"><Outlet /></main>
      </div>
    </div>
  );
}
