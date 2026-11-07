import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { daily, monthly } from '../api';
import { Alert, Empty, Field, Modal, Money, Spinner, Stat } from '../components/ui';
import { label, money } from '../format';

export const MONTHLY_TYPES = [
  ['RENT', '🏠'], ['UTILITIES', '💡'], ['PHONE_INTERNET', '📶'], ['SUBSCRIPTION', '📺'], ['INSURANCE', '🛡️'],
  ['EDUCATION', '🎓'], ['HOUSEHOLD_HELP', '🧹'], ['TRANSPORT', '🚌'], ['FAMILY_SUPPORT', '👪'], ['OTHER', '🧾'],
];
const ICON = Object.fromEntries(MONTHLY_TYPES);
const typeName = (t) => (t === 'PHONE_INTERNET' ? 'Phone & internet' : label(t));

const ordinal = (n) => {
  const s = n % 100 >= 11 && n % 100 <= 13 ? 'th' : ['th', 'st', 'nd', 'rd'][n % 10] || 'th';
  return `${n}${s}`;
};

// "Due 5th · in 3 days". A due day past the month's end (31st in a 30-day month) falls on its last day.
function dueText(dueDay) {
  if (!dueDay) return null;
  const now = new Date();
  const y = now.getFullYear();
  const m = now.getMonth();
  const onDay = (yy, mm) => new Date(yy, mm, Math.min(dueDay, new Date(yy, mm + 1, 0).getDate()));
  let next = onDay(y, m);
  const start = new Date(y, m, now.getDate());
  if (next < start) next = onDay(y, m + 1);
  const days = Math.round((next - start) / 86400000);
  const when = days === 0 ? 'today' : days === 1 ? 'tomorrow' : `in ${days} days`;
  return { text: `Due ${ordinal(dueDay)} · ${when}`, soon: days <= 3 };
}

function MonthlyForm({ initial, onSaved, onCancel }) {
  const [form, setForm] = useState(
    initial
      ? { name: initial.name, amount: initial.amount, type: initial.type, dueDay: initial.dueDay ?? '', note: initial.note || '', active: initial.active }
      : { name: '', amount: '', type: 'RENT', dueDay: '', note: '', active: true }
  );
  const [errors, setErrors] = useState({});
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value });

  const submit = async (e) => {
    e.preventDefault();
    setBusy(true);
    setError('');
    setErrors({});
    const body = {
      name: form.name.trim(),
      amount: form.amount,
      type: form.type,
      dueDay: form.dueDay === '' ? null : Number(form.dueDay),
      note: form.note.trim() || null,
      active: form.active,
    };
    try {
      if (initial) await monthly.update(initial.monthlyExpenseId, body);
      else await monthly.create(body);
      onSaved();
    } catch (err) {
      setErrors(err.fieldErrors);
      setError(err.message);
      setBusy(false);
    }
  };

  return (
    <form onSubmit={submit} className="form">
      <Alert>{error}</Alert>
      <div className="type-picker" role="radiogroup" aria-label="Type">
        {MONTHLY_TYPES.map(([t, icon]) => (
          <button type="button" key={t} role="radio" aria-checked={form.type === t}
            className={form.type === t ? 'on' : ''} onClick={() => setForm({ ...form, type: t })}>
            <span aria-hidden="true">{icon}</span>{typeName(t)}
          </button>
        ))}
      </div>
      <div className="grid-2">
        <Field label="Name" error={errors.name}>
          <input required maxLength={100} autoFocus={!initial} value={form.name} onChange={set('name')}
            placeholder="e.g. Flat rent, Netflix, Maid" />
        </Field>
        <Field label="Amount each month (₹)" error={errors.amount}>
          <input type="number" inputMode="decimal" required min="0.01" step="0.01"
            value={form.amount} onChange={set('amount')} />
        </Field>
        <Field label="Usually paid on" error={errors.dueDay} hint="Day of the month, optional.">
          <select value={form.dueDay} onChange={set('dueDay')}>
            <option value="">No fixed day</option>
            {Array.from({ length: 31 }, (_, i) => i + 1).map((d) => <option key={d} value={d}>{ordinal(d)}</option>)}
          </select>
        </Field>
      </div>
      <Field label="Note" error={errors.note} hint="Anything to remember: who to pay, account number, when it renews.">
        <textarea rows={3} maxLength={500} value={form.note} onChange={set('note')}
          placeholder="e.g. Pay the owner by UPI before the 5th" />
      </Field>
      {initial && (
        <label className="check">
          <input type="checkbox" checked={form.active} onChange={(e) => setForm({ ...form, active: e.target.checked })} />
          <span>Count this every month <span className="muted small">(untick to pause it)</span></span>
        </label>
      )}
      <div className="form-actions">
        <button type="button" className="btn btn-ghost" onClick={onCancel}>Cancel</button>
        <button className="btn btn-primary" disabled={busy}>{busy ? 'Saving…' : 'Save'}</button>
      </div>
    </form>
  );
}

