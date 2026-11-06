import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../AuthContext';
import { ThemeToggle } from '../theme';
import { Alert, Field } from '../components/ui';

export default function Login() {
  const { login } = useAuth();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const submit = async (e) => {
    e.preventDefault();
    setBusy(true);
    setError('');
    try {
      await login(email.trim(), password);
    } catch (err) {
      setError(err.status === 401 ? 'Email or password is incorrect.' : err.message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="auth-page">
      <ThemeToggle className="auth-theme" />
      <form className="card auth-card" onSubmit={submit}>
        <div className="auth-brand"><img src="/icon.svg" alt="" width="48" height="48" /><h1>BudgetX</h1></div>
        <p className="muted center">Sign in to manage your budget</p>
        <Alert>{error}</Alert>
        <Field label="Email">
          <input type="email" autoComplete="email" required value={email} onChange={(e) => setEmail(e.target.value)} />
        </Field>
        <Field label="Password">
          <input type="password" autoComplete="current-password" required value={password}
            onChange={(e) => setPassword(e.target.value)} />
        </Field>
        <button className="btn btn-primary btn-block" disabled={busy}>{busy ? 'Signing in…' : 'Sign in'}</button>
        <p className="center small">New to BudgetX? <Link to="/register">Create an account</Link></p>
      </form>
    </div>
  );
}
