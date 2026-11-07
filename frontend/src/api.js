// Thin fetch wrapper for the BudgetX gateway. All calls go to the same origin under /api
// (nginx in production, the Vite proxy in development).

const STORAGE_KEY = 'budgetx.auth';

// Demo builds (VITE_DEMO=true, used for the Netlify preview) answer /api calls from a pretend backend
// in the browser. Normal builds leave this out entirely.
export const DEMO = import.meta.env.VITE_DEMO === 'true';
const doFetch = DEMO
  ? (url, opts) => import('./demo/backend.js').then((m) => m.demoFetch(url, opts))
  : (url, opts) => fetch(url, opts);

export function loadSession() {
  try {
    return JSON.parse(localStorage.getItem(STORAGE_KEY)) || null;
  } catch {
    return null;
  }
}

export function saveSession(session) {
  try {
    if (session) localStorage.setItem(STORAGE_KEY, JSON.stringify(session));
    else localStorage.removeItem(STORAGE_KEY);
  } catch {
    /* storage unavailable (private mode): session lives in memory only */
  }
}

let session = loadSession();
let onSessionChange = () => {};
let refreshing = null;

export function setSessionListener(fn) {
  onSessionChange = fn;
}

export function setSession(next) {
  session = next;
  saveSession(next);
  onSessionChange(next);
}

export function getSession() {
  return session;
}

export class ApiError extends Error {
  constructor(status, message, fieldErrors) {
    super(message);
    this.status = status;
    this.fieldErrors = fieldErrors || {};
  }
}

async function parseError(res) {
  let body = null;
  try {
    body = await res.json();
  } catch {
    /* empty or non-JSON body */
  }
  const fieldErrors = body?.errors && typeof body.errors === 'object' ? body.errors : {};
  let message = body?.detail || body?.message || body?.title;
  if (!message) {
    message =
      res.status === 401 ? 'Please sign in again.'
      : res.status === 403 ? 'You are not allowed to do that.'
      : res.status === 429 ? 'Too many requests. Please wait a moment.'
      : res.status >= 500 ? 'The server is unavailable. Please try again shortly.'
      : `Request failed (${res.status})`;
  }
  return new ApiError(res.status, message, fieldErrors);
}

async function refreshTokens() {
  if (!session?.refreshToken) return false;
  if (!refreshing) {
    refreshing = doFetch('/api/auth/refresh', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken: session.refreshToken }),
    })
      .then(async (res) => {
        if (!res.ok) return false;
        const data = await res.json();
        setSession({ ...session, ...data, user: data.user || session.user });
        return true;
      })
      .catch(() => false)
      .finally(() => {
        refreshing = null;
      });
  }
  return refreshing;
}

export async function api(path, { method = 'GET', body, query, auth = true } = {}) {
  let url = `/api${path}`;
  if (query) {
    const params = new URLSearchParams();
    Object.entries(query).forEach(([k, v]) => {
      if (v !== undefined && v !== null && v !== '') params.append(k, v);
    });
    const qs = params.toString();
    if (qs) url += `?${qs}`;
  }

  const send = () => {
    const headers = {};
    if (body !== undefined) headers['Content-Type'] = 'application/json';
    if (auth && session?.accessToken) headers.Authorization = `Bearer ${session.accessToken}`;
    return doFetch(url, { method, headers, body: body !== undefined ? JSON.stringify(body) : undefined });
  };

  let res;
  try {
    res = await send();
  } catch {
    throw new ApiError(0, 'Cannot reach the server. Check your connection.');
  }

  if (res.status === 401 && auth) {
    if (await refreshTokens()) {
      res = await send();
    }
    if (res.status === 401) {
      setSession(null);
      throw await parseError(res);
    }
  }

  if (!res.ok) throw await parseError(res);
  if (res.status === 204) return null;
  const text = await res.text();
  return text ? JSON.parse(text) : null;
}

// ---------- endpoints ----------
export const auth = {
  login: (email, password) => api('/auth/login', { method: 'POST', body: { email, password }, auth: false }),
  register: (data) => api('/auth/register', { method: 'POST', body: data, auth: false }),
};

export const users = {
  me: () => api('/users/me'),
  update: (data) => api('/users/me', { method: 'PATCH', body: data }),
  changePassword: (data) => api('/users/me/password', { method: 'PUT', body: data }),
};

export const emis = {
  list: (status) => api('/emis', { query: { status } }),
  summary: () => api('/emis/summary'),
  create: (data) => api('/emis', { method: 'POST', body: data }),
  update: (id, data) => api(`/emis/${id}`, { method: 'PUT', body: data }),
  close: (id) => api(`/emis/${id}/close`, { method: 'PATCH' }),
  remove: (id) => api(`/emis/${id}`, { method: 'DELETE' }),
};

export const expenses = {
  list: (query) => api('/expenses', { query }),
  monthSummary: (month, year) => api('/expenses/summary/month', { query: { month, year } }),
  yearSummary: (year) => api('/expenses/summary/year', { query: { year } }),
  create: (data) => api('/expenses', { method: 'POST', body: data }),
  update: (id, data) => api(`/expenses/${id}`, { method: 'PUT', body: data }),
  remove: (id) => api(`/expenses/${id}`, { method: 'DELETE' }),
  categories: () => api('/expense-categories'),
};

export const daily = {
  summary: (date) => api('/expenses/daily', { query: { date } }),
  saveNote: (date, note) => api(`/expenses/daily/${date}/note`, { method: 'PUT', body: { note } }),
  settings: () => api('/expenses/daily/settings'),
  saveSettings: (monthlySavingsGoal) => api('/expenses/daily/settings', { method: 'PUT', body: { monthlySavingsGoal } }),
};

export const monthly = {
  list: () => api('/expenses/monthly'),
  create: (data) => api('/expenses/monthly', { method: 'POST', body: data }),
  update: (id, data) => api(`/expenses/monthly/${id}`, { method: 'PUT', body: data }),
  setActive: (id, active) => api(`/expenses/monthly/${id}/active`, { method: 'PATCH', body: { active } }),
  remove: (id) => api(`/expenses/monthly/${id}`, { method: 'DELETE' }),
};

export const guidance = {
  latest: () => api('/recommendations/latest'),
  generate: () => api('/recommendations/generate', { method: 'POST' }),
  safetyProfile: () => api('/ai/safety-profile'),
  saveSafetyProfile: (data) => api('/ai/safety-profile', { method: 'PUT', body: data }),
};
