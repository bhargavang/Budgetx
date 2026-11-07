// Demo mode: a pretend BudgetX backend that runs inside the browser, so the app can be hosted as a
// static site (Netlify) and shown without the Java services. It answers the same /api routes with
// the same JSON shapes, starts with sample data, and keeps changes in this browser's localStorage.
// Only bundled when the site is built with VITE_DEMO=true.

const KEY = 'budgetx.demo';

const CATEGORIES = [
  [1, 'Rent', 'Rent and maintenance', true], [2, 'Groceries', 'Food and household supplies', true],
  [3, 'Utilities', 'Electricity, water, gas, internet', true], [4, 'Transport', 'Fuel, public transport, cabs', true],
  [5, 'Healthcare', 'Medicines, consultations', true], [6, 'Education', 'Fees, books, courses', true],
  [7, 'Insurance', 'Premiums not tracked as investments', true], [8, 'Mobile', 'Mobile and DTH recharges', true],
  [9, 'Dining Out', 'Restaurants and food delivery', false], [10, 'Entertainment', 'Movies, streaming, events', false],
  [11, 'Shopping', 'Clothing, electronics, personal items', false], [12, 'Travel', 'Trips and holidays', false],
  [13, 'Personal Care', 'Salon, grooming, fitness', false], [14, 'Gifts', 'Gifts and donations', false],
  [15, 'Other', 'Anything else', false],
].map(([categoryId, name, description, essential]) => ({ categoryId, name, description, essential }));
const cat = (id) => CATEGORIES.find((c) => c.categoryId === Number(id));

const pad = (n) => String(n).padStart(2, '0');
const iso = (d) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
const parse = (s) => new Date(`${s}T00:00:00`);
const todayIso = () => iso(new Date());
const round2 = (n) => Math.round(n * 100) / 100;
const down2 = (n) => Math.floor(n * 100) / 100;
const daysIn = (y, m) => new Date(y, m, 0).getDate();
const addMonths = (s, n) => { const d = parse(s); d.setMonth(d.getMonth() + n); return iso(d); };

function emiAmount(p, rate, months) {
  const r = rate / 1200;
  if (!r) return round2(p / months);
  return round2((p * r * (1 + r) ** months) / ((1 + r) ** months - 1));
}

// ---------- sample data, dated relative to today so it always looks current ----------
function seed() {
  const now = new Date();
  const expenses = [];
  let id = 1;
  const add = (date, categoryId, amount, description, paymentMethod = 'UPI', note = null, allowToday = false) => {
    if (iso(date) > todayIso() || (!allowToday && iso(date) === todayIso())) return;
    expenses.push({ expenseId: id++, categoryId, amount, description, expenseDate: iso(date), paymentMethod, note });
  };
  // the last 12 months of everyday spends (rent and bills live in monthly expenses instead)
  for (let back = 11; back >= 0; back--) {
    const y = now.getFullYear();
    const m = now.getMonth() - back;
    const d = (day) => new Date(y, m, Math.min(day, daysIn(new Date(y, m, 1).getFullYear(), new Date(y, m, 1).getMonth() + 1)));
    const k = (back * 37) % 11;
    add(d(6), 2, 2100 + k * 90, 'BigBasket order');
    add(d(9), 9, 640 + k * 30, 'Swiggy dinner');
    add(d(12), 4, 500, 'Metro card top-up');
    add(d(14), 11, 1200 + k * 150, 'Myntra order', 'CREDIT_CARD');
    add(d(17), 2, 1850 + k * 60, 'D-Mart groceries');
    add(d(20), 10, 499, 'Movie tickets');
    add(d(23), 9, 420 + k * 25, 'Lunch with team');
    add(d(26), 4, 350 + k * 20, 'Ola rides');
    add(d(28), 13, 400, 'Haircut');
  }
  // a little more detail this week so the Today tab has something to show
  const day = (n) => new Date(now.getFullYear(), now.getMonth(), now.getDate() - n);
  if (now.getDate() > 1) add(day(1), 9, 260, 'Chai and samosa', 'CASH', 'Evening snack with friends');
  add(day(0), 2, 340, 'Vegetables and milk', 'UPI', 'Weekly veg run', true);
  add(day(0), 4, 120, 'Auto to office', 'CASH', null, true);

  const start = addMonths(iso(now), -14).slice(0, 8) + '05';
  return {
    user: {
      userId: 1, fullName: 'Demo User', email: 'demo@budgetx.app', phone: null, dateOfBirth: '1996-04-12',
      gender: null, occupation: 'Software engineer', monthlySalary: 85000, riskProfile: 'MODERATE',
      createdAt: new Date(now.getFullYear() - 1, now.getMonth(), 1).toISOString(),
    },
    expenses,
    nextExpenseId: id,
    emis: [
      { emiId: 1, loanName: 'Bike loan', lenderName: 'HDFC Bank', loanType: 'TWO_WHEELER', principalAmount: 120000,
        interestRate: 10.5, tenureMonths: 24, emiAmount: emiAmount(120000, 10.5, 24), startDate: start, status: 'ACTIVE' },
      { emiId: 2, loanName: 'Phone on EMI', lenderName: 'Bajaj Finserv', loanType: 'CONSUMER_DURABLE', principalAmount: 60000,
        interestRate: 0, tenureMonths: 12, emiAmount: 5000, startDate: addMonths(iso(now), -4).slice(0, 8) + '10', status: 'ACTIVE' },
    ],
    nextEmiId: 3,
    savingsGoal: 10000,
    monthly: seedMonthly(),
    nextMonthlyId: 8,
    notes: {},
    safety: null,
    guidance: null,
  };
}

