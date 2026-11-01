import { useCallback, useEffect, useState } from 'react';
import { emis } from '../api';
import { Alert, Empty, Field, Modal, Spinner, Stat } from '../components/ui';
import { date, label, money, moneyExact, today } from '../format';

const LOAN_TYPES = ['HOME', 'CAR', 'TWO_WHEELER', 'PERSONAL', 'EDUCATION', 'GOLD', 'CONSUMER_DURABLE', 'CREDIT_CARD', 'OTHER'];

// Standard reducing-balance EMI, used only to preview the figure before saving.
function previewEmi(p, rate, n) {
  p = Number(p); rate = Number(rate); n = Number(n);
  if (!p || !n) return null;
  const r = rate / 12 / 100;
  if (!r) return p / n;
  const f = Math.pow(1 + r, n);
  return (p * r * f) / (f - 1);
}

function EmiForm({ initial, onSaved, onCancel }) {
  const [form, setForm] = useState(
    initial
      ? { loanName: initial.loanName, lenderName: initial.lenderName || '', loanType: initial.loanType,
          principalAmount: initial.principalAmount, interestRate: initial.interestRate,
          tenureMonths: initial.tenureMonths, emiAmount: initial.emiAmount, startDate: initial.startDate }
      : { loanName: '', lenderName: '', loanType: 'PERSONAL', principalAmount: '', interestRate: '',
          tenureMonths: '', emiAmount: '', startDate: today() }
  );
  const [errors, setErrors] = useState({});
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value });
  const preview = previewEmi(form.principalAmount, form.interestRate, form.tenureMonths);

  const submit = async (e) => {
    e.preventDefault();
    setBusy(true);
    setError('');
    setErrors({});
    const body = {
      ...form,
      lenderName: form.lenderName.trim() || null,
      tenureMonths: Number(form.tenureMonths),
      emiAmount: form.emiAmount === '' ? null : form.emiAmount,
    };
    try {
      if (initial) await emis.update(initial.emiId, body);
      else await emis.create(body);
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
      <div className="grid-2">
        <Field label="Loan name" error={errors.loanName}>
          <input required maxLength={100} autoFocus value={form.loanName} onChange={set('loanName')} placeholder="e.g. Car loan" />
        </Field>
        <Field label="Lender" error={errors.lenderName}>
          <input maxLength={100} value={form.lenderName} onChange={set('lenderName')} placeholder="e.g. HDFC Bank" />
        </Field>
        <Field label="Type" error={errors.loanType}>
          <select value={form.loanType} onChange={set('loanType')}>
            {LOAN_TYPES.map((t) => <option key={t} value={t}>{label(t)}</option>)}
          </select>
        </Field>
        <Field label="Principal (₹)" error={errors.principalAmount}>
          <input type="number" inputMode="decimal" required min="0.01" step="0.01"
            value={form.principalAmount} onChange={set('principalAmount')} />
        </Field>
        <Field label="Interest rate (% p.a.)" error={errors.interestRate}>
          <input type="number" inputMode="decimal" required min="0" max="60" step="0.01"
            value={form.interestRate} onChange={set('interestRate')} />
        </Field>
        <Field label="Tenure (months)" error={errors.tenureMonths}>
          <input type="number" inputMode="numeric" required min="1" max="480" step="1"
            value={form.tenureMonths} onChange={set('tenureMonths')} />
        </Field>
        <Field label="Start date" error={errors.startDate}>
          <input type="date" required value={form.startDate} onChange={set('startDate')} />
        </Field>
        <Field label="EMI amount (₹)" error={errors.emiAmount}
          hint={preview ? `Leave blank to use ${moneyExact(preview)}` : 'Leave blank to calculate'}>
          <input type="number" inputMode="decimal" min="0.01" step="0.01"
            value={form.emiAmount ?? ''} onChange={set('emiAmount')} />
        </Field>
      </div>
      <div className="form-actions">
        <button type="button" className="btn btn-ghost" onClick={onCancel}>Cancel</button>
        <button className="btn btn-primary" disabled={busy}>{busy ? 'Saving…' : 'Save'}</button>
      </div>
    </form>
  );
}

