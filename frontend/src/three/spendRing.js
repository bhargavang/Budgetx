// This month's spending as a 3D ring: one slice per category, its angle is its share of the month and
// its height grows with the amount. Needs are clay, wants are teal (same as the 2D bars below it).
// Pointing at (or tapping) a slice calls onPick(index); the page shows the details next to the ring.
import { Color, ExtrudeGeometry, Group, Mesh, MeshStandardMaterial, Raycaster, Shape, Vector2 } from 'three';
import { createRuntime } from './runtime.js';

const INNER = 1.15;
const OUTER = 2;
const GAP = 0.035;

function sliceGeometry(a0, a1, depth) {
  const s = new Shape();
  s.moveTo(Math.cos(a0) * OUTER, Math.sin(a0) * OUTER);
  s.absarc(0, 0, OUTER, a0, a1, false);
  s.lineTo(Math.cos(a1) * INNER, Math.sin(a1) * INNER);
  s.absarc(0, 0, INNER, a1, a0, true);
  s.closePath();
  return new ExtrudeGeometry(s, { depth, curveSegments: 48, bevelEnabled: true, bevelThickness: 0.04, bevelSize: 0.035, bevelSegments: 3 });
}

export function mount(canvas, { palette, still, slices = [], selected = null, onPick = () => {} }) {
  const rt = createRuntime(canvas, { palette, still, fov: 38, z: 6.4 });
  if (!rt) return null;
  rt.camera.position.y = 0.6;
  rt.camera.lookAt(0, 0, 0);

  const tilt = new Group();
  tilt.rotation.x = -1.05;
  tilt.position.y = -0.2;
  const ring = new Group();
  tilt.add(ring);
  rt.scene.add(tilt);

  let parts = [];
  let current = { palette, slices, selected };
  let pick = onPick;

  const paint = () => {
    const { palette: p } = current;
    const clay = new Color(p.primary);
    const teal = new Color(p.accent);
    const fade = new Color(p.surface);
    let need = 0;
    let want = 0;
    parts.forEach((m, i) => {
      const k = current.slices[i].essential ? need++ : want++;
      const c = (current.slices[i].essential ? clay : teal).clone().lerp(fade, Math.min(0.55, k * 0.16));
      m.material.color.copy(c);
      m.material.emissive.copy(c);
    });
  };

  const build = () => {
    parts.forEach((m) => { ring.remove(m); m.geometry.dispose(); m.material.dispose(); });
    const total = current.slices.reduce((s, c) => s + c.total, 0) || 1;
    const max = Math.max(1, ...current.slices.map((c) => c.total));
    let a = Math.PI / 2;
    parts = current.slices.map((c, i) => {
      const sweep = (c.total / total) * Math.PI * 2;
      const gap = current.slices.length > 1 ? Math.min(GAP, sweep * 0.25) : 0;
      const m = new Mesh(sliceGeometry(a + gap / 2, a + sweep - gap / 2, 0.25 + (c.total / max) * 0.75),
        new MeshStandardMaterial({ roughness: 0.5, metalness: 0.12, emissiveIntensity: 0 }));
      m.userData.index = i;
      a += sweep;
      ring.add(m);
      return m;
    });
    paint();
  };
  build();

  // Hover and tap: raycast against the slices.
  const ray = new Raycaster();
  const ndc = new Vector2();
  let hovering = null;
  const hit = (e) => {
    const r = canvas.getBoundingClientRect();
    ndc.set(((e.clientX - r.left) / r.width) * 2 - 1, -((e.clientY - r.top) / r.height) * 2 + 1);
    ray.setFromCamera(ndc, rt.camera);
    const h = ray.intersectObjects(parts, false)[0];
    return h ? h.object.userData.index : null;
  };
  const onMove = (e) => {
    const i = hit(e);
    if (i !== hovering) { hovering = i; pick(i); }
    canvas.style.cursor = i === null ? '' : 'pointer';
  };
  const onLeave = () => { if (hovering !== null) { hovering = null; pick(null); } };
  const onDown = (e) => { const i = hit(e); hovering = i; pick(i); };
  canvas.addEventListener('pointermove', onMove);
  canvas.addEventListener('pointerleave', onLeave);
  canvas.addEventListener('pointerdown', onDown);

  rt.onFrame = (t, dt) => {
    parts.forEach((m, i) => {
      const on = current.selected === i;
      const lift = on ? 0.22 : 0;
      m.position.z = still || !dt ? lift : m.position.z + (lift - m.position.z) * 0.18;
      m.material.emissiveIntensity = on ? 0.18 : 0;
    });
    if (!still && current.selected === null) ring.rotation.z += dt * 0.12;
  };

  rt.start();

  return {
    update(next) {
      if (next.onPick) pick = next.onPick;
      const rebuild = next.slices !== undefined && JSON.stringify(next.slices) !== JSON.stringify(current.slices);
      current = { ...current, ...next };
      if (rebuild) build();
      rt.frame();
    },
    setPalette(p) { current = { ...current, palette: p }; paint(); rt.setPalette(p); rt.frame(); },
    setActive(on) { on ? rt.play() : rt.pause(); },
    dispose() {
      canvas.removeEventListener('pointermove', onMove);
      canvas.removeEventListener('pointerleave', onLeave);
      canvas.removeEventListener('pointerdown', onDown);
      parts.forEach((m) => m.material.dispose());
      rt.dispose();
    },
  };
}
