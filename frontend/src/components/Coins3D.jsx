import { useEffect, useRef } from 'react';
import { useTheme } from '../theme';

const reducedMotion = () => window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;

function readColors() {
  const s = getComputedStyle(document.documentElement);
  const v = (name, fallback) => s.getPropertyValue(name).trim() || fallback;
  return { primary: v('--primary', '#c15f3c'), accent: v('--accent', '#5e8f8a'), ink: v('--primary-ink', '#ffffff') };
}

// A canvas with a three.js coin scene. three is imported lazily, and if WebGL is missing
// the canvas simply stays empty.
export default function Coins3D({ mode = 'field', className = '' }) {
  const ref = useRef(null);
  const scene = useRef(null);
  const { theme } = useTheme() || {};

  useEffect(() => {
    let cancelled = false;
    import('../three/coins.js')
      .then(({ mountCoins }) => {
        if (cancelled || !ref.current) return;
        scene.current = mountCoins(ref.current, { mode, colors: readColors(), reduced: reducedMotion() });
      })
      .catch(() => { /* 3D is decoration only */ });
    return () => {
      cancelled = true;
      scene.current?.dispose();
      scene.current = null;
    };
  }, [mode]);

  useEffect(() => {
    // wait a frame so the new theme's CSS variables are in place
    const id = requestAnimationFrame(() => scene.current?.setColors(readColors()));
    return () => cancelAnimationFrame(id);
  }, [theme]);

  return <canvas ref={ref} className={`coins-3d coins-${mode} ${className}`} aria-hidden="true" />;
}
