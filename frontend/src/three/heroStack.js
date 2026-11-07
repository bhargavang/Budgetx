// Home banner: a stack of rupee coins (one per eighth of salary still left this month) with a few
// coins and soft pebbles floating beside it. Uses the same coin look as the sign-in field.
import { CircleGeometry, CylinderGeometry, Group, Mesh, MeshStandardMaterial, SphereGeometry } from 'three';
import { coinMaterials } from './coins.js';
import { createRuntime } from './runtime.js';

// [x, y] as fractions of the half-width/half-height, z depth, size
const FLOAT = [[0.42, 0.45, -1, 0.42], [0.92, -0.35, -2, 0.5], [0.95, 0.62, -3, 0.34]];
const PEBBLES = [[0.55, -0.6, -0.5, 0.2], [0.8, 0.2, 0.4, 0.16]];
const seeded = (i) => { const x = Math.sin(i * 127.1 + 311.7) * 43758.5453; return x - Math.floor(x); };

export function mount(canvas, { palette, still, stack = 0 }) {
  const rt = createRuntime(canvas, { palette, still, fov: 40, z: 10 });
  if (!rt) return null;

  const rim = new CylinderGeometry(1, 1, 0.16, 48, 1);
  rim.rotateX(Math.PI / 2);
  const face = new CircleGeometry(0.995, 48);
  const pebbleGeo = new SphereGeometry(1, 32, 24);
  let mats = coinMaterials(palette.primary, palette.primaryInk);
  const pebbleMats = [0, 1].map(() => new MeshStandardMaterial({ roughness: 0.55, metalness: 0.05 }));
  const paintPebbles = (p) => { pebbleMats[0].color.set(p.accent); pebbleMats[1].color.set(p.soft); };
  paintPebbles(palette);

  const coins = [];
  const makeCoin = () => {
    const g = new Group();
    const r = new Mesh(rim, mats.edge);
    const f = new Mesh(face, mats.face); f.position.z = 0.081;
    const b = new Mesh(face, mats.face); b.position.z = -0.081; b.rotation.y = Math.PI;
    g.add(r, f, b);
    g.userData.parts = [r, f, b];
    coins.push(g);
    return g;
  };

  const rig = new Group();
  rt.scene.add(rig);

  const floaters = FLOAT.map((spec, i) => {
    const m = makeCoin();
    rig.add(m);
    return { m, spec, phase: seeded(i + 1) * 6.28, spin: 0.25 + seeded(i + 9) * 0.35 };
  });
  const pebbles = PEBBLES.map((spec, i) => {
    const m = new Mesh(pebbleGeo, pebbleMats[i % 2]);
    rig.add(m);
    return { m, spec, phase: seeded(i + 20) * 6.28 };
  });

  const pile = new Group();
  pile.rotation.set(0.35, -0.5, 0);
  rig.add(pile);
  let pileCoins = [];
  let dropStart = 0;
  const setStack = (n) => {
    pileCoins.forEach((c) => pile.remove(c));
    pileCoins = Array.from({ length: n }, (_, i) => {
      const c = makeCoin();
      c.rotation.set(-Math.PI / 2, 0, i * 0.7);
      c.scale.set(0.62, 0.62, 1.4); // a bit thicker than the floating coins
      pile.add(c);
      return c;
    });
    dropStart = null;
  };
  setStack(stack);

  let small = false;
  let unit = 1;
  rt.onResize = () => {
    const { w, h } = rt.view();
    small = rt.width < 640;
    unit = h / 4.2; // size against the short side so the short banner still gets big coins
    const at = (o, [x, y, z, s]) => {
      const k = 1 + -z / 12; // farther objects need a bigger spread to land at the same spot
      o.position.set(x * (w / 2) * k, y * (h / 2) * k, z);
      o.userData.y = o.position.y;
      o.scale.setScalar(s * unit);
    };
    floaters.forEach((f, i) => { at(f.m, f.spec); f.m.visible = !small || i === 0; });
    pebbles.forEach((p) => { at(p.m, p.spec); p.m.visible = !small; });
    pile.position.set(w * (small ? 0.33 : 0.3), -h * 0.27, 0.5);
    pile.scale.setScalar(unit);
  };

  const pointer = { x: 0, y: 0 };
  const onPointer = (e) => {
    if (e.pointerType && e.pointerType !== 'mouse') return;
    pointer.x = (e.clientX / window.innerWidth) * 2 - 1;
    pointer.y = (e.clientY / window.innerHeight) * 2 - 1;
  };
  if (!still) window.addEventListener('pointermove', onPointer, { passive: true });

  rt.onFrame = (t, dt) => {
    floaters.forEach(({ m, phase, spin }) => {
      m.position.y = m.userData.y + (still ? 0 : Math.sin(t * 0.8 + phase) * 0.12);
      m.rotation.set(0.25, 0.5 + Math.sin(t * spin + phase) * 0.9, Math.sin(t * 0.4 + phase) * 0.15);
    });
    pebbles.forEach(({ m, phase }) => { m.position.y = m.userData.y + (still ? 0 : Math.sin(t * 0.6 + phase) * 0.16); });
    // coins drop onto the pile one after another, then rest
    if (dropStart === null) dropStart = t;
    pileCoins.forEach((c, i) => {
      const p = still ? 1 : Math.min(1, Math.max(0, (t - dropStart - i * 0.12) / 0.5));
      c.position.set(Math.sin(i * 2.1) * 0.04, i * 0.23 + (1 - (1 - (1 - p) ** 3)) * 2.5, Math.cos(i * 1.7) * 0.04);
      c.visible = p > 0;
    });
    if (!still && dt) {
      rig.rotation.y += (pointer.x * 0.18 - rig.rotation.y) * 0.05;
      rig.rotation.x += (-pointer.y * 0.12 - rig.rotation.x) * 0.05;
    }
  };

  rt.start();

  return {
    update({ stack: n = 0 }) { if (n !== pileCoins.length) { setStack(n); rt.frame(); } },
    setPalette(p) {
      mats.dispose();
      mats = coinMaterials(p.primary, p.primaryInk);
      coins.forEach((c) => { const [r, f, b] = c.userData.parts; r.material = mats.edge; f.material = mats.face; b.material = mats.face; });
      paintPebbles(p);
      rt.setPalette(p);
      rt.frame();
    },
    setActive(on) { on ? rt.play() : rt.pause(); },
    dispose() {
      window.removeEventListener('pointermove', onPointer);
      rt.dispose();
      rim.dispose(); face.dispose(); pebbleGeo.dispose();
      mats.dispose(); pebbleMats.forEach((m) => m.dispose());
    },
  };
}

