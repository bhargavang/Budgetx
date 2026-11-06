// UPI payment links. BudgetX never touches money: it builds a standard upi://pay link
// (NPCI's "UPI deep linking" spec) that hands the payment to the user's own UPI app,
// then asks the user whether it went through so the spend can be logged.

const PENDING_KEY = 'budgetx.pendingPay';
const PAYEES_KEY = 'budgetx.payees';
const PENDING_TTL_MS = 30 * 60 * 1000;

// handle@bank, e.g. ravi.kumar@okhdfcbank or 9876543210@ybl
const VPA = /^[a-zA-Z0-9.\-_]{2,256}@[a-zA-Z][a-zA-Z0-9.\-]{1,63}$/;

export const isValidVpa = (v) => VPA.test((v || '').trim());

// Apps differ in how much of the note they keep, so stay short and plain.
const cleanNote = (s) => (s || '').replace(/[^\p{L}\p{N} .,\-_/&()]/gu, ' ').replace(/\s+/g, ' ').trim().slice(0, 50);

function query({ vpa, name, amount, note }) {
  const parts = [
    ['pa', vpa.trim()],
    ['pn', (name || '').trim() || vpa.trim()],
    ['am', Number(amount).toFixed(2)],
    ['cu', 'INR'],
  ];
  const tn = cleanNote(note);
  if (tn) parts.push(['tn', tn]);
  // encodeURIComponent gives %20 for spaces; some UPI apps read "+" literally.
  // Keep the @ in the UPI ID literal: a few older apps fail to decode %40.
  return parts.map(([k, v]) => `${k}=${encodeURIComponent(v).replace(/%40/g, '@')}`).join('&');
}

export function upiLink(payment) {
  return `upi://pay?${query(payment)}`;
}

const ANDROID_PACKAGES = {
  gpay: 'com.google.android.apps.nbu.paisa.user',
  phonepe: 'com.phonepe.app',
  paytm: 'net.one97.paytm',
};
const IOS_SCHEMES = {
  gpay: 'gpay://upi/pay',
  phonepe: 'phonepe://pay',
  paytm: 'paytmmp://pay',
};

export const APPS = [
  { id: 'gpay', name: 'GPay' },
  { id: 'phonepe', name: 'PhonePe' },
  { id: 'paytm', name: 'Paytm' },
];

// A link that opens one particular app, for when the system picker is not wanted.
export function appLink(appId, payment) {
  const q = query(payment);
  const p = platform();
  if (p === 'ios') return `${IOS_SCHEMES[appId]}?${q}`;
  if (p === 'android') return `intent://pay?${q}#Intent;scheme=upi;package=${ANDROID_PACKAGES[appId]};end`;
  return upiLink(payment);
}

export function platform() {
  if (typeof navigator === 'undefined') return 'desktop';
  const ua = navigator.userAgent || '';
  if (/Android/i.test(ua)) return 'android';
  if (/iPhone|iPad|iPod/i.test(ua) || (/Macintosh/.test(ua) && navigator.maxTouchPoints > 1)) return 'ios';
  return 'desktop';
}

// ---------- the payment waiting for an answer ----------
// Kept in localStorage because the browser may unload BudgetX while the UPI app is open.
export function savePending(p) {
  try { localStorage.setItem(PENDING_KEY, JSON.stringify({ ...p, startedAt: Date.now() })); } catch { /* memory only */ }
}

export function loadPending(owner) {
  try {
    const p = JSON.parse(localStorage.getItem(PENDING_KEY));
    if (!p || p.owner !== owner || Date.now() - p.startedAt > PENDING_TTL_MS) {
      localStorage.removeItem(PENDING_KEY);
      return null;
    }
    return p;
  } catch {
    return null;
  }
}

export function clearPending() {
  try { localStorage.removeItem(PENDING_KEY); } catch { /* nothing stored */ }
}

// ---------- people you have paid before (this device only) ----------
// Scoped by account so two people sharing a phone do not see each other's payees.
const payeesKey = (owner) => `${PAYEES_KEY}.${owner || 'me'}`;

export function loadPayees(owner) {
  try {
    const list = JSON.parse(localStorage.getItem(payeesKey(owner)));
    return Array.isArray(list) ? list : [];
  } catch {
    return [];
  }
}

export function rememberPayee(owner, { vpa, name, categoryId }) {
  const key = vpa.trim().toLowerCase();
  const list = loadPayees(owner).filter((p) => p.vpa.toLowerCase() !== key);
  list.unshift({ vpa: vpa.trim(), name: (name || '').trim(), categoryId });
  try { localStorage.setItem(payeesKey(owner), JSON.stringify(list.slice(0, 8))); } catch { /* not remembered */ }
}

export function forgetPayee(owner, vpa) {
  const list = loadPayees(owner).filter((p) => p.vpa.toLowerCase() !== vpa.toLowerCase());
  try { localStorage.setItem(payeesKey(owner), JSON.stringify(list)); } catch { /* not stored */ }
  return list;
}

// Someone may paste a whole UPI link (e.g. copied from a QR scanner) into the UPI ID box.
export function parseUpiLink(text) {
  const s = (text || '').trim();
  if (!/^upi:\/\/pay\?/i.test(s)) return null;
  const params = new URLSearchParams(s.slice(s.indexOf('?') + 1));
  const vpa = params.get('pa');
  if (!vpa) return null;
  return { vpa, name: params.get('pn') || '', amount: params.get('am') || '', note: params.get('tn') || '' };
}
