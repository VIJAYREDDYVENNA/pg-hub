import { useEffect, useState } from 'react';
import { Outlet, useLocation } from 'react-router-dom';
import Sidebar from '../components/layout/Sidebar';
import Topbar from '../components/layout/Topbar';
import BottomNav from '../components/layout/BottomNav';
import { HostelProvider } from '../context/HostelContext';
import { SubscriptionBanner } from '../components/subscription/UsagePanel';

export default function DashboardLayout() {
  const [menuOpen, setMenuOpen] = useState(false);
  const [collapsed, setCollapsed] = useState(false);
  const location = useLocation();
  useEffect(() => { setMenuOpen(false); window.scrollTo(0, 0); }, [location.pathname]);
  // The menu button opens the off-canvas sidebar on small screens and collapses it on desktop.
  const onMenu = () => (window.matchMedia('(min-width: 992px)').matches ? setCollapsed((c) => !c) : setMenuOpen(true));
  return (
    <HostelProvider>
      <div className={`hh-shell ${collapsed ? 'collapsed' : ''}`}>
        <Sidebar open={menuOpen} onClose={() => setMenuOpen(false)} />
        <div className={`hh-offcanvas-backdrop ${menuOpen ? 'open' : ''}`} onClick={() => setMenuOpen(false)} />
        <div className="hh-main">
          <Topbar onMenu={onMenu} />
          <main className="hh-content" id="main">
            <SubscriptionBanner />
            <Outlet />
          </main>
        </div>
        <BottomNav onMore={() => setMenuOpen(true)} />
      </div>
    </HostelProvider>
  );
}