export default function Emis() {
  const [status, setStatus] = useState('ACTIVE');
  const [list, setList] = useState(null);
  const [summary, setSummary] = useState(null);
  const [error, setError] = useState('');
  const [editing, setEditing] = useState(null);

  const load = useCallback(() => {
    setError('');
    emis.list(status || undefined).then(setList).catch((e) => { setError(e.message); setList([]); });
    emis.summary().then(setSummary).catch(() => {});
  }, [status]);

  useEffect(load, [load]);

  const act = async (fn, msg) => {
    if (!window.confirm(msg)) return;
    try {
      await fn();
      load();
    } catch (err) {
      setError(err.message);
    }
  };

  return (
    <>
      <div className="page-head">
        <h1>EMIs</h1>
        <button className="btn btn-primary" onClick={() => setEditing('new')}>+ Add loan</button>
      </div>

      {summary && (
        <div className="stats three">
          <Stat label="Active loans" value={summary.activeLoans} />
          <Stat label="Monthly EMIs" value={money(summary.monthlyEmiTotal)} />
          <Stat label="Outstanding principal" value={money(summary.totalOutstandingPrincipal)} />
        </div>
      )}

      <div className="segmented" role="tablist">
        {[['ACTIVE', 'Active'], ['CLOSED', 'Closed'], ['', 'All']].map(([v, l]) => (
          <button key={l} role="tab" aria-selected={status === v} className={status === v ? 'on' : ''}
            onClick={() => setStatus(v)}>{l}</button>
        ))}
      </div>

      <Alert>{error}</Alert>

      {!list ? <Spinner /> : list.length === 0 ? (
        <Empty title={status === 'CLOSED' ? 'No closed loans' : 'No loans yet'}>
          {status !== 'CLOSED' && <button className="btn btn-primary" onClick={() => setEditing('new')}>Add a loan</button>}
        </Empty>
      ) : (
        <div className="emi-grid">
          {list.map((e) => {
            const pct = Math.round((e.instalmentsPaid / e.tenureMonths) * 100);
            return (
              <article className="card emi" key={e.emiId}>
                <div className="emi-head">
                  <div>
                    <h3>{e.loanName}</h3>
                    <p className="muted small">{label(e.loanType)}{e.lenderName ? ` · ${e.lenderName}` : ''}</p>
                  </div>
                  <span className={`badge${e.status === 'CLOSED' ? ' muted-badge' : ''}`}>{label(e.status)}</span>
                </div>
                <div className="emi-amount">{moneyExact(e.emiAmount)}<span className="muted small"> / month</span></div>
                <div className="bar"><div className="bar-fill" style={{ width: `${pct}%` }} /></div>
                <p className="muted small">{e.instalmentsPaid} of {e.tenureMonths} paid · {e.instalmentsRemaining} left</p>
                <dl className="kv">
                  <dt>Outstanding</dt><dd>{money(e.outstandingPrincipal)}</dd>
                  <dt>Principal</dt><dd>{money(e.principalAmount)}</dd>
                  <dt>Rate</dt><dd>{e.interestRate}%</dd>
                  <dt>Ends</dt><dd>{date(e.endDate)}</dd>
                </dl>
                <div className="card-actions">
                  <button className="btn btn-ghost" onClick={() => setEditing(e)}>Edit</button>
                  {e.status === 'ACTIVE' && (
                    <button className="btn btn-ghost" onClick={() => act(() => emis.close(e.emiId), `Mark "${e.loanName}" as closed?`)}>
                      Close
                    </button>
                  )}
                  <button className="btn btn-ghost danger" onClick={() => act(() => emis.remove(e.emiId), `Delete "${e.loanName}"? This cannot be undone.`)}>
                    Delete
                  </button>
                </div>
              </article>
            );
          })}
        </div>
      )}

      {editing && (
        <Modal title={editing === 'new' ? 'Add loan' : 'Edit loan'} onClose={() => setEditing(null)}>
          <EmiForm initial={editing === 'new' ? null : editing} onCancel={() => setEditing(null)}
            onSaved={() => { setEditing(null); load(); }} />
        </Modal>
      )}
    </>
  );
}
