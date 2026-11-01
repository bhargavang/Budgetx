'use strict';
// BudgetX web app. Plain JS with no build step, served by the API gateway so it shares the API's origin.

const AUTH_KEY = 'budgetx.auth';
const LOAN_TYPES = ['HOME', 'CAR', 'TWO_WHEELER', 'PERSONAL', 'EDUCATION', 'GOLD', 'CONSUMER_DURABLE', 'CREDIT_CARD', 'OTHER'];
const PAYMENT_METHODS = ['UPI', 'CASH', 'DEBIT_CARD', 'CREDIT_CARD', 'NET_BANKING', 'WALLET', 'OTHER'];
const RISK_PROFILES = ['CONSERVATIVE', 'MODERATE', 'AGGRESSIVE'];
const FIELD_LABELS = {
  fullName: 'Full name', email: 'Email', password: 'Password', phone: 'Phone', monthlySalary: 'Monthly salary',
  categoryId: 'Category', amount: 'Amount', expenseDate: 'Date', description: 'Description',
  loanName: 'Loan name', principalAmount: 'Loan amount', interestRate: 'Interest rate',
  tenureMonths: 'Tenure', emiAmount: 'EMI', startDate: 'Start date', newPassword: 'New password',
};

const app = document.getElementById('app');
const modal = document.getElementById('modal');
const toastEl = document.getElementById('toast');

let auth = loadAuth();
let categories = null;
const expenseView = { month: currentMonth(), page: 0, categoryId: '' };

// ---------- helpers ----------

function esc(value) {
  return String(value ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

const inr = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 });
const money = value => inr.format(Number(value ?? 0));
const pretty = value => String(value ?? '').toLowerCase().replace(/_/g, ' ').replace(/^./, c => c.toUpperCase());

function localDate(d = new Date()) {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}
function currentMonth() {
  const d = new Date();
  return { year: d.getFullYear(), month: d.getMonth() + 1 };
}
function shiftMonth({ year, month }, by) {
  const d = new Date(year, month - 1 + by, 1);
  return { year: d.getFullYear(), month: d.getMonth() + 1 };
}
const monthName = ({ year, month }) => new Date(year, month - 1, 1).toLocaleDateString('en-IN', { month: 'long', year: 'numeric' });
const shortDate = iso => new Date(`${iso}T00:00:00`).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' });
const options = (values, selected) => values.map(v => `<option value="${esc(v)}"${v === selected ? ' selected' : ''}>${esc(pretty(v))}</option>`).join('');

function toast(message) {
  toastEl.textContent = message;
  toastEl.classList.add('show');
  clearTimeout(toast.timer);
  toast.timer = setTimeout(() => toastEl.classList.remove('show'), 2500);
}

// ---------- session and API ----------

function loadAuth() {
  try {
    return JSON.parse(localStorage.getItem(AUTH_KEY));
  } catch {
    return null;
  }
}

function saveAuth(value) {
  auth = value;
  try {
    if (value) localStorage.setItem(AUTH_KEY, JSON.stringify(value));
    else localStorage.removeItem(AUTH_KEY);
  } catch {
    // Storage blocked: the session simply lasts until the page is closed.
  }
}

function signOut() {
  saveAuth(null);
  categories = null;
  location.hash = '';
  render();
}

function problemMessage(data, status) {
  if (data?.errors) {
    return Object.entries(data.errors).map(([k, v]) => `${FIELD_LABELS[k] || k} ${v}`).join('. ');
  }
  if (data?.detail) return data.detail;
  if (status === 429) return 'Too many requests. Please wait a moment and try again.';
  if (status === 503) return 'BudgetX is still starting up. Please try again in a minute.';
  return `Something went wrong (error ${status}).`;
}

