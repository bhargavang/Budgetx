import { useCallback, useEffect, useState } from 'react';
import { guidance as guidanceApi } from '../api';
import { Alert, Field, Spinner, Stat } from '../components/ui';
import { label, money } from '../format';

const SEVERITY = { CRITICAL: 'Fix first', WARNING: 'Watch out', INFO: 'Note' };
const ASSET = { EQUITY: 'Equity', DEBT: 'Safe / debt', GOLD: 'Gold' };

function SafetyNetForm({ initial, onSaved }) {
  const [form, setForm] = useState({
    emergencySavings: initial.saved ? String(initial.emergencySavings) : '',
    dependents: initial.saved ? String(initial.dependents) : '',
    hasTermInsurance: initial.hasTermInsurance ? 'true' : 'false',
    hasHealthInsurance: initial.hasHealthInsurance ? 'true' : 'false',
  });
  const [errors, setErrors] = useState({});
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value });

  const submit = async (e) => {
    e.preventDefault();
    setBusy(true);
    setErrors({});
    setError('');
    try {
      await guidanceApi.saveSafetyProfile({
        emergencySavings: Number(form.emergencySavings),
        dependents: Number(form.dependents),
        hasTermInsurance: form.hasTermInsurance === 'true',
        hasHealthInsurance: form.hasHealthInsurance === 'true',
      });
      await onSaved();
    } catch (err) {
      setErrors(err.fieldErrors || {});
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  const yesNo = (k) => (
    <select value={form[k]} onChange={set(k)}>
      <option value="false">No</option>
      <option value="true">Yes</option>
    </select>
  );

  return (
    <form className="card form" onSubmit={submit}>
      <h2>Your safety net</h2>
      <p className="muted small">We can't see your bank or policies, so tell us. This stays in BudgetX.</p>
      <Alert>{error}</Alert>
      <Field label="Emergency savings you can withdraw within a day (₹)" error={errors.emergencySavings}>
        <input type="number" inputMode="numeric" min="0" step="1" required value={form.emergencySavings}
          onChange={set('emergencySavings')} />
      </Field>
      <Field label="People who depend on your income" hint="Spouse, children, parents" error={errors.dependents}>
        <input type="number" inputMode="numeric" min="0" max="20" step="1" required value={form.dependents}
          onChange={set('dependents')} />
      </Field>
      <div className="grid-2">
        <Field label="Term life insurance?">{yesNo('hasTermInsurance')}</Field>
        <Field label="Personal health insurance?">{yesNo('hasHealthInsurance')}</Field>
      </div>
      <div className="form-actions">
        <button className="btn btn-primary" disabled={busy}>{busy ? 'Saving…' : 'Save and update guidance'}</button>
      </div>
    </form>
  );
}

function AllocationRow({ a }) {
  return (
    <li className="alloc">
      <div className="alloc-head">
        <div>
          <div className="alloc-title">{a.title}</div>
          <div className="muted small">{ASSET[a.assetClass] || a.assetClass} · {label(a.riskLevel)} risk · {a.horizon}</div>
        </div>
        <div className="alloc-amount">
          <span className="num">{money(a.monthlyAmount)}</span>
          <span className="muted small">/mo · {a.percent}%</span>
        </div>
      </div>
      <div className="bar">
        <div className={`bar-fill${a.assetClass === 'EQUITY' ? ' bar-equity' : ''}`} style={{ width: `${Math.min(100, a.percent)}%` }} />
      </div>
      {a.reason && <p className="alloc-reason">{a.reason}</p>}
      <details><summary>What is this?</summary><p className="muted small">{a.about}</p></details>
    </li>
  );
}

function GuidanceView({ g }) {
  const b = g.budget;
  const ai = g.mode === 'AI_CHECKED';
  return (
    <div className="stack">
      <div className={`card mode${ai ? ' mode-ai' : ''}`}>
        <strong>{ai ? 'AI-personalised, safety-checked' : 'Rule-based guidance'}</strong>
        <span className="muted small">
          {g.modeNote} · {new Date(g.generatedAt).toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'short' })}
        </span>
      </div>

      <div className="stats">
        <Stat label="Left after EMIs and spending" value={money(b.disposableIncome)}
          tone={Number(b.disposableIncome) < 0 ? 'bad' : undefined} />
        <Stat label="Set aside each month" value={money(b.investableAmount)} />
        <Stat label="To emergency fund" value={money(b.emergencyFundContribution)} />
        <Stat label="To invest" value={money(b.amountToInvest)} tone="good" />
      </div>

      {g.warnings.length > 0 && (
        <section className="card">
          <h2>Things to know</h2>
          <ul className="list">
            {g.warnings.map((w) => (
              <li className="insight" key={w.title}>
                <span className={`sev sev-${w.severity.toLowerCase()}`}>{SEVERITY[w.severity] || w.severity}</span>
                <div><div className="alloc-title">{w.title}</div><div className="muted small">{w.detail}</div></div>
              </li>
            ))}
          </ul>
        </section>
      )}

      {g.actions.length > 0 && (
        <section className="card">
          <h2>First steps, before investing</h2>
          <ol className="steps">
            {g.actions.map((a) => (
              <li key={a.type}>
                <div className="alloc-title">
                  {a.title} {a.monthlyAmount && <span className="badge">{money(a.monthlyAmount)}/mo</span>}
                </div>
                <div className="muted small">{a.detail}</div>
              </li>
            ))}
          </ol>
        </section>
      )}

      <section className="card">
        <h2>Suggested monthly split</h2>
        <p className="muted small">
          Your risk profile is {label(g.riskProfile)}, so at most {g.equityCapPercent}% goes to equity.
        </p>
        {g.summary && <p className="ai-summary"><span className="badge">AI explanation</span> {g.summary}</p>}
        {g.allocations.length > 0
          ? <ul className="list">{g.allocations.map((a) => <AllocationRow a={a} key={a.type} />)}</ul>
          : <p className="muted">No investing suggestion this month. Focus on the steps above first.</p>}
      </section>

      <section className="card">
        <h2>Our promises</h2>
        <ul className="promises">{g.principles.map((p) => <li key={p}>{p}</li>)}</ul>
        <p className="disclaimer">{g.disclaimer}</p>
      </section>
    </div>
  );
}

