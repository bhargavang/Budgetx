import { useCallback, useEffect, useState } from 'react';
import { daily, expenses, users } from '../api';
import { useAuth } from '../AuthContext';
import { Alert, Field, Modal, Money, Spinner } from '../components/ui';
import { date as fmtDate, label, money, today } from '../format';
import { ExpenseForm } from './Expenses';
import { EXPENSE_ADDED } from '../components/PayFlow';

function shift(iso, days) {
  const d = new Date(`${iso}T00:00:00`);
  d.setDate(d.getDate() + days);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

function PlanForm({ summary, onSaved, onCancel }) {
  const { updateUser } = useAuth();
  const [salary, setSalary] = useState(summary.monthlySalary ?? '');
  const [goal, setGoal] = useState(summary.monthlySavingsGoal ?? 0);
  const [errors, setErrors] = useState({});
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const submit = async (e) => {
    e.preventDefault();
    setBusy(true);
    setError('');
    setErrors({});
    try {
      if (Number(salary) !== Number(summary.monthlySalary)) {
        updateUser(await users.update({ monthlySalary: salary }));
      }
      if (Number(goal) !== Number(summary.monthlySavingsGoal)) {
        await daily.saveSettings(goal === '' ? 0 : goal);
      }
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
      <Field label="Monthly salary (₹)" error={errors.monthlySalary} hint="Your take-home pay each month.">
        <input type="number" inputMode="decimal" min="0" step="0.01" required autoFocus
          value={salary} onChange={(e) => setSalary(e.target.value)} />
      </Field>
      <Field label="Keep aside for savings each month (₹)" error={errors.monthlySavingsGoal}
        hint="This is taken out before your daily budget is worked out. Use 0 to skip.">
        <input type="number" inputMode="decimal" min="0" step="0.01"
          value={goal} onChange={(e) => setGoal(e.target.value)} />
      </Field>
      <div className="form-actions">
        <button type="button" className="btn btn-ghost" onClick={onCancel}>Cancel</button>
        <button className="btn btn-primary" disabled={busy}>{busy ? 'Saving…' : 'Save'}</button>
      </div>
    </form>
  );
}

function DayNote({ day, initial, onSaved }) {
  const [text, setText] = useState(initial || '');
  const [status, setStatus] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    setText(initial || '');
    setStatus('');
  }, [day, initial]);

  const save = async () => {
    setBusy(true);
    setStatus('');
    try {
      const res = await daily.saveNote(day, text.trim() || null);
      setStatus(res?.note ? 'Saved' : 'Note cleared');
      onSaved(res?.note || null);
    } catch (err) {
      setStatus(err.message);
    } finally {
      setBusy(false);
    }
  };

  const dirty = (text.trim() || '') !== (initial || '');
  return (
    <section className="card">
      <h2>Note for the day</h2>
      <textarea rows={4} maxLength={2000} value={text} onChange={(e) => setText(e.target.value)}
        aria-label="Note for the day"
        placeholder="How did today go? e.g. Office party, spent more on food than planned." />
      <div className="note-actions">
        <span className="muted small" role="status">{status}</span>
        <button className="btn btn-primary" onClick={save} disabled={busy || !dirty}>
          {busy ? 'Saving…' : 'Save note'}
        </button>
      </div>
    </section>
  );
}

