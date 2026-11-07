import { createContext, useCallback, useContext, useEffect, useState } from 'react';

// Light / dark theme. The choice is 'light', 'dark' or 'system' (follow the device) and is kept in
// localStorage. index.html applies the saved choice before React loads, so the page never flashes.
const KEY = 'budgetx.theme';
const BAR = { light: '#faf9f5', dark: '#262624' };
const media = typeof window !== 'undefined' && window.matchMedia ? window.matchMedia('(prefers-color-scheme: dark)') : null;

function readChoice() {
  try {
    const v = localStorage.getItem(KEY);
    return v === 'light' || v === 'dark' ? v : 'system';
  } catch {
    return 'system';
  }
}

const resolve = (choice) => (choice === 'system' ? (media?.matches ? 'dark' : 'light') : choice);

function apply(choice) {
  const root = document.documentElement;
  const theme = resolve(choice);
  if (choice === 'system') root.removeAttribute('data-theme');
  else root.setAttribute('data-theme', choice);
  document.querySelector('meta[name="theme-color"]')?.setAttribute('content', BAR[theme]);
  return theme;
}

const ThemeContext = createContext(null);

export function ThemeProvider({ children }) {
  const [choice, setChoice] = useState(readChoice);
  const [theme, setTheme] = useState(() => resolve(choice));

  useEffect(() => {
    setTheme(apply(choice));
    if (choice !== 'system' || !media) return undefined;
    const onChange = () => setTheme(apply('system'));
    media.addEventListener('change', onChange);
    return () => media.removeEventListener('change', onChange);
  }, [choice]);

  const choose = useCallback((next) => {
    // Fade colours only while switching, so normal hovers stay snappy.
    const root = document.documentElement;
    root.classList.add('theme-fade');
    window.setTimeout(() => root.classList.remove('theme-fade'), 400);
    try {
      if (next === 'system') localStorage.removeItem(KEY);
      else localStorage.setItem(KEY, next);
    } catch {
      /* storage blocked: the choice lasts for this visit only */
    }
    setChoice(next);
  }, []);

  const toggle = useCallback(() => choose(theme === 'dark' ? 'light' : 'dark'), [choose, theme]);

  return <ThemeContext.Provider value={{ choice, theme, choose, toggle }}>{children}</ThemeContext.Provider>;
}

export const useTheme = () => useContext(ThemeContext);

const SUN = 'M12 4V2M12 22v-2M4.93 4.93 3.51 3.51M20.49 20.49l-1.42-1.42M4 12H2M22 12h-2M4.93 19.07l-1.42 1.42M20.49 3.51l-1.42 1.42M12 17a5 5 0 1 0 0-10 5 5 0 0 0 0 10z';
const MOON = 'M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z';
const MONITOR = 'M3 4h18v12H3zM8 20h8M12 16v4';

function Glyph({ d, size = 18 }) {
  return (
    <svg viewBox="0 0 24 24" width={size} height={size} fill="none" stroke="currentColor" strokeWidth="1.8"
      strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d={d} /></svg>
  );
}

// Round button that flips between light and dark, with the icon spinning between sun and moon.
export function ThemeToggle({ className = '' }) {
  const { theme, toggle } = useTheme();
  const dark = theme === 'dark';
  return (
    <button type="button" className={`theme-toggle ${className}`} onClick={toggle}
      aria-label={dark ? 'Switch to light mode' : 'Switch to dark mode'} title={dark ? 'Light mode' : 'Dark mode'}>
      <span className={`theme-icon${dark ? ' is-dark' : ''}`}>
        <span className="sun"><Glyph d={SUN} /></span>
        <span className="moon"><Glyph d={MOON} /></span>
      </span>
    </button>
  );
}

// Three-way picker (Light / Dark / System) for the sidebar and profile page.
export function ThemePicker() {
  const { choice, choose } = useTheme();
  const opts = [['light', 'Light', SUN], ['dark', 'Dark', MOON], ['system', 'Auto', MONITOR]];
  return (
    <div className="theme-picker" role="radiogroup" aria-label="Appearance">
      {opts.map(([v, l, d]) => (
        <button key={v} type="button" role="radio" aria-checked={choice === v}
          className={choice === v ? 'on' : ''} onClick={() => choose(v)}>
          <Glyph d={d} size={15} /> {l}
        </button>
      ))}
    </div>
  );
}
