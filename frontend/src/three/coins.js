// three.js scenes made of rupee coins. Loaded on demand (see components/Coins3D.jsx) so the
// rest of the app does not pay for the 3D library.
//
// modes:
//   field  - many coins drifting and turning, follows the pointer a little (auth pages)
//   spin   - one coin turning slowly (waiting for the UPI app)
//   burst  - one coin drops in and flips, small coins burst outwards (payment logged)
import {
  AmbientLight,
  CanvasTexture,
  CircleGeometry,
  Color,
  CylinderGeometry,
  DirectionalLight,
  Group,
  MeshStandardMaterial,
  Mesh,
  PerspectiveCamera,
  Scene,
  SRGBColorSpace,
  WebGLRenderer,
} from 'three';

function faceTexture(base, ink) {
  const size = 256;
  const c = document.createElement('canvas');
  c.width = c.height = size;
  const g = c.getContext('2d');
  const grad = g.createRadialGradient(size * 0.38, size * 0.34, size * 0.05, size / 2, size / 2, size / 2);
  grad.addColorStop(0, new Color(base).lerp(new Color('#ffffff'), 0.35).getStyle());
  grad.addColorStop(1, base);
  g.fillStyle = grad;
  g.fillRect(0, 0, size, size);
  g.strokeStyle = ink;
  g.globalAlpha = 0.55;
  g.lineWidth = 8;
  g.beginPath();
  g.arc(size / 2, size / 2, size * 0.4, 0, Math.PI * 2);
  g.stroke();
  g.globalAlpha = 0.9;
  g.fillStyle = ink;
  g.font = `600 ${size * 0.5}px Georgia, 'Times New Roman', serif`;
  g.textAlign = 'center';
  g.textBaseline = 'middle';
  g.fillText('₹', size / 2, size / 2 + size * 0.03);
  const t = new CanvasTexture(c);
  t.colorSpace = SRGBColorSpace;
  return t;
}

function coinMaterials(base, ink) {
  const face = faceTexture(base, ink);
  const edge = new MeshStandardMaterial({ color: new Color(base).multiplyScalar(0.8), metalness: 0.55, roughness: 0.35 });
  const top = new MeshStandardMaterial({ map: face, metalness: 0.35, roughness: 0.4 });
  return { edge, face: top, dispose: () => { face.dispose(); edge.dispose(); top.dispose(); } };
}

const rand = (a, b) => a + Math.random() * (b - a);

