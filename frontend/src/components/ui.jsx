import { useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { money } from '../format';

export function Field({ label, error, hint, children }) {
  return (
    <label className={`field${error ? ' has-error' : ''}`}>
      <span className="field-label">{label}</span>
      {children}
      {error ? <span className="field-error">{error}</span> : hint ? <span className="field-hint">{hint}</span> : null}
    </label>
  );
}

export function Alert({ children, kind = 'error' }) {
  if (!children) return null;
  return <div className={`alert alert-${kind}`} role={kind === 'error' ? 'alert' : 'status'}>{children}</div>;
}

export function Modal({ title, onClose, children }) {
  const [closing, setClosing] = useState(false);
  const close = () => {
    if (closing) return;
    setClosing(true);
    window.setTimeout(onClose, reducedMotion() ? 0 : 160);
  };
  const closeRef = useRef(close);
  closeRef.current = close;

  useEffect(() => {
    const onKey = (e) => e.key === 'Escape' && closeRef.current();
    document.addEventListener('keydown', onKey);
    document.body.classList.add('no-scroll');
    return () => {
      document.removeEventListener('keydown', onKey);
      document.body.classList.remove('no-scroll');
    };
  }, []);

  // Portal to <body> so page animations (which use transforms) can't trap the fixed overlay.
  return createPortal(
    <div className={`modal-backdrop${closing ? ' closing' : ''}`} onMouseDown={(e) => e.target === e.currentTarget && close()}>
      <div className="modal" role="dialog" aria-modal="true" aria-label={title}>
        <div className="modal-head">
          <h2>{title}</h2>
          <button className="icon-btn" onClick={close} aria-label="Close">✕</button>
        </div>
        {children}
      </div>
    </div>,
    document.body
  );
}

export function Spinner() {
  return <div className="spinner" aria-label="Loading" />;
}

export function Empty({ title, children }) {
  return (
    <div className="empty">
      <p className="empty-title">{title}</p>
      {children}
    </div>
  );
}

export function Stat({ label, value, sub, tone }) {
  return (
    <div className={`stat${tone ? ` stat-${tone}` : ''}`}>
      <span className="stat-label">{label}</span>
      <span className="stat-value">{value}</span>
      {sub && <span className="stat-sub">{sub}</span>}
    </div>
  );
}

const reducedMotion = () =>
  typeof window !== 'undefined' && window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;

// A rupee amount that counts up to its value (and eases between values when it changes).
export function Money({ value, signed = false }) {
  const target = Number(value || 0);
  const [shown, setShown] = useState(reducedMotion() ? target : 0);
  const from = useRef(shown);

  useEffect(() => {
    if (reducedMotion()) { setShown(target); return undefined; }
    const start = performance.now();
    const begin = from.current;
    const dur = 700;
    let raf;
    const step = (t) => {
      const p = Math.min(1, (t - start) / dur);
      const eased = 1 - (1 - p) ** 3;
      const v = begin + (target - begin) * eased;
      from.current = v;
      setShown(v);
      if (p < 1) raf = requestAnimationFrame(step);
    };
    raf = requestAnimationFrame(step);
    return () => cancelAnimationFrame(raf);
  }, [target]);

  const v = Math.round(shown);
  if (signed && v < 0) return <>−{money(-v)}</>;
  return <>{money(v)}</>;
}