// Salary split into EMIs, fixed monthly costs, savings and what is free to spend.
function SalarySplit({ s }) {
  const salary = Number(s.monthlySalary || 0);
  const parts = [
    ['EMIs', Number(s.monthlyEmi || 0), 'seg-emi'],
    ['Monthly expenses', Number(s.monthlyExpenses || 0), 'seg-fixed'],
    ['Savings goal', Number(s.monthlySavingsGoal || 0), 'seg-save'],
  ];
  const used = parts.reduce((a, [, v]) => a + v, 0);
  const free = salary - used;
  const rows = [...parts, ['Free to spend', Math.max(0, free), 'seg-free']];
  return (
    <section className="card">
      <div className="section-head">
        <h2>Where your salary goes</h2>
        <span className="num">{money(salary)}</span>
      </div>
      <div className="split-bar" role="img"
        aria-label={rows.map(([l, v]) => `${l} ${money(v)}`).join(', ')}>
        {rows.map(([l, v, cls]) => v > 0 && (
          <span key={l} className={cls} style={{ flexGrow: v }} title={`${l}: ${money(v)}`} />
        ))}
      </div>
      <ul className="split-legend">
        {rows.map(([l, v, cls]) => (
          <li key={l}>
            <span className={`dot ${cls}`} aria-hidden="true" />
            <span>{l}</span>
            <span className="muted small">{salary > 0 ? `${Math.round((v / salary) * 100)}%` : ''}</span>
            <span className="num">{money(v)}</span>
          </li>
        ))}
      </ul>
      {free < 0 ? (
        <p className="small" style={{ color: 'var(--bad)', margin: '10px 0 0' }}>
          These add up to {money(-free)} more than your salary. Try lowering the savings goal or pausing something.
        </p>
      ) : (
        <p className="muted small" style={{ margin: '10px 0 0' }}>
          What is free to spend is shared out across the days of the month on the <Link to="/today">Today</Link> tab.
        </p>
      )}
    </section>
  );
}