function seedMonthly() {
  return [
    [1, 'Flat rent', 14000, 'RENT', 1, 'Pay Mr. Rao by UPI, he sends the receipt on WhatsApp'],
    [2, 'Maid', 3000, 'HOUSEHOLD_HELP', 1, null],
    [3, 'Jio fiber + mobile', 1299, 'PHONE_INTERNET', 3, null],
    [4, 'Electricity', 1600, 'UTILITIES', 8, 'Roughly this; more in summer with the AC'],
    [5, 'Send home', 5000, 'FAMILY_SUPPORT', 10, 'For Amma and Appa'],
    [6, 'Netflix', 199, 'SUBSCRIPTION', 18, 'Mobile plan, renews automatically'],
    [7, 'Gym membership', 1500, 'OTHER', 5, 'Paused while travelling for work', false],
  ].map(([monthlyExpenseId, name, amount, type, dueDay, note, active = true]) =>
    ({ monthlyExpenseId, name, amount, type, dueDay, note, active }));
}

let db = null;
function load() {
  if (db) return db;
  try { db = JSON.parse(localStorage.getItem(KEY)); } catch { db = null; }
  if (!db) { db = seed(); save(); }
  if (!db.monthly) { db.monthly = []; db.nextMonthlyId = 1; } // demo data saved before monthly expenses existed
  return db;
}
function save() {
  try { localStorage.setItem(KEY, JSON.stringify(db)); } catch { /* private mode: lasts for this visit */ }
}
export function resetDemo() {
  db = seed();
  save();
}

// ---------- derived views, mirroring the Java services ----------
const expenseView = (e) => ({ ...e, categoryName: cat(e.categoryId)?.name || 'Other' });

function emiView(e, asOf = todayIso()) {
  const s = parse(e.startDate);
  const a = parse(asOf);
  let paid = (a.getFullYear() - s.getFullYear()) * 12 + (a.getMonth() - s.getMonth()) + (a.getDate() >= s.getDate() ? 1 : 0);
  paid = e.status === 'CLOSED' ? e.tenureMonths : Math.max(0, Math.min(e.tenureMonths, paid));
  const r = e.interestRate / 1200;
  let bal = e.principalAmount;
  for (let i = 0; i < paid; i++) bal = bal * (1 + r) - e.emiAmount;
  const endDate = addMonths(e.startDate, e.tenureMonths - 1);
  return { ...e, endDate, instalmentsPaid: paid, instalmentsRemaining: e.tenureMonths - paid,
    outstandingPrincipal: e.status === 'CLOSED' ? 0 : round2(Math.max(0, bal)) };
}