export default function Guidance() {
  const [state, setState] = useState({ loading: true });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    try {
      const [latest, safety] = await Promise.all([
        guidanceApi.latest().catch((e) => { if (e.status === 404) return null; throw e; }),
        guidanceApi.safetyProfile(),
      ]);
      setState({ latest, safety });
    } catch (e) {
      setState({ error: e.message });
    }
  }, []);

  useEffect(() => { load(); }, [load]);

  const generate = async () => {
    setBusy(true);
    setError('');
    try {
      const latest = await guidanceApi.generate();
      setState((s) => ({ ...s, latest }));
    } catch (e) {
      setError(e.message);
    } finally {
      setBusy(false);
    }
  };

  const afterSave = async () => {
    const safety = await guidanceApi.safetyProfile();
    setState((s) => ({ ...s, safety }));
    await generate();
  };

  if (state.loading) return <Spinner />;
  if (state.error) return <Alert>{state.error}</Alert>;

  return (
    <>
      <div className="page-head">
        <div>
          <h1>Money guidance</h1>
          <p className="muted small">Free and educational. Not investment advice.</p>
        </div>
        <button className="btn btn-primary" onClick={generate} disabled={busy}>
          {busy ? 'Working it out…' : state.latest ? 'Refresh' : 'Get my guidance'}
        </button>
      </div>
      <Alert>{error}</Alert>
      <div className="guidance-layout">
        {state.latest ? <GuidanceView g={state.latest} /> : (
          <div className="card">
            <h2>How this works</h2>
            <p className="muted">
              BudgetX looks at your salary, EMIs and spending, then checks the basics first: no spending gap, an
              emergency fund, and health and life cover. Only then does it suggest how to split what's left across
              broad, regulated categories like index funds, PPF and deposits. We never name specific funds or earn
              commissions.
            </p>
            <p className="muted">Answer the safety-net questions, then press <strong>Get my guidance</strong>.</p>
          </div>
        )}
        <SafetyNetForm initial={state.safety} onSaved={afterSave} />
      </div>
    </>
  );
}
