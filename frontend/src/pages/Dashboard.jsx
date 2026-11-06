import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { emis, expenses, users } from '../api';
import { useAuth } from '../AuthContext';
import { Alert, Money, Spinner, Stat } from '../components/ui';
import { MONTHS, date, money } from '../format';
import { EXPENSE_ADDED } from '../components/PayFlow';
import Stage, { canUse3D } from '../three/Stage';

export default function Dashboard() {
  const { user, updateUser } = useAuth();
  const [data, setData] = useState(null);
  const [error, setError] = useState('');
  const [picked, setPicked] = useState(null);

  const load = useCallback(() => {
    const now = new Date();
    Promise.allSettled([
      users.me(),
      emis.summary(),
      expenses.monthSummary(now.getMonth() + 1, now.getFullYear()),
      expenses.yearSummary(now.getFullYear()),
      expenses.list({ size: 5 }),
    ]).then(([me, emi, month, year, recent]) => {
      if (me.status === 'fulfilled') updateUser(me.value);
      const failed = [emi, month, year, recent].filter((r) => r.status === 'rejected');
      if (failed.length) setError(`Some data could not load: ${failed[0].reason.message}`);
      setData({
        emi: emi.value,
        month: month.value,
        year: year.value || [],
        recent: recent.value?.content || [],
      });
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(load, [load]);
  useEffect(() => {
    window.addEventListener(EXPENSE_ADDED, load);
    return () => window.removeEventListener(EXPENSE_ADDED, load);
  }, [load]);

  if (!data) return <Spinner />;

  const salary = Number(user?.monthlySalary || 0);
  const emiTotal = Number(data.emi?.monthlyEmiTotal || 0);
  const spent = Number(data.month?.total || 0);
  const left = salary - emiTotal - spent;
  const emiRatio = salary ? Math.round((emiTotal / salary) * 100) : 0;
  const cats = [...(data.month?.byCategory || [])].sort((a, b) => b.total - a.total);
  const maxCat = Math.max(1, ...cats.map((c) => Number(c.total)));
  const maxMonth = Math.max(1, ...data.year.map((m) => Number(m.total)));
  const byMonth = Object.fromEntries(data.year.map((m) => [m.month, Number(m.total)]));
  const thisMonth = new Date().getMonth() + 1;
  // Coins on the Home banner: one per eighth of salary still left this month.
  const stack = salary > 0 ? Math.max(0, Math.min(8, Math.round((Math.max(0, left) / salary) * 8))) : 0;
  const slices = cats.map((c) => ({ total: Number(c.total), essential: c.essential }));
  const catTotal = slices.reduce((a, c) => a + c.total, 0);
  const pick = picked !== null ? cats[picked] : null;

  return (
    <>
      <section className="card hero">
        <Stage scene="hero" stack={stack} className="hero-stage" />
        <div className="hero-text">
          <h1>Hi, {user?.fullName?.split(' ')[0] || 'there'}</h1>
          <p className="muted">{MONTHS[thisMonth - 1]} {new Date().getFullYear()} at a glance</p>
          {salary > 0 && (
            <p className="hero-left">
              {left >= 0 ? <><strong><Money value={left} /></strong> left to spend</> : <>Over budget by <strong><Money value={-left} /></strong></>}
            </p>
          )}
          {stack > 0 && <p className="hero-note">The coin stack shows it: one coin for every eighth of your salary.</p>}
          <div className="hero-actions"><Link to="/expenses?add=1" className="btn btn-primary">+ Add expense</Link></div>
        </div>
      </section>
      <Alert>{error}</Alert>

      <div className="stats">
        <Stat label="Monthly salary" value={<Money value={salary} />} />
        <Stat label="EMIs this month" value={<Money value={emiTotal} />}
          sub={`${data.emi?.activeLoans || 0} active · ${emiRatio}% of salary`} tone={emiRatio > 40 ? 'warn' : undefined} />
        <Stat label="Spent this month" value={<Money value={spent} />}
          sub={data.month ? `${money(data.month.essentialTotal)} needs · ${money(data.month.discretionaryTotal)} wants` : ''} />
        <Stat label="Left to spend" value={<Money value={left} signed />} tone={left < 0 ? 'bad' : 'good'}
          sub={left < 0 ? 'Over budget this month' : 'After EMIs and expenses'} />
      </div>

      <div className="grid-dash">
        <section className="card">
          <h2>Spending by category</h2>
          {cats.length === 0 ? (
            <p className="muted">No expenses this month yet.</p>
          ) : (
            <>
            {canUse3D() && <div className="ring-wrap">
              <Stage scene="ring" slices={slices} selected={picked} onPick={setPicked} className="ring-stage" />
              <div className="ring-center" aria-live="polite">
                <span className="small">{pick ? pick.categoryName : 'This month'}</span>
                <span className="num">{money(pick ? pick.total : catTotal)}</span>
                {pick && <span className="small">{Math.round((Number(pick.total) / (catTotal || 1)) * 100)}% of spending</span>}
                {!pick && <span className="small">Point at a slice or a bar</span>}
              </div>
            </div>}
            <ul className="bars">
              {cats.map((c, i) => (
                <li key={c.categoryId} style={{ '--i': i }} className={picked === i ? 'picked' : undefined}
                  onPointerEnter={() => setPicked(i)} onPointerLeave={() => setPicked(null)}>
                  <div className="bar-row">
                    <span>{c.categoryName} {!c.essential && <span className="tag">want</span>}</span>
                    <span className="num">{money(c.total)}</span>
                  </div>
                  <div className="bar"><div className={`bar-fill${c.essential ? '' : ' alt'}`}
                    style={{ width: `${(Number(c.total) / maxCat) * 100}%` }} /></div>
                </li>
              ))}
            </ul>
            </>
          )}
        </section>

        <section className="card">
          <h2>This year</h2>
          <div className="month-chart" role="img" aria-label="Monthly spending this year">
            {MONTHS.map((m, i) => {
              const v = byMonth[i + 1] || 0;
              return (
                <div className={`month-col${i + 1 === thisMonth ? ' current' : ''}`} key={m} style={{ '--i': i }}
                  title={`${m}: ${money(v)}`}>
                  <span className="month-tip">{money(v)}</span>
                  <div className="month-bar-wrap">
                    <div className={`month-bar${i + 1 === thisMonth ? ' current' : ''}`}
                      style={{ height: `${(v / maxMonth) * 100}%` }} />
                  </div>
                  <span className="month-label">{m.charAt(0)}</span>
                </div>
              );
            })}
          </div>
        </section>

        <section className="card span-2">
          <div className="section-head">
            <h2>Recent expenses</h2>
            <Link to="/expenses" className="small">See all</Link>
          </div>
          {data.recent.length === 0 ? (
            <p className="muted">Nothing recorded this month.</p>
          ) : (
            <ul className="list">
              {data.recent.map((e) => (
                <li key={e.expenseId} className="list-row">
                  <div>
                    <div>{e.description || e.categoryName}</div>
                    <div className="muted small">{e.categoryName} · {date(e.expenseDate)}</div>
                  </div>
                  <span className="num">{money(e.amount)}</span>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>
    </>
  );
}