export default function Monthly() {
  const [data, setData] = useState(null);
  const [summary, setSummary] = useState(null);
  const [error, setError] = useState('');
  const [editing, setEditing] = useState(null);

  const load = useCallback(() => {
    setError('');
    monthly.list().then(setData).catch((e) => { setError(e.message); setData((d) => d || { items: [], monthlyTotal: 0, activeCount: 0 }); });
    daily.summary().then(setSummary).catch(() => setSummary(null));
  }, []);

  useEffect(load, [load]);

  const run = async (fn) => {
    try {
      await fn();
      load();
    } catch (err) {
      setError(err.message);
    }
  };
  const remove = (m) => window.confirm(`Delete "${m.name}"? This cannot be undone.`)
    && run(() => monthly.remove(m.monthlyExpenseId));
  const toggle = (m) => run(() => monthly.setActive(m.monthlyExpenseId, !m.active));

  const head = (
    <>
      <div className="page-head">
        <div>
          <h1>Monthly expenses</h1>
          <p className="muted">Rent, bills and subscriptions you pay every month</p>
        </div>
        <button className="btn btn-primary" onClick={() => setEditing('new')}>+ Add</button>
      </div>
      <Alert>{error}</Alert>
    </>
  );

  if (!data) return <>{head}<Spinner /></>;

  const active = data.items.filter((m) => m.active);
  const paused = data.items.filter((m) => !m.active);
  const salary = Number(summary?.monthlySalary || 0);
  const share = salary > 0 ? Math.round((Number(data.monthlyTotal) / salary) * 100) : null;

  const row = (m) => {
    const due = m.active ? dueText(m.dueDay) : null;
    return (
      <li key={m.monthlyExpenseId} className={`list-row monthly-row${m.active ? '' : ' paused'}`}>
        <span className="type-icon" aria-hidden="true">{ICON[m.type] || '🧾'}</span>
        <button className="row-main" onClick={() => setEditing(m)} aria-label={`Edit ${m.name}`}>
          <div className="monthly-name">{m.name}</div>
          <div className="muted small">
            {typeName(m.type)}
            {due && <> · <span className={due.soon ? 'due-soon' : ''}>{due.text}</span></>}
            {!m.active && ' · Paused'}
          </div>
          {m.note && <div className="note-line small">📝 {m.note}</div>}
        </button>
        <span className="num">{money(m.amount)}</span>
        <span className="row-tools">
          <button className="icon-btn pause-btn" onClick={() => toggle(m)}
            aria-label={m.active ? `Pause ${m.name}` : `Resume ${m.name}`} title={m.active ? 'Pause' : 'Resume'}>
            {m.active ? '⏸' : '▶'}
          </button>
          <button className="icon-btn" aria-label={`Delete ${m.name}`} title="Delete" onClick={() => remove(m)}>🗑</button>
        </span>
      </li>
    );
  };

  return (
    <>
      {head}

      <div className="stats three">
        <Stat label="Fixed every month" value={<Money value={data.monthlyTotal} />}
          sub={`${data.activeCount} active${paused.length ? ` · ${paused.length} paused` : ''}`} />
        <Stat label="Share of salary" value={share === null ? '—' : `${share}%`}
          sub={salary > 0 ? `of ${money(salary)}` : 'Add your salary on Today'} />
        <Stat label="Left after EMIs & bills"
          value={summary ? money(salary - Number(summary.monthlyEmi || 0) - Number(data.monthlyTotal)) : '—'}
          sub="before savings and daily spending" />
      </div>

      {data.items.length === 0 ? (
        <section className="card">
          <Empty title="Nothing noted yet">
            <p className="muted" style={{ margin: 0, maxWidth: 420 }}>
              Note the costs that come round every month, like rent, electricity, your phone plan or Netflix.
              They are taken out of your salary before your daily budget is worked out.
            </p>
            <button className="btn btn-primary" onClick={() => setEditing('new')}>Add your first one</button>
          </Empty>
        </section>
      ) : (
        <div className="grid-dash">
          <section className="card">
            <div className="section-head">
              <h2>Every month</h2>
              <span className="num">{money(data.monthlyTotal)}</span>
            </div>
            {active.length === 0 ? <p className="muted">Everything is paused.</p> : <ul className="list">{active.map(row)}</ul>}
            {paused.length > 0 && (
              <>
                <h3 className="list-sub">Paused <span className="muted small">not counted</span></h3>
                <ul className="list">{paused.map(row)}</ul>
              </>
            )}
            <p className="muted small" style={{ margin: '12px 0 0' }}>
              These already come out of your daily budget, so there is no need to log them again on Today.
            </p>
          </section>

          {summary && salary > 0 && <SalarySplit s={{ ...summary, monthlyExpenses: data.monthlyTotal }} />}
        </div>
      )}

      {editing && (
        <Modal title={editing === 'new' ? 'Add monthly expense' : 'Edit monthly expense'} onClose={() => setEditing(null)}>
          <MonthlyForm initial={editing === 'new' ? null : editing} onCancel={() => setEditing(null)}
            onSaved={() => { setEditing(null); load(); }} />
        </Modal>
      )}
    </>
  );
}
