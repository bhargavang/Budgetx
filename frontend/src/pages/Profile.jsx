import { useState } from 'react';
import { users } from '../api';
import { useAuth } from '../AuthContext';
import { Alert, Field } from '../components/ui';
import { label } from '../format';

const GENDERS = ['MALE', 'FEMALE', 'OTHER', 'PREFER_NOT_TO_SAY'];
const RISKS = ['CONSERVATIVE', 'MODERATE', 'AGGRESSIVE'];

function Details() {
  const { user, updateUser } = useAuth();
  const [form, setForm] = useState({
    fullName: user?.fullName || '', phone: user?.phone || '', dateOfBirth: user?.dateOfBirth || '',
    gender: user?.gender || '', occupation: user?.occupation || '',
    monthlySalary: user?.monthlySalary ?? '', riskProfile: user?.riskProfile || 'MODERATE',
  });
  const [errors, setErrors] = useState({});
  const [msg, setMsg] = useState({});
  const [busy, setBusy] = useState(false);
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value });

  const submit = async (e) => {
    e.preventDefault();
    setBusy(true);
    setErrors({});
    setMsg({});
    // PATCH ignores nulls, so blank optional fields are simply left as they were.
    const body = Object.fromEntries(Object.entries(form).map(([k, v]) => [k, v === '' ? null : v]));
    try {
      updateUser(await users.update(body));
      setMsg({ ok: 'Profile saved.' });
    } catch (err) {
      setErrors(err.fieldErrors);
      setMsg({ error: err.message });
    } finally {
      setBusy(false);
    }
  };

  return (
    <form className="card form" onSubmit={submit}>
      <h2>Your details</h2>
      <p className="muted small">{user?.email}</p>
      <Alert>{msg.error}</Alert>
      <Alert kind="ok">{msg.ok}</Alert>
      <div className="grid-2">
        <Field label="Full name" error={errors.fullName}>
          <input required maxLength={100} value={form.fullName} onChange={set('fullName')} />
        </Field>
        <Field label="Monthly salary (₹)" error={errors.monthlySalary}>
          <input type="number" inputMode="decimal" min="0" step="0.01" required value={form.monthlySalary}
            onChange={set('monthlySalary')} />
        </Field>
        <Field label="Phone" error={errors.phone}>
          <input type="tel" value={form.phone} onChange={set('phone')} />
        </Field>
        <Field label="Date of birth" error={errors.dateOfBirth}>
          <input type="date" value={form.dateOfBirth} onChange={set('dateOfBirth')} />
        </Field>
        <Field label="Occupation" error={errors.occupation}>
          <input maxLength={100} value={form.occupation} onChange={set('occupation')} />
        </Field>
        <Field label="Gender" error={errors.gender}>
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
      <div className="form-actions">
        <button className="btn btn-primary" disabled={busy}>{busy ? 'Saving…' : 'Save changes'}</button>
      </div>
    </form>
  );
}

function Password() {
  const [form, setForm] = useState({ currentPassword: '', newPassword: '' });
  const [msg, setMsg] = useState({});
  const [busy, setBusy] = useState(false);

  const submit = async (e) => {
    e.preventDefault();
    setBusy(true);
    setMsg({});
    try {
      await users.changePassword(form);
      setForm({ currentPassword: '', newPassword: '' });
      setMsg({ ok: 'Password changed.' });
    } catch (err) {
      setMsg({ error: err.fieldErrors.newPassword ? `New password ${err.fieldErrors.newPassword}` : err.message });
    } finally {
      setBusy(false);
    }
  };

  return (
    <form className="card form" onSubmit={submit}>
      <h2>Change password</h2>
      <Alert>{msg.error}</Alert>
      <Alert kind="ok">{msg.ok}</Alert>
      <div className="grid-2">
        <Field label="Current password">
          <input type="password" required autoComplete="current-password" value={form.currentPassword}
            onChange={(e) => setForm({ ...form, currentPassword: e.target.value })} />
        </Field>
        <Field label="New password" hint="At least 8 characters">
          <input type="password" required minLength={8} maxLength={72} autoComplete="new-password"
            value={form.newPassword} onChange={(e) => setForm({ ...form, newPassword: e.target.value })} />
        </Field>
      </div>
      <div className="form-actions">
        <button className="btn btn-primary" disabled={busy}>{busy ? 'Saving…' : 'Change password'}</button>
      </div>
    </form>
  );
}

export default function Profile() {
  const { logout } = useAuth();
  return (
    <>
      <div className="page-head">
        <h1>Profile</h1>
        <button className="btn btn-ghost" onClick={logout}>Sign out</button>
      </div>
      <div className="stack">
        <Details />
        <Password />
      </div>
    </>
  );
}