const activeEmis = () => load().emis.map((e) => emiView(e)).filter((e) => e.status === 'ACTIVE' && e.instalmentsRemaining > 0);
const monthlyEmi = () => round2(activeEmis().reduce((s, e) => s + e.emiAmount, 0));
const inRange = (from, to) => load().expenses.filter((e) => e.expenseDate >= from && e.expenseDate <= to);
const monthlyTotal = () => round2(load().monthly.filter((x) => x.active).reduce((s, x) => s + x.amount, 0));
const monthlyOrder = (a, b) => (a.active === b.active ? 0 : a.active ? -1 : 1)
  || (a.dueDay ?? 99) - (b.dueDay ?? 99) || b.amount - a.amount;
const monthRange = (y, m) => [`${y}-${pad(m)}-01`, `${y}-${pad(m)}-${pad(daysIn(y, m))}`];

function monthSummary(month, year) {
  const list = inRange(...monthRange(year, month));
  const by = new Map();
  list.forEach((e) => {
    const c = cat(e.categoryId);
    const row = by.get(c.categoryId) || { categoryId: c.categoryId, categoryName: c.name, essential: c.essential, total: 0, count: 0 };
    row.total = round2(row.total + e.amount);
    row.count += 1;
    by.set(c.categoryId, row);
  });
  const rows = [...by.values()].sort((a, b) => b.total - a.total);
  const sum = (f) => round2(rows.filter(f).reduce((s, r) => s + r.total, 0));
  return { month, year, total: sum(() => true), essentialTotal: sum((r) => r.essential), discretionaryTotal: sum((r) => !r.essential), byCategory: rows };
}

function dailySummary(date) {
  const d = load();
  const day = parse(date);
  const y = day.getFullYear();
  const m = day.getMonth() + 1;
  const dim = daysIn(y, m);
  const daysLeft = dim - day.getDate() + 1;
  const salary = Number(d.user.monthlySalary || 0);
  const emi = monthlyEmi();
  const fixed = monthlyTotal();
  const spendable = Math.max(0, salary - emi - d.savingsGoal - fixed);
  const [from, to] = monthRange(y, m);
  const before = inRange(from, date).filter((e) => e.expenseDate < date).reduce((s, e) => s + e.amount, 0);
  const allowance = down2(Math.max(0, spendable - before) / daysLeft);
  const todays = inRange(date, date).map(expenseView);
  const spentToday = round2(todays.reduce((s, e) => s + e.amount, 0));
  const spentMonth = round2(inRange(from, to).reduce((s, e) => s + e.amount, 0));
  return {
    date, monthlySalary: salary, monthlyEmi: emi, emiAvailable: true, monthlySavingsGoal: d.savingsGoal,
    monthlyExpenses: fixed, spendableThisMonth: spendable, daysInMonth: dim, daysLeftInMonth: daysLeft, dailyBudget: down2(spendable / dim),
    todaysAllowance: allowance, spentToday, remainingToday: round2(allowance - spentToday), spentThisMonth: spentMonth,
    remainingThisMonth: round2(spendable - spentMonth), dayNote: d.notes[date] || null, expenses: todays,
  };
}

