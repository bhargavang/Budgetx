import { Navigate, Route, Routes } from 'react-router-dom';
import { useAuth } from './AuthContext';
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

export default function App() {
  return (
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
  );
}
