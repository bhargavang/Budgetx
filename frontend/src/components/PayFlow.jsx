import { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import QRCode from 'qrcode';
import { expenses } from '../api';
import { useAuth } from '../AuthContext';
import { money, today } from '../format';
import {
  APPS, appLink, clearPending, forgetPayee, isValidVpa, loadPayees, loadPending, parseUpiLink,
  platform, rememberPayee, savePending, upiLink,
} from '../upi';
import Coins3D from './Coins3D';
import { Alert, Field, Modal } from './ui';

export const EXPENSE_ADDED = 'budgetx:expense-added';

const PayContext = createContext({ openPay: () => {} });
export const usePay = () => useContext(PayContext);

// Holds the Pay sheet for every signed-in page, and picks up a payment that was still
// waiting for an answer when the browser reloaded BudgetX after the UPI app.
export function PayProvider({ children }) {
  const { user } = useAuth();
  const owner = String(user?.userId ?? user?.email ?? '');
  const [state, setState] = useState(null); // { step, payment }

  useEffect(() => {
    const pending = loadPending(owner);
    if (pending) setState({ step: 'confirm', payment: pending });
  }, [owner]);

  const openPay = useCallback((preset) => setState({ step: 'form', payment: preset || null }), []);
  const close = useCallback(() => {
    clearPending();
    setState(null);
  }, []);

  return (
    <PayContext.Provider value={{ openPay }}>
      {children}
      {state && (
        <Modal title={state.step === 'form' ? 'Pay with UPI' : 'UPI payment'} onClose={close}>
          <PaySheet owner={owner} initial={state} onClose={close} />
        </Modal>
      )}
    </PayContext.Provider>
  );
}

function PaySheet({ owner, initial, onClose }) {
  const [step, setStep] = useState(initial.step);
  const [payment, setPayment] = useState(initial.payment);
  const [categories, setCategories] = useState([]);
  const [error, setError] = useState('');
  const [logged, setLogged] = useState(null);

  useEffect(() => {
    expenses.categories().then(setCategories).catch((e) => setError(e.message));
  }, []);

  // When the person comes back from their UPI app, ask how it went.
  const stepRef = useRef(step);
  stepRef.current = step;
  useEffect(() => {
    let wentAway = false;
    const onVis = () => {
      if (document.hidden) wentAway = true;
      else if (wentAway && stepRef.current === 'handoff') setStep('confirm');
    };
    document.addEventListener('visibilitychange', onVis);
    return () => document.removeEventListener('visibilitychange', onVis);
  }, []);

  const start = (p) => {
    const full = { ...p, owner };
    savePending(full);
    setPayment(full);
    setStep('handoff');
    if (platform() !== 'desktop') window.location.href = upiLink(full);
  };

  const log = async () => {
    setStep('logging');
    setError('');
    const who = payment.name ? `${payment.name} (${payment.vpa})` : payment.vpa;
    try {
      const saved = await expenses.create({
        categoryId: Number(payment.categoryId),
        amount: Number(payment.amount).toFixed(2),
        description: `Paid ${who}`.slice(0, 255),
        expenseDate: today(),
        paymentMethod: 'UPI',
        note: payment.note?.trim() || null,
      });
      clearPending();
      rememberPayee(owner, payment);
      window.dispatchEvent(new CustomEvent(EXPENSE_ADDED, { detail: saved }));
      setLogged(saved || payment);
      setStep('done');
    } catch (err) {
      setError(err.message);
      setStep('confirm');
    }
  };

  if (step === 'form') {
    return <PayForm owner={owner} categories={categories} initial={payment} loadError={error}
      onCancel={onClose} onSubmit={start} />;
  }
  if (step === 'handoff') {
    return <Handoff payment={payment} onDone={() => setStep('confirm')} onEdit={() => setStep('form')} />;
  }
  if (step === 'done') {
    return <Done payment={payment} logged={logged} categories={categories} onClose={onClose} />;
  }
  // confirm / logging
  return (
    <div className="pay-step">
      <Coins3D mode="spin" className="pay-coin" />
      <p className="pay-amount">{money(payment.amount)}</p>
      <p className="center">to <strong>{payment.name || payment.vpa}</strong></p>
      <h3 className="pay-question">Did the payment go through?</h3>
      <p className="muted small center">Check your UPI app or bank SMS. BudgetX only logs it, it never moves money.</p>
      <Alert>{error}</Alert>
      <div className="pay-actions">
        <button className="btn btn-primary btn-block" onClick={log} disabled={step === 'logging'}>
          {step === 'logging' ? 'Logging…' : 'Yes, log this expense'}
        </button>
        <button className="btn btn-ghost btn-block" onClick={() => setStep('handoff')} disabled={step === 'logging'}>
          Not yet, try paying again
        </button>
        <button className="btn btn-ghost btn-block danger" onClick={onClose} disabled={step === 'logging'}>
          No, it failed
        </button>
      </div>
    </div>
  );
}

function PayForm({ owner, categories, initial, loadError, onCancel, onSubmit }) {
  const [payees, setPayees] = useState(() => loadPayees(owner));
  const [form, setForm] = useState({
    vpa: initial?.vpa || '', name: initial?.name || '', amount: initial?.amount || '',
    categoryId: initial?.categoryId ? String(initial.categoryId) : '', note: initial?.note || '',
  });
  const [errors, setErrors] = useState({});
  const set = (k) => (e) => setForm((f) => ({ ...f, [k]: e.target.value }));

  // Default to the first category once they load, unless a payee already chose one.
  useEffect(() => {
    if (!form.categoryId && categories.length) {
      const food = categories.find((c) => /food|grocer/i.test(c.name));
      setForm((f) => ({ ...f, categoryId: String((food || categories[0]).categoryId) }));
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [categories]);

  const onVpa = (e) => {
    const parsed = parseUpiLink(e.target.value);
    if (parsed) {
      setForm((f) => ({ ...f, vpa: parsed.vpa, name: parsed.name || f.name,
        amount: parsed.amount || f.amount, note: parsed.note || f.note }));
    } else {
      set('vpa')(e);
    }
  };

  const pick = (p) => setForm((f) => ({ ...f, vpa: p.vpa, name: p.name,
    categoryId: p.categoryId ? String(p.categoryId) : f.categoryId }));

  const submit = (e) => {
    e.preventDefault();
    const errs = {};
    if (!isValidVpa(form.vpa)) errs.vpa = 'Enter a UPI ID like name@okhdfcbank or 98xxxxxx10@ybl.';
    const amt = Number(form.amount);
    if (!(amt > 0)) errs.amount = 'Enter an amount above ₹0.';
    else if (amt > 100000) errs.amount = 'UPI payments are limited to ₹1,00,000 at a time.';
    if (!form.categoryId) errs.categoryId = 'Choose a category so it lands in the right place.';
    setErrors(errs);
    if (Object.keys(errs).length) return;
    onSubmit({ ...form, vpa: form.vpa.trim(), name: form.name.trim(), amount: amt.toFixed(2), categoryId: Number(form.categoryId) });
  };

  return (
    <form onSubmit={submit} className="form" noValidate>
      <Alert>{loadError}</Alert>
      {payees.length > 0 && (
        <div className="payees" role="list" aria-label="People you paid before">
          {payees.map((p) => (
            <span key={p.vpa} className={`payee-chip${p.vpa === form.vpa ? ' active' : ''}`} role="listitem">
              <button type="button" onClick={() => pick(p)}>
                <span className="avatar small" aria-hidden="true">{(p.name || p.vpa).charAt(0).toUpperCase()}</span>
                {p.name || p.vpa}
              </button>
              <button type="button" className="payee-x" aria-label={`Forget ${p.name || p.vpa}`}
                onClick={() => setPayees(forgetPayee(owner, p.vpa))}>×</button>
            </span>
          ))}
        </div>
      )}
      <Field label="Pay to (UPI ID)" error={errors.vpa} hint="You can also paste a UPI payment link.">
        <input autoFocus required autoCapitalize="none" autoCorrect="off" spellCheck={false} inputMode="email"
          placeholder="name@okhdfcbank" value={form.vpa} onChange={onVpa} />
      </Field>
      <div className="grid-2">
        <Field label="Name (optional)">
          <input maxLength={60} placeholder="e.g. Ravi's Tea Stall" value={form.name} onChange={set('name')} />
        </Field>
        <Field label="Amount (₹)" error={errors.amount}>
          <input type="number" inputMode="decimal" min="1" step="0.01" required value={form.amount} onChange={set('amount')} />
        </Field>
      </div>
      <Field label="Category" error={errors.categoryId}>
        <select required value={form.categoryId} onChange={set('categoryId')}>
          <option value="" disabled>Choose…</option>
          {categories.map((c) => <option key={c.categoryId} value={c.categoryId}>{c.name}</option>)}
        </select>
      </Field>
      <Field label="Note" hint="Shown in your UPI app and saved with the expense.">
        <textarea rows={2} maxLength={1000} placeholder="e.g. Lunch with team, Ravi owes me ₹200"
          value={form.note} onChange={set('note')} />
      </Field>
      <p className="muted small">
        Your UPI app does the payment and asks for your PIN. BudgetX never sees your bank details.
      </p>
      <div className="form-actions">
        <button type="button" className="btn btn-ghost" onClick={onCancel}>Cancel</button>
        <button className="btn btn-primary">
          {platform() === 'desktop' ? 'Show QR to pay' : `Pay ${form.amount > 0 ? money(form.amount) : ''}`.trim()}
        </button>
      </div>
    </form>
  );
}

function Handoff({ payment, onDone, onEdit }) {
  const desktop = platform() === 'desktop';
  const link = upiLink(payment);
  const [qr, setQr] = useState('');

  useEffect(() => {
    if (!desktop) return;
    QRCode.toString(link, { type: 'svg', margin: 1, errorCorrectionLevel: 'M', color: { dark: '#1f1e1c', light: '#ffffff' } })
      .then(setQr)
      .catch(() => setQr(''));
  }, [desktop, link]);

  return (
    <div className="pay-step">
      {desktop ? (
        <>
          <div className="pay-qr" aria-label={`UPI QR code to pay ${money(payment.amount)} to ${payment.name || payment.vpa}`}
            role="img" dangerouslySetInnerHTML={{ __html: qr }} />
          <p className="pay-amount">{money(payment.amount)}</p>
          <p className="center">to <strong>{payment.name || payment.vpa}</strong>
            {payment.name && <span className="muted small"> · {payment.vpa}</span>}</p>
          <p className="muted small center">Scan with GPay, PhonePe, Paytm or any UPI app on your phone, then come back here.</p>
        </>
      ) : (
        <>
          <Coins3D mode="spin" className="pay-coin" />
          <p className="pay-amount">{money(payment.amount)}</p>
          <p className="center">to <strong>{payment.name || payment.vpa}</strong></p>
          <p className="muted small center">Finish in your UPI app, then come back here. If no app opened, pick one:</p>
          <div className="pay-apps">
            <a className="btn btn-ghost" href={link}>Any UPI app</a>
            {APPS.map((a) => <a key={a.id} className="btn btn-ghost" href={appLink(a.id, payment)}>{a.name}</a>)}
          </div>
          <p className="muted small center">
            Some apps refuse payment links to personal UPI IDs. If yours does, scan the payee's QR in the app as usual
            and come back here to log it.
          </p>
        </>
      )}
      <div className="pay-actions">
        <button className="btn btn-primary btn-block" onClick={onDone}>I've paid</button>
        <button className="btn btn-ghost btn-block" onClick={onEdit}>Change details</button>
      </div>
    </div>
  );
}

function Done({ payment, logged, categories, onClose }) {
  const navigate = useNavigate();
  const cat = categories.find((c) => c.categoryId === Number(payment.categoryId));
  return (
    <div className="pay-step">
      <Coins3D mode="burst" className="pay-coin big" />
      <h3 className="pay-question">Logged {money(logged?.amount ?? payment.amount)}</h3>
      <p className="center muted">
        Paid {payment.name || payment.vpa} via UPI{cat ? `, under ${cat.name}` : ''}.
        {payment.note ? ` Note: “${payment.note.trim()}”` : ''}
      </p>
      <div className="pay-actions">
        <button className="btn btn-primary btn-block" onClick={() => { onClose(); navigate('/today'); }}>See today</button>
        <button className="btn btn-ghost btn-block" onClick={onClose}>Done</button>
      </div>
    </div>
  );
}
