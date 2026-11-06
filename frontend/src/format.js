const inr = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 });
const inrExact = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', minimumFractionDigits: 2 });

export const money = (v) => inr.format(Number(v || 0));
export const moneyExact = (v) => inrExact.format(Number(v || 0));

export const date = (iso) =>
  iso ? new Date(`${iso}T00:00:00`).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' }) : '—';

// Words that stay in capitals when an enum like NET_BANKING becomes "Net Banking".
const ACRONYMS = new Set(['UPI', 'EMI', 'EMIS', 'SIP', 'PPF', 'NPS', 'EPF', 'ELSS', 'FD', 'RD', 'ETF', 'NEFT', 'IMPS', 'RTGS', 'GST', 'HRA', 'ATM']);

export const label = (s) =>
  (s || '').split('_').filter(Boolean).map((w) => {
    const up = w.toUpperCase();
    if (ACRONYMS.has(up)) return up === 'EMIS' ? 'EMIs' : up;
    return up.charAt(0) + up.slice(1).toLowerCase();
  }).join(' ');

export const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

export const today = () => {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
};
