import { NavLink, Outlet } from 'react-router-dom';
import { useAuth } from '../AuthContext';

const NAV = [
  { to: '/', label: 'Home', icon: 'M3 11l9-7 9 7v9a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z', end: true },
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

export default function Layout() {
  const { user, logout } = useAuth();
  return (
    <div className="shell">
      <aside className="sidebar">
        <div className="brand"><img src="/icon.svg" alt="" width="32" height="32" /> BudgetX</div>
        <nav>
          {NAV.map((n) => (
            <NavLink key={n.to} to={n.to} end={n.end} className="side-link">
              <Icon d={n.icon} /> {n.label}
            </NavLink>
          ))}
        </nav>
        <div className="sidebar-foot">
          <span className="muted small">{user?.email}</span>
          <button className="btn btn-ghost" onClick={logout}>Sign out</button>
        </div>
      </aside>

      <header className="topbar">
        <div className="brand"><img src="/icon.svg" alt="" width="28" height="28" /> BudgetX</div>
      </header>

      <main className="content">
        <Outlet />
      </main>

      <nav className="tabbar">
        {NAV.map((n) => (
          <NavLink key={n.to} to={n.to} end={n.end} className="tab-link">
            <Icon d={n.icon} />
            <span>{n.label}</span>
          </NavLink>
        ))}
      </nav>
    </div>
  );
}