export function mountCoins(canvas, { mode = 'field', colors, reduced = false }) {
  let renderer;
  try {
    renderer = new WebGLRenderer({ canvas, alpha: true, antialias: true, powerPreference: 'low-power' });
  } catch {
    return null; // no WebGL: the page just shows its normal background
  }
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
  renderer.outputColorSpace = SRGBColorSpace;

  const scene = new Scene();
  const camera = new PerspectiveCamera(mode === 'field' ? 45 : 35, 1, 0.1, 100);
  camera.position.set(0, 0, mode === 'field' ? 14 : 6);

  const ambient = new AmbientLight('#ffffff', 1.1);
  const key = new DirectionalLight('#ffffff', 2.2);
  key.position.set(3, 5, 6);
  const rim = new DirectionalLight('#ffffff', 0.8);
  rim.position.set(-5, -2, -3);
  scene.add(ambient, key, rim);

  // The rim is a cylinder; each face is a circle in front of its cap so the ₹ reads the
  // right way round from both sides (cylinder cap UVs are rotated).
  const geometry = new CylinderGeometry(1, 1, 0.16, 64, 1);
  geometry.rotateX(Math.PI / 2); // face the camera
  const faceGeometry = new CircleGeometry(0.995, 64);

  let palettes = [];
  const setPalettes = (c) => {
    palettes.forEach((p) => p.dispose());
    palettes = [coinMaterials(c.primary, c.ink), coinMaterials(c.accent, c.ink)];
  };
  setPalettes(colors);

  const root = new Group();
  scene.add(root);
  const coins = [];

  const addCoin = (opts) => {
    const pal = palettes[opts.palette || 0];
    const m = new Group();
    const rimMesh = new Mesh(geometry, pal.edge);
    const front = new Mesh(faceGeometry, pal.face);
    front.position.z = 0.081;
    const back = new Mesh(faceGeometry, pal.face);
    back.position.z = -0.081;
    back.rotation.y = Math.PI;
    m.add(rimMesh, front, back);
    m.userData.parts = { rimMesh, front, back };
    m.scale.setScalar(opts.scale || 1);
    root.add(m);
    const coin = { mesh: m, palette: opts.palette || 0, ...opts };
    coins.push(coin);
    return coin;
  };

  if (mode === 'field') {
    for (let i = 0; i < 16; i++) {
      const c = addCoin({
        palette: i % 4 === 3 ? 1 : 0,
        scale: rand(0.35, 0.85),
        home: [rand(-9, 9), rand(-6, 6), rand(-6, 1)],
        spin: [rand(-0.5, 0.5), rand(0.2, 0.8), rand(-0.2, 0.2)],
        bob: rand(0, Math.PI * 2),
      });
      c.mesh.position.set(...c.home);
      c.mesh.rotation.set(rand(0, 6), rand(0, 6), rand(0, 6));
    }
  } else {
    addCoin({ scale: 1.25, hero: true });
    if (mode === 'burst') {
      for (let i = 0; i < 14; i++) {
        const a = (i / 14) * Math.PI * 2 + rand(-0.2, 0.2);
        const speed = rand(2.2, 3.6);
        addCoin({
          palette: i % 3 === 0 ? 1 : 0,
          scale: rand(0.14, 0.24),
          vel: [Math.cos(a) * speed, Math.sin(a) * speed + 1.5, rand(-1, 1)],
          spin: [rand(-8, 8), rand(-8, 8), 0],
          spark: true,
        });
      }
    }
  }

  const resize = () => {
    const w = canvas.clientWidth || 1;
    const h = canvas.clientHeight || 1;
    renderer.setSize(w, h, false);
    camera.aspect = w / h;
    camera.updateProjectionMatrix();
  };
  resize();
  const ro = typeof ResizeObserver !== 'undefined' ? new ResizeObserver(resize) : null;
  ro?.observe(canvas);

  const pointer = { x: 0, y: 0 };
  const onPointer = (e) => {
    pointer.x = (e.clientX / window.innerWidth) * 2 - 1;
    pointer.y = (e.clientY / window.innerHeight) * 2 - 1;
  };
  if (mode === 'field') window.addEventListener('pointermove', onPointer, { passive: true });

  const start = performance.now();
  let last = start;
  const frame = (now) => {
    const t = (now - start) / 1000;
    const dt = Math.min(0.05, (now - last) / 1000);
    last = now;

    for (const c of coins) {
      const m = c.mesh;
      if (mode === 'field') {
        m.rotation.x += c.spin[0] * dt;
        m.rotation.y += c.spin[1] * dt;
        m.rotation.z += c.spin[2] * dt;
        m.position.y = c.home[1] + Math.sin(t * 0.6 + c.bob) * 0.35;
      } else if (c.hero && mode === 'spin') {
        m.rotation.y = t * 1.6;
        m.position.y = Math.sin(t * 2) * 0.08;
      } else if (c.hero) {
        // drop in with a bounce, two flips, then settle into a gentle turn
        const p = Math.min(1, t / 0.9);
        const bounce = 1 - Math.abs(Math.cos(p * Math.PI * 1.5)) * (1 - p) ** 2;
        m.position.y = 3 * (1 - bounce);
        m.rotation.x = (1 - p) ** 2 * Math.PI * 4;
        m.rotation.y = t > 0.9 ? Math.sin((t - 0.9) * 1.4) * 0.5 : 0;
      } else if (c.spark) {
        if (t < 0.35) { m.visible = false; continue; }
        m.visible = true;
        c.vel[1] -= 6 * dt;
        m.position.x += c.vel[0] * dt;
        m.position.y += c.vel[1] * dt;
        m.position.z += c.vel[2] * dt;
        m.rotation.x += c.spin[0] * dt;
        m.rotation.y += c.spin[1] * dt;
        const life = Math.max(0, 1 - (t - 0.35) / 1.6);
        m.scale.setScalar(c.scale * life);
      }
    }
    if (mode === 'field') {
      root.rotation.y += (pointer.x * 0.25 - root.rotation.y) * 0.04;
      root.rotation.x += (pointer.y * 0.15 - root.rotation.x) * 0.04;
    }
    renderer.render(scene, camera);
  };

  let raf = 0;
  const loop = (now) => {
    frame(now);
    raf = requestAnimationFrame(loop);
  };
  const play = () => {
    if (reduced || raf) return;
    last = performance.now();
    raf = requestAnimationFrame(loop);
  };
  const pause = () => {
    cancelAnimationFrame(raf);
    raf = 0;
  };
  const onVisibility = () => (document.hidden ? pause() : play());
  document.addEventListener('visibilitychange', onVisibility);

  if (reduced) {
    // one still frame: settled hero coin, no sparks
    frame(start + (mode === 'burst' ? 2600 : mode === 'spin' ? 0 : 400));
  } else {
    play();
  }

  return {
    setColors(next) {
      setPalettes(next);
      coins.forEach((c) => {
        const { rimMesh, front, back } = c.mesh.userData.parts;
        const pal = palettes[c.palette];
        rimMesh.material = pal.edge;
        front.material = pal.face;
        back.material = pal.face;
      });
      if (reduced) renderer.render(scene, camera);
    },
    dispose() {
      pause();
      document.removeEventListener('visibilitychange', onVisibility);
      window.removeEventListener('pointermove', onPointer);
      ro?.disconnect();
      geometry.dispose();
      faceGeometry.dispose();
      palettes.forEach((p) => p.dispose());
      renderer.dispose();
    },
  };
}