async function send(path, method, body, token) {
  const headers = {};
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (token) headers.Authorization = `Bearer ${token}`;
  return fetch(path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
}

let refreshing = null;
function refreshSession() {
  refreshing ??= send('/api/auth/refresh', 'POST', { refreshToken: auth.refreshToken })
    .then(async res => {
      if (!res.ok) return false;
      saveAuth(await res.json());
      return true;
    })
    .catch(() => false)
    .finally(() => { refreshing = null; });
  return refreshing;
}

async function api(path, method = 'GET', body) {
  let res = await send(path, method, body, auth?.accessToken);
  if (res.status === 401 && auth?.refreshToken) {
    if (await refreshSession()) {
      res = await send(path, method, body, auth.accessToken);
    } else {
      signOut();
      throw new Error('Your session expired. Please sign in again.');
    }
  }
  if (res.status === 204) return null;
  const data = await res.json().catch(() => null);
  if (!res.ok) throw new Error(problemMessage(data, res.status));
  return data;
}

async function getCategories() {
  categories ??= await api('/api/expense-categories');
  return categories;
}

// ---------- forms ----------

/** Collects a form into an object, dropping empty fields so optional values stay null. */
function formValues(form) {
  return Object.fromEntries([...new FormData(form).entries()].map(([k, v]) => [k, v.trim()]).filter(([, v]) => v !== ''));
}

function bindForm(form, onSubmit) {
  form.addEventListener('submit', async event => {
    event.preventDefault();
    const button = form.querySelector('button[type=submit]');
    const error = form.querySelector('.form-error');
    error.textContent = '';
    button.disabled = true;
    try {
      await onSubmit(formValues(form));
    } catch (e) {
      error.textContent = e.message === 'Failed to fetch' ? 'Could not reach BudgetX. Check your connection.' : e.message;
    } finally {
      button.disabled = false;
    }
  });
}

function openModal(html, onSubmit) {
  modal.innerHTML = html;
  const form = modal.querySelector('form');
  bindForm(form, onSubmit);
  form.querySelector('[data-cancel]').addEventListener('click', () => modal.close());
  modal.showModal();
}

function confirmAction(title, detail, label, action) {
  openModal(`<form>
    <h2>${esc(title)}</h2>
    <p class="muted">${esc(detail)}</p>
    <p class="form-error"></p>
    <div class="actions"><button type="button" data-cancel>Cancel</button><button type="submit" class="primary">${esc(label)}</button></div>
  </form>`, async () => {
    await action();
    modal.close();
  });
}

// ---------- sign in / register ----------

function renderAuth(mode = 'login') {
  const login = mode === 'login';
  const notice = location.protocol === 'http:' && location.hostname !== 'localhost'
    ? '<p class="notice">This test site uses plain HTTP. Use a password you don\'t use anywhere else.</p>' : '';
  app.innerHTML = `<main class="auth">
    <a class="brand" href="#">Budget<span>X</span></a>
    <p class="muted">Track your spending and loans in one place.</p>
    <div class="card">
      <h1>${login ? 'Sign in' : 'Create your account'}</h1><br>
      <form>
        ${login ? '' : '<label>Full name<input name="fullName" required maxlength="100" autocomplete="name"></label>'}
        <label>Email<input name="email" type="email" required autocomplete="email"></label>
        <label>Password ${login ? '' : '<span class="hint">(at least 8 characters)</span>'}
          <input name="password" type="password" required ${login ? '' : 'minlength="8" maxlength="72"'} autocomplete="${login ? 'current-password' : 'new-password'}"></label>
        ${login ? '' : '<label>Monthly salary (₹)<input name="monthlySalary" type="number" min="0" step="0.01" required inputmode="decimal"></label>'}
        <p class="form-error"></p>
        <button type="submit" class="primary" style="width:100%">${login ? 'Sign in' : 'Create account'}</button>
      </form>
      <p class="switch">${login ? 'New here?' : 'Already registered?'} <button type="button" class="linkish" id="switch">${login ? 'Create an account' : 'Sign in'}</button></p>
      ${notice}
    </div>
  </main>`;
  app.querySelector('#switch').addEventListener('click', () => renderAuth(login ? 'register' : 'login'));
  bindForm(app.querySelector('form'), async values => {
    saveAuth(await api(login ? '/api/auth/login' : '/api/auth/register', 'POST', values));
    location.hash = '#/dashboard';
    render();
  });
}

// ---------- shell ----------

const PAGES = {
  dashboard: { label: 'Dashboard', render: renderDashboard },
  expenses: { label: 'Expenses', render: renderExpenses },
  loans: { label: 'Loans', render: renderLoans },
  profile: { label: 'Profile', render: renderProfile },
};

function currentPage() {
  const name = location.hash.replace(/^#\/?/, '');
  return PAGES[name] ? name : 'dashboard';
}

function render() {
  if (!auth) {
    renderAuth();
    return;
  }
  const page = currentPage();
  app.innerHTML = `<div class="shell">
    <header class="topbar">
      <a class="brand" href="#/dashboard">Budget<span>X</span></a>
      <nav>${Object.entries(PAGES).map(([key, p]) =>
        `<a href="#/${key}"${key === page ? ' aria-current="page"' : ''}>${p.label}</a>`).join('')}</nav>
    </header>
    <main id="view"><p class="empty">Loading…</p></main>
  </div>`;
  refreshView();
}

/** Re-renders the current page, ignoring results from a page the user has since left. */
async function refreshView() {
  const page = currentPage();
  const view = document.getElementById('view');
  if (!view) return;
  try {
    const html = await PAGES[page].render();
    if (document.getElementById('view') !== view || currentPage() !== page) return;
    view.innerHTML = html;
    PAGES[page].bind?.(view);
  } catch (e) {
    if (document.getElementById('view') === view) {
      view.innerHTML = `<div class="card"><p class="form-error">${esc(e.message)}</p><button id="retry">Try again</button></div>`;
      view.querySelector('#retry').addEventListener('click', refreshView);
    }
  }
}

window.addEventListener('hashchange', render);

// ---------- dashboard ----------

function barRow(label, amount, max, cls = '') {
  const pct = max > 0 ? Math.min(100, (amount / max) * 100) : 0;
  return `<div class="bar-row">
    <div class="labels"><span>${esc(label)}</span><span class="num">${money(amount)}</span></div>
    <div class="bar"><i class="${cls}" style="width:${pct.toFixed(1)}%"></i></div>
  </div>`;
}

async function renderDashboard() {
  const now = currentMonth();
  const [user, month, emi, recent] = await Promise.all([
    api('/api/users/me'),
    api(`/api/expenses/summary/month?month=${now.month}&year=${now.year}`),
    api('/api/emis/summary'),
    api('/api/expenses?size=5'),
  ]);
  const salary = Number(user.monthlySalary ?? 0);
  const spent = Number(month.total ?? 0);
  const emis = Number(emi.monthlyEmiTotal ?? 0);
  const left = salary - spent - emis;
  const committed = spent + emis;

  const categoryRows = [...month.byCategory].sort((a, b) => b.total - a.total);
  const maxCategory = Math.max(0, ...categoryRows.map(c => Number(c.total)));

  return `<div class="page-head"><div><h1>Hi, ${esc(user.fullName.split(' ')[0])}</h1><span class="muted">${esc(monthName(now))}</span></div>
      <button class="primary" id="add">Add expense</button></div>
    <section class="grid stats">
      <div class="card stat"><div class="label">Monthly salary</div><div class="value num">${money(salary)}</div></div>
      <div class="card stat"><div class="label">Spent this month</div><div class="value num">${money(spent)}</div></div>
      <div class="card stat"><div class="label">Loan EMIs</div><div class="value num">${money(emis)}</div></div>
      <div class="card stat"><div class="label">Left this month</div><div class="value num ${left < 0 ? 'neg' : ''}">${money(left)}</div></div>
    </section>
    <section class="grid two">
      <div class="card">
        <h2>Where your salary is going</h2>
        ${barRow('Spending and EMIs', committed, salary, committed > salary ? 'over' : '')}
        ${barRow('Needs', month.essentialTotal, salary)}
        ${barRow('Wants', month.discretionaryTotal, salary, 'want')}
        <p class="muted" style="margin:0;font-size:.85rem">${emi.activeLoans} active ${emi.activeLoans === 1 ? 'loan' : 'loans'}, ${money(emi.totalOutstandingPrincipal)} outstanding</p>
      </div>
      <div class="card">
        <h2>By category</h2>
        ${categoryRows.length ? categoryRows.map(c => barRow(c.categoryName, c.total, maxCategory, c.essential ? '' : 'want')).join('') : '<p class="empty">No spending yet this month.</p>'}
      </div>
    </section>
    <section class="card" style="margin-top:16px">
      <h2>Recent expenses</h2>
      ${recent.content.length ? `<ul class="list">${recent.content.map(expenseRow).join('')}</ul>` : '<p class="empty">Add your first expense to get started.</p>'}
    </section>`;
}
PAGES.dashboard.bind = view => view.querySelector('#add').addEventListener('click', () => expenseForm());

// ---------- expenses ----------

function expenseRow(e, actions = false) {
  return `<li class="item">
    <div class="main">
      <div class="title">${esc(e.description || e.categoryName)}</div>
      <div class="sub">${esc(e.categoryName)} · ${esc(shortDate(e.expenseDate))}${e.paymentMethod ? ` · ${esc(pretty(e.paymentMethod))}` : ''}</div>
    </div>
    <div class="amount num">${money(e.amount)}</div>
    ${actions === true ? `<div class="btns"><button class="small" data-edit="${e.expenseId}">Edit</button><button class="small danger" data-delete="${e.expenseId}">Delete</button></div>` : ''}
  </li>`;
}

async function expenseForm(expense) {
  const cats = await getCategories();
  openModal(`<form>
    <h2>${expense ? 'Edit expense' : 'Add expense'}</h2>
    <div class="row">
      <label>Amount (₹)<input name="amount" type="number" min="0.01" step="0.01" required inputmode="decimal" value="${esc(expense?.amount)}"></label>
      <label>Date<input name="expenseDate" type="date" required max="${localDate()}" value="${esc(expense?.expenseDate ?? localDate())}"></label>
    </div>
    <label>Category<select name="categoryId" required>${cats.map(c =>
      `<option value="${c.categoryId}"${c.categoryId === expense?.categoryId ? ' selected' : ''}>${esc(c.name)}</option>`).join('')}</select></label>
    <label>Description <span class="hint">(optional)</span><input name="description" maxlength="255" value="${esc(expense?.description)}"></label>
    <label>Paid with<select name="paymentMethod">${options(PAYMENT_METHODS, expense?.paymentMethod)}</select></label>
    <p class="form-error"></p>
    <div class="actions"><button type="button" data-cancel>Cancel</button><button type="submit" class="primary">Save</button></div>
  </form>`, async values => {
    await (expense ? api(`/api/expenses/${expense.expenseId}`, 'PUT', values) : api('/api/expenses', 'POST', values));
    modal.close();
    toast(expense ? 'Expense updated' : 'Expense added');
    refreshView();
  });
}

let expensesOnPage = [];

async function renderExpenses() {
  const { month: selected, page, categoryId } = expenseView;
  const first = localDate(new Date(selected.year, selected.month - 1, 1));
  const last = localDate(new Date(selected.year, selected.month, 0));
  const query = new URLSearchParams({ from: first, to: last, page, size: 20 });
  if (categoryId) query.set('categoryId', categoryId);
  const [result, summary, cats] = await Promise.all([
    api(`/api/expenses?${query}`),
    api(`/api/expenses/summary/month?month=${selected.month}&year=${selected.year}`),
    getCategories(),
  ]);
  expensesOnPage = result.content;
  const isCurrent = selected.year === currentMonth().year && selected.month === currentMonth().month;
  return `<div class="page-head"><h1>Expenses</h1><button class="primary" id="add">Add expense</button></div>
    <section class="card">
      <div class="toolbar">
        <div class="months">
          <button class="small" id="prev" aria-label="Previous month">‹</button>
          <strong>${esc(monthName(selected))}</strong>
          <button class="small" id="next" aria-label="Next month" ${isCurrent ? 'disabled' : ''}>›</button>
        </div>
        <label>Category<select id="filter"><option value="">All categories</option>${cats.map(c =>
          `<option value="${c.categoryId}"${String(c.categoryId) === categoryId ? ' selected' : ''}>${esc(c.name)}</option>`).join('')}</select></label>
      </div>
      <p class="muted">Total for the month: <strong class="num">${money(summary.total)}</strong></p>
      ${result.content.length ? `<ul class="list">${result.content.map(e => expenseRow(e, true)).join('')}</ul>` : '<p class="empty">No expenses here.</p>'}
      ${result.totalPages > 1 ? `<div class="pager">
        <button class="small" id="older" ${page === 0 ? 'disabled' : ''}>Previous</button>
        <span class="muted">Page ${page + 1} of ${result.totalPages}</span>
        <button class="small" id="newer" ${page + 1 >= result.totalPages ? 'disabled' : ''}>Next</button>
      </div>` : ''}
    </section>`;
}

PAGES.expenses.bind = view => {
  const goTo = changes => {
    Object.assign(expenseView, changes);
    refreshView();
  };
  view.querySelector('#add').addEventListener('click', () => expenseForm());
  view.querySelector('#prev').addEventListener('click', () => goTo({ month: shiftMonth(expenseView.month, -1), page: 0 }));
  view.querySelector('#next').addEventListener('click', () => goTo({ month: shiftMonth(expenseView.month, 1), page: 0 }));
  view.querySelector('#filter').addEventListener('change', e => goTo({ categoryId: e.target.value, page: 0 }));
  view.querySelector('#older')?.addEventListener('click', () => goTo({ page: expenseView.page - 1 }));
  view.querySelector('#newer')?.addEventListener('click', () => goTo({ page: expenseView.page + 1 }));
  view.querySelectorAll('[data-edit]').forEach(b => b.addEventListener('click', () =>
    expenseForm(expensesOnPage.find(e => String(e.expenseId) === b.dataset.edit))));
  view.querySelectorAll('[data-delete]').forEach(b => b.addEventListener('click', () => {
    const expense = expensesOnPage.find(e => String(e.expenseId) === b.dataset.delete);
    confirmAction('Delete this expense?', `${expense.description || expense.categoryName}, ${money(expense.amount)}. This can't be undone.`, 'Delete', async () => {
      await api(`/api/expenses/${expense.expenseId}`, 'DELETE');
      toast('Expense deleted');
      refreshView();
    });
  }));
};

// ---------- loans ----------

function loanForm(loan) {
  openModal(`<form>
    <h2>${loan ? 'Edit loan' : 'Add loan'}</h2>
    <label>Loan name<input name="loanName" required maxlength="100" value="${esc(loan?.loanName)}"></label>
    <div class="row">
      <label>Lender <span class="hint">(optional)</span><input name="lenderName" maxlength="100" value="${esc(loan?.lenderName)}"></label>
      <label>Type<select name="loanType">${options(LOAN_TYPES, loan?.loanType)}</select></label>
    </div>
    <div class="row">
      <label>Loan amount (₹)<input name="principalAmount" type="number" min="0.01" step="0.01" required inputmode="decimal" value="${esc(loan?.principalAmount)}"></label>
      <label>Interest (% a year)<input name="interestRate" type="number" min="0" max="60" step="0.01" required inputmode="decimal" value="${esc(loan?.interestRate)}"></label>
    </div>
    <div class="row">
      <label>Tenure (months)<input name="tenureMonths" type="number" min="1" max="480" step="1" required inputmode="numeric" value="${esc(loan?.tenureMonths)}"></label>
      <label>First EMI date<input name="startDate" type="date" required value="${esc(loan?.startDate ?? localDate())}"></label>
    </div>
    <label>Monthly EMI (₹) <span class="hint">(leave blank to calculate it)</span>
      <input name="emiAmount" type="number" min="0.01" step="0.01" inputmode="decimal" value="${esc(loan?.emiAmount)}"></label>
    <p class="form-error"></p>
    <div class="actions"><button type="button" data-cancel>Cancel</button><button type="submit" class="primary">Save</button></div>
  </form>`, async values => {
    await (loan ? api(`/api/emis/${loan.emiId}`, 'PUT', values) : api('/api/emis', 'POST', values));
    modal.close();
    toast(loan ? 'Loan updated' : 'Loan added');
    refreshView();
  });
}

let loansOnPage = [];

function loanRow(l) {
  const active = l.status !== 'CLOSED';
  const progress = l.tenureMonths ? (l.instalmentsPaid / l.tenureMonths) * 100 : 0;
  return `<li class="item">
    <div class="main">
      <div class="title">${esc(l.loanName)}<span class="tag ${active ? '' : 'closed'}">${esc(pretty(l.status))}</span></div>
      <div class="sub">${esc([pretty(l.loanType), l.lenderName, `${l.interestRate}% a year`].filter(Boolean).join(' · '))}</div>
      <div class="sub">${l.instalmentsPaid} of ${l.tenureMonths} EMIs paid · ${money(l.outstandingPrincipal)} left · ends ${esc(shortDate(l.endDate))}</div>
      <div class="bar"><i style="width:${progress.toFixed(1)}%"></i></div>
    </div>
    <div class="amount num">${money(l.emiAmount)}/mo</div>
    <div class="btns">
      <button class="small" data-edit="${l.emiId}">Edit</button>
      ${active ? `<button class="small" data-close="${l.emiId}">Close</button>` : ''}
      <button class="small danger" data-delete="${l.emiId}">Delete</button>
    </div>
  </li>`;
}

async function renderLoans() {
  const [loans, summary] = await Promise.all([api('/api/emis'), api('/api/emis/summary')]);
  loansOnPage = loans;
  return `<div class="page-head"><h1>Loans</h1><button class="primary" id="add">Add loan</button></div>
    <section class="grid stats">
      <div class="card stat"><div class="label">Active loans</div><div class="value num">${summary.activeLoans}</div></div>
      <div class="card stat"><div class="label">Monthly EMIs</div><div class="value num">${money(summary.monthlyEmiTotal)}</div></div>
      <div class="card stat"><div class="label">Total outstanding</div><div class="value num">${money(summary.totalOutstandingPrincipal)}</div></div>
    </section>
    <section class="card">
      ${loans.length ? `<ul class="list">${loans.map(loanRow).join('')}</ul>` : '<p class="empty">No loans added yet.</p>'}
    </section>`;
}

PAGES.loans.bind = view => {
  const find = id => loansOnPage.find(l => String(l.emiId) === id);
  view.querySelector('#add').addEventListener('click', () => loanForm());
  view.querySelectorAll('[data-edit]').forEach(b => b.addEventListener('click', () => loanForm(find(b.dataset.edit))));
  view.querySelectorAll('[data-close]').forEach(b => b.addEventListener('click', () => {
    const loan = find(b.dataset.close);
    confirmAction('Mark this loan as closed?', `${loan.loanName} will stop counting towards your monthly EMIs.`, 'Close loan', async () => {
      await api(`/api/emis/${loan.emiId}/close`, 'PATCH');
      toast('Loan closed');
      refreshView();
    });
  }));
  view.querySelectorAll('[data-delete]').forEach(b => b.addEventListener('click', () => {
    const loan = find(b.dataset.delete);
    confirmAction('Delete this loan?', `${loan.loanName} will be removed. This can't be undone.`, 'Delete', async () => {
      await api(`/api/emis/${loan.emiId}`, 'DELETE');
      toast('Loan deleted');
      refreshView();
    });
  }));
};

// ---------- profile ----------

async function renderProfile() {
  const u = await api('/api/users/me');
  return `<div class="page-head"><h1>Profile</h1><button id="signout">Sign out</button></div>
    <section class="grid two">
      <div class="card">
        <h2>Your details</h2>
        <form id="details">
          <label>Full name<input name="fullName" required maxlength="100" value="${esc(u.fullName)}"></label>
          <label>Email<input value="${esc(u.email)}" disabled></label>
          <label>Phone <span class="hint">(optional)</span><input name="phone" type="tel" value="${esc(u.phone)}"></label>
          <label>Occupation <span class="hint">(optional)</span><input name="occupation" maxlength="100" value="${esc(u.occupation)}"></label>
          <label>Monthly salary (₹)<input name="monthlySalary" type="number" min="0" step="0.01" required inputmode="decimal" value="${esc(u.monthlySalary)}"></label>
          <label>Risk profile<select name="riskProfile">${options(RISK_PROFILES, u.riskProfile)}</select></label>
          <p class="form-error"></p>
          <div class="actions"><button type="submit" class="primary">Save changes</button></div>
        </form>
      </div>
      <div class="card">
        <h2>Change password</h2>
        <form id="password">
          <label>Current password<input name="currentPassword" type="password" required autocomplete="current-password"></label>
          <label>New password <span class="hint">(at least 8 characters)</span>
            <input name="newPassword" type="password" required minlength="8" maxlength="72" autocomplete="new-password"></label>
          <p class="form-error"></p>
          <div class="actions"><button type="submit" class="primary">Change password</button></div>
        </form>
      </div>
    </section>`;
}

PAGES.profile.bind = view => {
  view.querySelector('#signout').addEventListener('click', signOut);
  bindForm(view.querySelector('#details'), async values => {
    await api('/api/users/me', 'PATCH', values);
    toast('Profile saved');
  });
  const passwordForm = view.querySelector('#password');
  bindForm(passwordForm, async values => {
    await api('/api/users/me/password', 'PUT', values);
    passwordForm.reset();
    toast('Password changed');
  });
};

render();