// Rule-based guidance, in the same shape (and the same spirit: education, no fund picks) as ai-agent-service.
function makeGuidance() {
  const d = load();
  const now = new Date();
  const salary = Number(d.user.monthlySalary || 0);
  const emi = monthlyEmi();
  const ms = monthSummary(now.getMonth() + 1, now.getFullYear());
  const past = [1, 2, 3].map((k) => { const t = new Date(now.getFullYear(), now.getMonth() - k, 1); return monthSummary(t.getMonth() + 1, t.getFullYear()).total; });
  const spending = round2(Math.max(ms.total, past.reduce((a, b) => a + b, 0) / 3) + monthlyTotal());
  const disposable = round2(salary - emi - spending);
  const s = d.safety;
  const monthlyNeed = spending + emi;
  const target = monthlyNeed * 6;
  const gap = Math.max(0, target - (s?.emergencySavings || 0));
  const investable = Math.max(0, round2(disposable * 0.8));
  const emergency = gap > 0 ? round2(Math.min(investable * 0.5, gap)) : 0;
  const toInvest = round2(investable - emergency);
  const cap = { CONSERVATIVE: 30, MODERATE: 60, AGGRESSIVE: 80 }[d.user.riskProfile] || 60;
  const warnings = [];
  const emiRatio = salary ? (emi / salary) * 100 : 0;
  if (!s) warnings.push({ severity: 'WARNING', title: 'Safety net not filled in', detail: 'Tell BudgetX about your emergency savings and insurance so the plan can put protection first.' });
  if (s && !s.hasHealthInsurance) warnings.push({ severity: 'CRITICAL', title: 'No health insurance', detail: 'One hospital stay can wipe out years of savings. A family floater health policy usually comes before investing.' });
  if (s && s.dependents > 0 && !s.hasTermInsurance) warnings.push({ severity: 'CRITICAL', title: 'No term insurance with dependents', detail: 'Pure term cover of about 10-15x your yearly income protects the people who rely on you. Avoid policies that mix insurance and investment.' });
  if (gap > 0) warnings.push({ severity: 'WARNING', title: 'Emergency fund below 6 months', detail: `Aim for about ₹${Math.round(target).toLocaleString('en-IN')} (6 months of spending and EMIs) in a savings account or liquid fund.` });
  if (emiRatio > 40) warnings.push({ severity: 'WARNING', title: 'EMIs are a large share of salary', detail: `EMIs take ${Math.round(emiRatio)}% of your salary. Lenders and planners usually suggest staying under 40%.` });
  if (ms.discretionaryTotal > spending * 0.35) warnings.push({ severity: 'INFO', title: 'Wants are over a third of spending', detail: 'Dining out, shopping and entertainment are the easiest places to free up money for goals.' });
  const eq = Math.min(cap, 60);
  const alloc = (type, title, assetClass, riskLevel, horizon, percent, about, reason) =>
    ({ type, title, assetClass, riskLevel, horizon, percent, monthlyAmount: round2((toInvest * percent) / 100), about, reason });
  const allocations = toInvest > 0 ? [
    alloc('INDEX_FUND_SIP', 'Index fund SIP', 'EQUITY', 'HIGH', '5+ years', eq,
      'A fixed monthly amount into a low-cost mutual fund that tracks a broad index such as the Nifty 50. Its value can fall sharply in the short term.',
      `Long-term growth, kept within your ${cap}% equity limit for a ${d.user.riskProfile.toLowerCase()} profile.`),
    alloc('PPF', 'Public Provident Fund (PPF)', 'DEBT', 'LOW', '15 years', Math.round((100 - eq) * 0.6),
      'A government-backed savings scheme at post offices and banks, with tax benefits. Money is locked for 15 years.',
      'A steady, safe base that balances the equity part.'),
    alloc('DEBT_FUND', 'Short-duration debt or liquid fund', 'DEBT', 'LOW_TO_MEDIUM', '1-3 years', 100 - eq - Math.round((100 - eq) * 0.6),
      'Mutual funds that lend to the government and companies. Steadier than equity but not risk-free.',
      'For goals one to three years away.'),
  ] : [];
  const actions = [];
  if (emergency > 0) actions.push({ type: 'EMERGENCY_FUND', title: 'Build your emergency fund', detail: 'Move this into a separate savings account or liquid fund every month until you reach 6 months of expenses.', monthlyAmount: emergency });
  if (toInvest > 0) actions.push({ type: 'INVEST', title: 'Invest the rest every month', detail: 'Set up automatic transfers on salary day so investing happens before spending.', monthlyAmount: toInvest });
  actions.push({ type: 'REVIEW', title: 'Review wants once a month', detail: 'Check the Expenses tab at month end and pick one category to trim next month.', monthlyAmount: null });
  const id = (d.guidance?.id || 0) + 1;
  d.guidance = {
    id, generatedAt: new Date().toISOString(), mode: 'RULES', modeNote: 'Demo: rule-based guidance computed in your browser',
    riskProfile: d.user.riskProfile, equityCapPercent: cap,
    budget: { month: now.getMonth() + 1, year: now.getFullYear(), monthlySalary: salary, totalEmi: emi, spendingEstimate: spending,
      disposableIncome: disposable, investableAmount: investable, emergencyFundContribution: emergency, amountToInvest: toInvest },
    safetyProfileMissing: !s, warnings, actions, allocations,
    summary: disposable > 0
      ? `After EMIs and your usual spending, about ₹${Math.round(disposable).toLocaleString('en-IN')} is left each month. Protect first, then invest steadily.`
      : 'Your spending and EMIs use up your salary this month, so the first step is to free up some room.',
    principles: ['Insurance and an emergency fund come before investing.', 'Keep costs low: prefer direct plans and index funds.', 'Invest regularly and stay invested through ups and downs.'],
    disclaimer: 'Educational guidance only, not investment advice. BudgetX is not a SEBI-registered adviser and recommends no specific funds.',
  };
  save();
  return d.guidance;
}

