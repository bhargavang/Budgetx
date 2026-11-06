import { NavLink, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '../AuthContext';
import { ThemePicker, ThemeToggle } from '../theme';
import { PayProvider, usePay } from './PayFlow';

const NAV = [
  { to: '/', label: 'Home', icon: 'M3 11l9-7 9 7v9a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z', end: true },
  { to: '/today', label: 'Today', icon: 'M4 6h16v14H4zM4 10h16M8 3v4M16 3v4M9 15h2' },
  { to: '/expenses', label: 'Expenses', icon: 'M4 5h16v14H4zM4 9h16M8 14h4' },
  { to: '/emis', label: 'EMIs', icon: 'M3 21h18M5 21V10l7-5 7 5v11M9 21v-6h6v6' },
  { to: '/guidance', label: 'Guidance', icon: 'M4 19V9M10 19V5M16 19v-7M22 19H2' },
  { to: '/profile', label: 'Profile', icon: 'M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM4 21a8 8 0 0 1 16 0' },
];

function Icon({ d }) {
  return (
    <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" strokeWidth="1.8"
      strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d={d} />
    </svg>
  );
}

const PAY_ICON = 'M3 7h18v12H3zM3 11h18M7 15h3M16 4l2 3M8 4 6 7';

function PayButton({ className = '' }) {
  const { openPay } = usePay();
  return (
    <button className={`btn btn-primary pay-btn ${className}`} onClick={() => openPay()}>
      <Icon d={PAY_ICON} /> Pay
    </button>
  );
}

export default function Layout() {
  return (
    <PayProvider>
      <Shell />
    </PayProvider>
  );
}

function Shell() {
  const { user, logout } = useAuth();
  const { pathname } = useLocation();
  return (
    <div className="shell">
      <aside className="sidebar">
        <div className="brand"><img src="/icon.svg" alt="" width="32" height="32" /> BudgetX</div>
        <PayButton className="btn-block" />
        <nav>
          {NAV.map((n) => (
            <NavLink key={n.to} to={n.to} end={n.end} className="side-link">
              <Icon d={n.icon} /> {n.label}
            </NavLink>
          ))}
        </nav>
        <div className="sidebar-foot">
          <ThemePicker />
          <div className="who">
            <span className="avatar" aria-hidden="true">{(user?.fullName || user?.email || '?').charAt(0).toUpperCase()}</span>
            <span className="muted small who-email">{user?.email}</span>
          </div>
          <button className="btn btn-ghost" onClick={logout}>Sign out</button>
        </div>
      </aside>

      <header className="topbar">
        <div className="brand"><img src="/icon.svg" alt="" width="28" height="28" /> BudgetX</div>
        <div className="topbar-actions">
          <PayButton />
          <ThemeToggle />
        </div>
      </header>

      <main className="content">
        {/* Keyed on the path so each page fades in when you switch tabs. */}
        <div className="page" key={pathname}>
          <Outlet />
        </div>
      </main>

      <nav className="tabbar">
        {NAV.map((n) => (
          <NavLink key={n.to} to={n.to} end={n.end} className="tab-link">
            <span className="tab-icon"><Icon d={n.icon} /></span>
            <span>{n.label}</span>
          </NavLink>
        ))}
      </nav>
    </div>
  );
}
