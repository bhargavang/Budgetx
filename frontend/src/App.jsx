import { Navigate, Route, Routes } from 'react-router-dom';
import { useAuth } from './AuthContext';
import { DEMO } from './api';
import Layout from './components/Layout';
import Login from './pages/Login';
import Register from './pages/Register';
import Dashboard from './pages/Dashboard';
import Emis from './pages/Emis';
import Expenses from './pages/Expenses';
import Today from './pages/Today';
import Profile from './pages/Profile';
import Guidance from './pages/Guidance';

function RequireAuth({ children }) {
  const { signedIn } = useAuth();
  return signedIn ? children : <Navigate to="/login" replace />;
}

function GuestOnly({ children }) {
  const { signedIn } = useAuth();
  return signedIn ? <Navigate to="/" replace /> : children;
}

// Shown on demo builds only: says the data is sample data kept on this device, and offers a reset.
function DemoBanner() {
  if (!DEMO) return null;
  const reset = async () => {
    if (!window.confirm('Reset the demo to its sample data? Your changes on this device will be lost.')) return;
    const { resetDemo } = await import('./demo/backend.js');
    resetDemo();
    window.location.reload();
  };
  return (
    <div className="demo-banner" role="note">
      <span><strong>Demo</strong> · sample data, saved only on this device<span className="demo-more">. Sign in with any email and password</span></span>
      <button type="button" onClick={reset}>Reset</button>
    </div>
  );
}

export default function App() {
  return (
    <>
    <DemoBanner />
    <Routes>
      <Route path="/login" element={<GuestOnly><Login /></GuestOnly>} />
      <Route path="/register" element={<GuestOnly><Register /></GuestOnly>} />
      <Route element={<RequireAuth><Layout /></RequireAuth>}>
        <Route index element={<Dashboard />} />
        <Route path="today" element={<Today />} />
        <Route path="expenses" element={<Expenses />} />
        <Route path="emis" element={<Emis />} />
        <Route path="profile" element={<Profile />} />
        <Route path="guidance" element={<Guidance />} />
        <Route path="insights" element={<Navigate to="/guidance" replace />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
    </>
  );
}
