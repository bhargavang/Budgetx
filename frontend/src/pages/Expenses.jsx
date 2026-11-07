import { useCallback, useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { expenses } from '../api';
import { Alert, Empty, Field, Modal, Spinner } from '../components/ui';
import { date, label, money, today } from '../format';

const METHODS = ['UPI', 'CASH', 'DEBIT_CARD', 'CREDIT_CARD', 'NET_BANKING', 'WALLET', 'OTHER'];
const PAGE_SIZE = 20;

function monthRange(ym) {
  const [y, m] = ym.split('-').map(Number);
  const last = new Date(y, m, 0).getDate();
  return { from: `${ym}-01`, to: `${ym}-${String(last).padStart(2, '0')}` };
}

export function ExpenseForm({ initial, categories, onSaved, onCancel, defaultDate }) {
  const [form, setForm] = useState(
    initial
      ? { categoryId: String(initial.categoryId), amount: initial.amount, description: initial.description || '',
          expenseDate: initial.expenseDate, paymentMethod: initial.paymentMethod || '', note: initial.note || '' }
      : { categoryId: '', amount: '', description: '', expenseDate: defaultDate || today(), paymentMethod: 'UPI', note: '' }
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
      categoryId: Number(form.categoryId),
      amount: form.amount,
      description: form.description.trim() || null,
      expenseDate: form.expenseDate,
      paymentMethod: form.paymentMethod || null,
      note: form.note.trim() || null,
    };
    try {
      if (initial) await expenses.update(initial.expenseId, body);
      else await expenses.create(body);
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
      <Field label="Amount (₹)" error={errors.amount}>
        <input type="number" inputMode="decimal" min="0.01" step="0.01" required autoFocus
          value={form.amount} onChange={set('amount')} />
      </Field>
      <Field label="Category" error={errors.categoryId}>
        <select required value={form.categoryId} onChange={set('categoryId')}>
          <option value="" disabled>Choose…</option>
          {categories.map((c) => <option key={c.categoryId} value={c.categoryId}>{c.name}</option>)}
        </select>
      </Field>
      <Field label="Description" error={errors.description}>
        <input maxLength={255} value={form.description} onChange={set('description')} placeholder="e.g. Weekly groceries" />
      </Field>
      <div className="grid-2">
        <Field label="Date" error={errors.expenseDate}>
          <input type="date" required max={today()} value={form.expenseDate} onChange={set('expenseDate')} />
        </Field>
        <Field label="Paid with" error={errors.paymentMethod}>
          <select value={form.paymentMethod} onChange={set('paymentMethod')}>
            <option value="">—</option>
            {METHODS.map((m) => <option key={m} value={m}>{label(m)}</option>)}
          </select>
        </Field>
      </div>
      <Field label="Note" error={errors.note} hint="Optional. Anything you want to remember about this spend.">
        <textarea rows={3} maxLength={1000} value={form.note} onChange={set('note')}
          placeholder="e.g. Split with Ravi, he owes me ₹200" />
      </Field>
      <div className="form-actions">
        <button type="button" className="btn btn-ghost" onClick={onCancel}>Cancel</button>
        <button className="btn btn-primary" disabled={busy}>{busy ? 'Saving…' : 'Save'}</button>
      </div>
    </form>
  );
}

export default function Expenses() {
  const [params, setParams] = useSearchParams();
  const [month, setMonth] = useState(today().slice(0, 7));
  const [categoryId, setCategoryId] = useState('');
  const [page, setPage] = useState(0);
  const [categories, setCategories] = useState([]);
  const [result, setResult] = useState(null);
  const [error, setError] = useState('');
  const [editing, setEditing] = useState(params.get('add') ? 'new' : null);

  useEffect(() => {
    expenses.categories().then(setCategories).catch((e) => setError(e.message));
  }, []);

  const load = useCallback(() => {
    setError('');
    expenses
      .list({ ...monthRange(month), categoryId, page, size: PAGE_SIZE })
      .then(setResult)
      .catch((e) => {
        setError(e.message);
        setResult({ content: [], totalPages: 0 });
      });
  }, [month, categoryId, page]);

  useEffect(load, [load]);

  const closeForm = () => {
    setEditing(null);
    if (params.get('add')) setParams({}, { replace: true });
  };

  const remove = async (e) => {
    if (!window.confirm(`Delete ${money(e.amount)} on ${e.categoryName}?`)) return;
    try {
      await expenses.remove(e.expenseId);
      load();
    } catch (err) {
      setError(err.message);
    }
  };

  const rows = result?.content || [];
  const pageTotal = rows.reduce((s, e) => s + Number(e.amount), 0);

  return (
    <>
      <div className="page-head">
        <h1>Expenses</h1>
        <button className="btn btn-primary" onClick={() => setEditing('new')}>+ Add</button>
      </div>

      <div className="filters">
        <input type="month" aria-label="Month" value={month} max={today().slice(0, 7)}
          onChange={(e) => { setMonth(e.target.value || today().slice(0, 7)); setPage(0); }} />
        <select aria-label="Category" value={categoryId} onChange={(e) => { setCategoryId(e.target.value); setPage(0); }}>
          <option value="">All categories</option>
          {categories.map((c) => <option key={c.categoryId} value={c.categoryId}>{c.name}</option>)}
        </select>
      </div>

      <Alert>{error}</Alert>

      {!result ? <Spinner /> : rows.length === 0 ? (
        <Empty title="No expenses for this period">
          <button className="btn btn-primary" onClick={() => setEditing('new')}>Add your first expense</button>
        </Empty>
      ) : (
        <section className="card flush">
          <ul className="list">
            {rows.map((e) => (
              <li key={e.expenseId} className="list-row">
                <button className="row-main" onClick={() => setEditing(e)}>
                  <div>{e.description || e.categoryName}</div>
                  <div className="muted small">
                    {e.categoryName} · {date(e.expenseDate)}{e.paymentMethod ? ` · ${label(e.paymentMethod)}` : ''}
                  </div>
                  {e.note && <div className="note-line small">📝 {e.note}</div>}
                </button>
                <span className="num">{money(e.amount)}</span>
                <button className="icon-btn" aria-label="Delete" onClick={() => remove(e)}>🗑</button>
              </li>
            ))}
          </ul>
          <div className="list-foot">
            <span className="muted small">
              {result.totalElements} expense{result.totalElements === 1 ? '' : 's'}
              {result.totalPages > 1 ? ` · this page ${money(pageTotal)}` : ` · total ${money(pageTotal)}`}
            </span>
            {result.totalPages > 1 && (
              <div className="pager">
                <button className="btn btn-ghost" disabled={page === 0} onClick={() => setPage(page - 1)}>‹ Prev</button>
                <span className="small">{page + 1} / {result.totalPages}</span>
                <button className="btn btn-ghost" disabled={page + 1 >= result.totalPages} onClick={() => setPage(page + 1)}>Next ›</button>
              </div>
            )}
          </div>
        </section>
      )}

      {editing && (
        <Modal title={editing === 'new' ? 'Add expense' : 'Edit expense'} onClose={closeForm}>
          <ExpenseForm
            initial={editing === 'new' ? null : editing}
            categories={categories}
            onCancel={closeForm}
            onSaved={() => { closeForm(); load(); }}
          />
        </Modal>
      )}
    </>
  );
}
