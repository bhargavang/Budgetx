import { useEffect, useRef, useState } from 'react';
import { useTheme } from '../theme';

// Small, eagerly loaded host for the Home 3D scenes. three.js lives in a lazy chunk (shared with the
// sign-in coins) that is only fetched when a scene scrolls into view on a device that can draw WebGL,
// and never when the user asked to save data. Until then, or without WebGL, the card keeps its flat
// design, so nothing depends on the 3D layer.

const scenes = {
  hero: () => import('./heroStack.js'),
  ring: () => import('./spendRing.js'),
};

let webgl;
export function canUse3D() {
  if (webgl !== undefined) return webgl;
  try {
    if (navigator.connection?.saveData) return (webgl = false);
    const c = document.createElement('canvas');
    const gl = c.getContext('webgl2') || c.getContext('webgl');
    webgl = !!gl;
    gl?.getExtension('WEBGL_lose_context')?.loseContext();
  } catch {
    webgl = false;
  }
  return webgl;
}

const reducedMotion = () => !!window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;

// Colours come from the CSS tokens so the scenes follow light/dark mode like everything else.
function readPalette() {
  const root = document.documentElement;
  const s = getComputedStyle(root);
  const g = (n) => s.getPropertyValue(n).trim();
  const t = root.getAttribute('data-theme');
  return {
    dark: t === 'dark' || (!t && !!window.matchMedia?.('(prefers-color-scheme: dark)').matches),
    primary: g('--primary'), primaryInk: g('--primary-ink'), soft: g('--primary-soft'),
    accent: g('--accent'), surface: g('--surface'),
  };
}

export default function Stage({ scene, className = '', ...props }) {
  const host = useRef(null);
  const canvas = useRef(null);
  const live = useRef(null);
  const latest = useRef(props);
  latest.current = props;
  const { theme } = useTheme() || {};
  const [ok] = useState(canUse3D);

  // Mount once the scene is near the screen; pause it while it is off screen or the tab is hidden.
  useEffect(() => {
    if (!ok || !host.current) return undefined;
    let cancelled = false;
    let inView = false;
    const sync = () => live.current?.setActive(inView && !document.hidden);
    const io = new IntersectionObserver(([e]) => {
      inView = e.isIntersecting;
      if (inView && !live.current && !cancelled) {
        scenes[scene]().then(({ mount }) => {
          if (cancelled || !canvas.current) return;
          live.current = mount(canvas.current, { ...latest.current, palette: readPalette(), still: reducedMotion() });
          sync();
        }).catch(() => { /* 3D is decoration only */ });
      }
      sync();
    }, { rootMargin: '120px' });
    io.observe(host.current);
    document.addEventListener('visibilitychange', sync);
    return () => {
      cancelled = true;
      io.disconnect();
      document.removeEventListener('visibilitychange', sync);
      live.current?.dispose();
      live.current = null;
    };
  }, [ok, scene]);

  // Pass prop changes (stack height, slices, the picked slice) to the running scene.
  useEffect(() => { live.current?.update(props); });

  // Re-read the tokens a frame after the theme flips, once the new CSS variables apply.
  useEffect(() => {
    const id = requestAnimationFrame(() => live.current?.setPalette(readPalette()));
    return () => cancelAnimationFrame(id);
  }, [theme]);

  if (!ok) return null;
  return (
    <div ref={host} className={`stage ${className}`} aria-hidden="true">
      <canvas ref={canvas} className="stage-canvas" />
    </div>
  );
}