export default function Today() {
  const [day, setDay] = useState(today());
  const [summary, setSummary] = useState(null);
  const [categories, setCategories] = useState([]);
  const [error, setError] = useState('');
  const [editing, setEditing] = useState(null);
  const [planOpen, setPlanOpen] = useState(false);

  useEffect(() => {
    expenses.categories().then(setCategories).catch((e) => setError(e.message));
  }, []);

  const load = useCallback(() => {
    setError('');
    daily.summary(day).then(setSummary).catch((e) => {
      setError(e.message);
      setSummary((s) => s || { failed: true });
    });
  }, [day]);

  useEffect(load, [load]);

  useEffect(() => {
    window.addEventListener(EXPENSE_ADDED, load);
    return () => window.removeEventListener(EXPENSE_ADDED, load);
  }, [load]);

  const remove = async (e) => {
    if (!window.confirm(`Delete ${money(e.amount)} on ${e.categoryName}?`)) return;
    try {
      await expenses.remove(e.expenseId);
      load();
    } catch (err) {
      setError(err.message);
    }
  };

  const isToday = day === today();
  const head = (
    <>
      <div className="page-head">
        <div>
          <h1>{isToday ? 'Today' : fmtDate(day)}</h1>
          <p className="muted">Your daily budget from your salary</p>
        </div>
        <button className="btn btn-primary" onClick={() => setEditing('new')}>+ Add expense</button>
      </div>
      <div className="day-nav">
        <button className="btn btn-ghost" aria-label="Previous day" onClick={() => setDay(shift(day, -1))}>‹</button>
        <input type="date" aria-label="Day" value={day} max={today()}
          onChange={(e) => setDay(e.target.value && e.target.value <= today() ? e.target.value : today())} />
        <button className="btn btn-ghost" aria-label="Next day" disabled={isToday}
          onClick={() => setDay(shift(day, 1))}>›</button>
        {!isToday && <button className="btn btn-ghost" onClick={() => setDay(today())}>Today</button>}
      </div>
      <Alert>{error}</Alert>
    </>
  );

  if (!summary) return <>{head}<Spinner /></>;

  const s = summary.failed ? null : summary;
  const allowance = Number(s?.todaysAllowance || 0);
  const spentToday = Number(s?.spentToday || 0);
  const remaining = Number(s?.remainingToday || 0);
  const pct = allowance > 0 ? Math.min(100, (spentToday / allowance) * 100) : spentToday > 0 ? 100 : 0;
  const noSalary = s && Number(s.monthlySalary) <= 0;

  return (
    <>
      {head}

      {s && (
        <>
          {noSalary ? (
            <section className="card day-hero">
              <h2>Add your salary to get a daily budget</h2>
              <p className="muted">We split what is left after EMIs and savings across the days of the month.</p>
              <div><button className="btn btn-primary" onClick={() => setPlanOpen(true)}>Set salary</button></div>
            </section>
          ) : (
            <section className="card day-hero">
              <span className="hero-label">{isToday ? 'Left to spend today' : `Left to spend on ${fmtDate(day)}`}</span>
              <span className={`hero-value ${remaining < 0 ? 'bad' : 'good'}`}>
                <Money value={remaining} signed />
              </span>
              <div className="bar" role="progressbar" aria-valuemin={0} aria-valuemax={100} aria-valuenow={Math.round(pct)}
                aria-label="Share of the day's allowance spent">
                <div className={`bar-fill${remaining < 0 ? ' over' : ''}`} style={{ width: `${pct}%` }} />
              </div>
              <div className="hero-foot">
                <span>Spent {money(spentToday)} of {money(allowance)}</span>
                <span>Usual daily budget {money(s.dailyBudget)}</span>
              </div>
              {remaining < 0 && (
                <p className="small" style={{ color: 'var(--bad)' }}>
                  Over by {money(-remaining)}. Tomorrow's allowance will shrink a little to make up for it.
                </p>
              )}
            </section>
          )}

          {!s.emiAvailable && (
            <Alert>EMI details could not load, so EMIs are not taken out of this budget right now.</Alert>
          )}

          <div className="grid-dash">
            <section className="card">
              <div className="section-head">
                <h2>Spent on this day</h2>
                <span className="num">{money(spentToday)}</span>
              </div>
              {s.expenses.length === 0 ? (
                <p className="muted">Nothing logged yet.</p>
              ) : (
                <ul className="list">
                  {s.expenses.map((e) => (
                    <li key={e.expenseId} className="list-row">
                      <button className="row-main" onClick={() => setEditing(e)}>
                        <div>{e.description || e.categoryName}</div>
                        <div className="muted small">
                          {e.categoryName}{e.paymentMethod ? ` · ${label(e.paymentMethod)}` : ''}
                        </div>
                        {e.note && <div className="note-line small">📝 {e.note}</div>}
                      </button>
                      <span className="num">{money(e.amount)}</span>
                      <button className="icon-btn" aria-label="Delete" onClick={() => remove(e)}>🗑</button>
                    </li>
                  ))}
                </ul>
              )}
            </section>

            <DayNote day={day} initial={s.dayNote}
              onSaved={(note) => setSummary((cur) => ({ ...cur, dayNote: note }))} />

            <section className="card span-2">
              <div className="section-head">
                <h2>Your month</h2>
                <button className="btn btn-ghost" onClick={() => setPlanOpen(true)}>Edit salary &amp; savings</button>
              </div>
              <div className="plan-row"><span>Monthly salary</span><span className="num">{money(s.monthlySalary)}</span></div>
              <div className="plan-row"><span>EMIs</span><span className="num">− {money(s.monthlyEmi)}</span></div>
              <div className="plan-row"><span>Savings goal</span><span className="num">− {money(s.monthlySavingsGoal)}</span></div>
              <div className="plan-row total"><span>Free to spend this month</span><span className="num">{money(s.spendableThisMonth)}</span></div>
              <div className="plan-row"><span>Spent so far</span><span className="num">{money(s.spentThisMonth)}</span></div>
              <div className="plan-row total">
                <span>Left this month</span>
                <span className="num" style={{ color: Number(s.remainingThisMonth) < 0 ? 'var(--bad)' : 'var(--good)' }}>
                  {money(s.remainingThisMonth)}
                </span>
              </div>
              <p className="muted small" style={{ marginTop: 8 }}>
                {s.daysLeftInMonth} day{s.daysLeftInMonth === 1 ? '' : 's'} left including {isToday ? 'today' : 'this day'}.
                Each day's allowance is what is left divided by the days remaining.
              </p>
            </section>
          </div>
        </>
      )}

      {editing && (
        <Modal title={editing === 'new' ? 'Add expense' : 'Edit expense'} onClose={() => setEditing(null)}>
          <ExpenseForm
            initial={editing === 'new' ? null : editing}
            categories={categories}
            defaultDate={day}
            onCancel={() => setEditing(null)}
            onSaved={() => { setEditing(null); load(); }}
          />
        </Modal>
      )}

      {planOpen && s && (
        <Modal title="Salary and savings" onClose={() => setPlanOpen(false)}>
          <PlanForm summary={s} onCancel={() => setPlanOpen(false)} onSaved={() => { setPlanOpen(false); load(); }} />
        </Modal>
      )}
    </>
  );
}
