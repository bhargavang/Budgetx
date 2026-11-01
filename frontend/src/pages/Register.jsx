import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../AuthContext';
import { Alert, Field } from '../components/ui';
import { label } from '../format';

const GENDERS = ['MALE', 'FEMALE', 'OTHER', 'PREFER_NOT_TO_SAY'];
const RISKS = ['CONSERVATIVE', 'MODERATE', 'AGGRESSIVE'];

export default function Register() {
  const { register } = useAuth();
  const [form, setForm] = useState({
    fullName: '', email: '', password: '', phone: '', dateOfBirth: '',
    gender: '', occupation: '', monthlySalary: '', riskProfile: 'MODERATE',
  });
  const [errors, setErrors] = useState({});
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value });

  const submit = async (e) => {
    e.preventDefault();
    setBusy(true);
    setError('');
    setErrors({});
    const body = Object.fromEntries(Object.entries(form).map(([k, v]) => [k, v === '' ? null : v]));
    body.email = body.email?.trim();
    try {
      await register(body);
    } catch (err) {
      setErrors(err.fieldErrors);
      setError(Object.keys(err.fieldErrors).length ? 'Please fix the highlighted fields.' : err.message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="auth-page">
      <form className="card auth-card wide" onSubmit={submit}>
        <div className="auth-brand"><img src="/icon.svg" alt="" width="48" height="48" /><h1>Create account</h1></div>
        <p className="muted center">Your salary and risk profile shape your budget and advice.</p>
        <Alert>{error}</Alert>
        <div className="grid-2">
          <Field label="Full name" error={errors.fullName}>
            <input required maxLength={100} autoComplete="name" value={form.fullName} onChange={set('fullName')} />
          </Field>
          <Field label="Email" error={errors.email}>
            <input type="email" required autoComplete="email" value={form.email} onChange={set('email')} />
          </Field>
          <Field label="Password" error={errors.password} hint="At least 8 characters">
            <input type="password" required minLength={8} maxLength={72} autoComplete="new-password"
              value={form.password} onChange={set('password')} />
          </Field>
          <Field label="Monthly salary (₹)" error={errors.monthlySalary}>
            <input type="number" inputMode="decimal" required min="0" step="0.01" value={form.monthlySalary}
              onChange={set('monthlySalary')} />
          </Field>
          <Field label="Phone (optional)" error={errors.phone}>
            <input type="tel" autoComplete="tel" value={form.phone} onChange={set('phone')} placeholder="+919876543210" />
          </Field>
          <Field label="Date of birth (optional)" error={errors.dateOfBirth}>
            <input type="date" value={form.dateOfBirth} onChange={set('dateOfBirth')} />
          </Field>
          <Field label="Occupation (optional)" error={errors.occupation}>
            <input maxLength={100} value={form.occupation} onChange={set('occupation')} />
          </Field>
          <Field label="Gender (optional)" error={errors.gender}>
            <select value={form.gender} onChange={set('gender')}>
              <option value="">—</option>
              {GENDERS.map((g) => <option key={g} value={g}>{label(g)}</option>)}
            </select>
          </Field>
          <Field label="Risk appetite" error={errors.riskProfile}>
            <select value={form.riskProfile} onChange={set('riskProfile')}>
              {RISKS.map((r) => <option key={r} value={r}>{label(r)}</option>)}
            </select>
          </Field>
        </div>
        <button className="btn btn-primary btn-block" disabled={busy}>{busy ? 'Creating…' : 'Create account'}</button>
        <p className="center small">Already have an account? <Link to="/login">Sign in</Link></p>
      </form>
    </div>
  );
}