// ---------- routing ----------
const ok = (body, status = 200) => ({ status, body });
const err = (status, message, errors) => ({ status, body: { status, detail: message, errors } });
const required = (body, fields) => {
  const errors = {};
  fields.forEach((f) => { if (body?.[f] === undefined || body?.[f] === null || body?.[f] === '') errors[f] = 'must not be blank'; });
  return Object.keys(errors).length ? err(400, 'Validation failed', errors) : null;
};
const authBody = () => ({ accessToken: 'demo-token', refreshToken: 'demo-refresh', tokenType: 'Bearer', expiresInSeconds: 86400, user: load().user });

function route(method, path, q, body) {
  const d = load();
  const now = new Date();
  let m;

  if (path === '/auth/login' && method === 'POST') return ok(authBody());
  if (path === '/auth/refresh' && method === 'POST') return ok(authBody());
  if (path === '/auth/register' && method === 'POST') {
    const bad = required(body, ['fullName', 'email', 'password', 'monthlySalary']);
    if (bad) return bad;
    if (String(body.password).length < 8) return err(400, 'Validation failed', { password: 'size must be between 8 and 72' });
    d.user = { ...d.user, ...body, password: undefined, monthlySalary: Number(body.monthlySalary), riskProfile: body.riskProfile || 'MODERATE' };
    save();
    return ok(authBody(), 201);
  }

  if (path === '/users/me') {
    if (method === 'GET') return ok(d.user);
    if (method === 'PATCH') {
      Object.entries(body || {}).forEach(([k, v]) => { if (v !== null && v !== undefined && k !== 'email') d.user[k] = k === 'monthlySalary' ? Number(v) : v; });
      save();
      return ok(d.user);
    }
  }
  if (path === '/users/me/password' && method === 'PUT') return ok(null, 204);

  if (path === '/expense-categories') return ok(CATEGORIES);

  if (path === '/expenses' && method === 'GET') {
    const [defFrom, defTo] = monthRange(now.getFullYear(), now.getMonth() + 1);
    let list = inRange(q.get('from') || defFrom, q.get('to') || defTo);
    if (q.get('categoryId')) list = list.filter((e) => e.categoryId === Number(q.get('categoryId')));
    list = [...list].sort((a, b) => (b.expenseDate.localeCompare(a.expenseDate)) || b.expenseId - a.expenseId);
    const size = Number(q.get('size') || 20);
    const page = Number(q.get('page') || 0);
    return ok({ content: list.slice(page * size, page * size + size).map(expenseView), totalElements: list.length,
      totalPages: Math.ceil(list.length / size), number: page, size });
  }
  if (path === '/expenses' && method === 'POST') {
    const bad = required(body, ['categoryId', 'amount', 'expenseDate']);
    if (bad) return bad;
    if (body.expenseDate > todayIso()) return err(400, 'Validation failed', { expenseDate: 'must be a date in the past or in the present' });
    const e = { expenseId: d.nextExpenseId++, categoryId: Number(body.categoryId), amount: round2(Number(body.amount)),
      description: body.description || null, expenseDate: body.expenseDate, paymentMethod: body.paymentMethod || null, note: body.note || null };
    d.expenses.push(e);
    save();
    return ok(expenseView(e), 201);
  }
  if ((m = path.match(/^\/expenses\/(\d+)$/))) {
    const i = d.expenses.findIndex((e) => e.expenseId === Number(m[1]));
    if (i < 0) return err(404, 'Expense not found');
    if (method === 'GET') return ok(expenseView(d.expenses[i]));
    if (method === 'DELETE') { d.expenses.splice(i, 1); save(); return ok(null, 204); }
    if (method === 'PUT') {
      const bad = required(body, ['categoryId', 'amount', 'expenseDate']);
      if (bad) return bad;
      d.expenses[i] = { ...d.expenses[i], ...body, categoryId: Number(body.categoryId), amount: round2(Number(body.amount)) };
      save();
      return ok(expenseView(d.expenses[i]));
    }
  }
  if (path === '/expenses/summary/month') {
    return ok(monthSummary(Number(q.get('month') || now.getMonth() + 1), Number(q.get('year') || now.getFullYear())));
  }
  if (path === '/expenses/summary/year') {
    const y = Number(q.get('year') || now.getFullYear());
    const rows = [];
    for (let mo = 1; mo <= 12; mo++) {
      const list = inRange(...monthRange(y, mo));
      if (list.length) rows.push({ month: mo, total: round2(list.reduce((s, e) => s + e.amount, 0)), count: list.length });
    }
    return ok(rows);
  }

  if (path === '/expenses/daily' && method === 'GET') return ok(dailySummary(q.get('date') || todayIso()));
  if ((m = path.match(/^\/expenses\/daily\/(\d{4}-\d{2}-\d{2})\/note$/)) && method === 'PUT') {
    const note = body?.note ? String(body.note).slice(0, 1000) : null;
    if (note) d.notes[m[1]] = note; else delete d.notes[m[1]];
    save();
    return ok({ note });
  }
  if (path === '/expenses/daily/settings') {
    if (method === 'PUT') { d.savingsGoal = Math.max(0, Number(body?.monthlySavingsGoal || 0)); save(); }
    return ok({ monthlySavingsGoal: d.savingsGoal });
  }

  if (path === '/expenses/monthly' && method === 'GET') {
    const items = [...d.monthly].sort(monthlyOrder);
    return ok({ monthlyTotal: monthlyTotal(), activeCount: items.filter((x) => x.active).length, items });
  }
  const monthlyBody = (b, cur = {}) => {
    const bad = required(b, ['name', 'amount']);
    if (bad) return { bad };
    return { item: { ...cur, name: String(b.name).trim(), amount: round2(Number(b.amount)), type: b.type || 'OTHER',
      dueDay: b.dueDay ? Number(b.dueDay) : null, note: b.note ? String(b.note).trim().slice(0, 500) : null,
      active: b.active === undefined || b.active === null ? (cur.active ?? true) : !!b.active } };
  };
  if (path === '/expenses/monthly' && method === 'POST') {
    const { bad, item } = monthlyBody(body);
    if (bad) return bad;
    item.monthlyExpenseId = d.nextMonthlyId++;
    d.monthly.push(item);
    save();
    return ok(item, 201);
  }
  if ((m = path.match(/^\/expenses\/monthly\/(\d+)(\/active)?$/))) {
    const i = d.monthly.findIndex((x) => x.monthlyExpenseId === Number(m[1]));
    if (i < 0) return err(404, 'Monthly expense not found');
    if (m[2] && method === 'PATCH') { d.monthly[i].active = !!body?.active; save(); return ok(d.monthly[i]); }
    if (method === 'DELETE') { d.monthly.splice(i, 1); save(); return ok(null, 204); }
    if (method === 'PUT') {
      const { bad, item } = monthlyBody(body, d.monthly[i]);
      if (bad) return bad;
      d.monthly[i] = item;
      save();
      return ok(item);
    }
  }

  if (path === '/emis' && method === 'GET') {
    let list = d.emis.map((e) => emiView(e));
    if (q.get('status')) list = list.filter((e) => e.status === q.get('status'));
    return ok(list);
  }
  if (path === '/emis' && method === 'POST') {
    const bad = required(body, ['loanName', 'loanType', 'principalAmount', 'interestRate', 'tenureMonths', 'startDate']);
    if (bad) return bad;
    const e = { emiId: d.nextEmiId++, loanName: body.loanName, lenderName: body.lenderName || null, loanType: body.loanType,
      principalAmount: Number(body.principalAmount), interestRate: Number(body.interestRate), tenureMonths: Number(body.tenureMonths),
      startDate: body.startDate, status: 'ACTIVE' };
    e.emiAmount = body.emiAmount ? Number(body.emiAmount) : emiAmount(e.principalAmount, e.interestRate, e.tenureMonths);
    d.emis.push(e);
    save();
    return ok(emiView(e), 201);
  }
  if (path === '/emis/summary') {
    const act = activeEmis();
    return ok({ activeLoans: act.length, monthlyEmiTotal: monthlyEmi(), totalOutstandingPrincipal: round2(act.reduce((s, e) => s + e.outstandingPrincipal, 0)) });
  }
  if ((m = path.match(/^\/emis\/(\d+)(\/close)?$/))) {
    const i = d.emis.findIndex((e) => e.emiId === Number(m[1]));
    if (i < 0) return err(404, 'EMI not found');
    if (m[2] && method === 'PATCH') { d.emis[i].status = 'CLOSED'; save(); return ok(emiView(d.emis[i])); }
    if (method === 'DELETE') { d.emis.splice(i, 1); save(); return ok(null, 204); }
    if (method === 'GET') return ok(emiView(d.emis[i]));
    if (method === 'PUT') {
      const e = { ...d.emis[i], ...body };
      ['principalAmount', 'interestRate', 'tenureMonths'].forEach((k) => { e[k] = Number(e[k]); });
      e.emiAmount = body.emiAmount ? Number(body.emiAmount) : emiAmount(e.principalAmount, e.interestRate, e.tenureMonths);
      d.emis[i] = e;
      save();
      return ok(emiView(e));
    }
  }

  if (path === '/recommendations/latest') return d.guidance ? ok(d.guidance) : err(404, 'No guidance yet');
  if (path === '/recommendations/generate' && method === 'POST') return ok(makeGuidance());
  if (path === '/ai/safety-profile') {
    if (method === 'PUT') {
      const bad = required(body, ['emergencySavings', 'dependents', 'hasTermInsurance', 'hasHealthInsurance']);
      if (bad) return bad;
      d.safety = { emergencySavings: Number(body.emergencySavings), dependents: Number(body.dependents),
        hasTermInsurance: !!body.hasTermInsurance, hasHealthInsurance: !!body.hasHealthInsurance, updatedAt: new Date().toISOString() };
      save();
    }
    return ok(d.safety ? { saved: true, ...d.safety } : { saved: false, emergencySavings: 0, dependents: 0, hasTermInsurance: false, hasHealthInsurance: false, updatedAt: null });
  }

  return err(404, 'Not available in the demo');
}

// fetch-compatible entry point used by api.js in demo builds.
export async function demoFetch(url, { method = 'GET', body } = {}) {
  const u = new URL(url, window.location.origin);
  const path = u.pathname.replace(/^\/api/, '');
  let res;
  try {
    res = route(method, path, u.searchParams, body ? JSON.parse(body) : undefined);
  } catch (e) {
    res = err(500, `Demo error: ${e.message}`);
  }
  await new Promise((r) => setTimeout(r, 120)); // feel like a network call
  const text = res.body === null || res.status === 204 ? '' : JSON.stringify(res.body);
  return new Response(res.status === 204 ? null : text, { status: res.status, headers: { 'Content-Type': 'application/json' } });
}
