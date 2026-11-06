// Shared plumbing for the Home scenes (hero coin stack, spending ring): renderer, sizing, lights and
// a render loop that can pause. Scenes only describe their objects and what changes per frame.
import { AmbientLight, DirectionalLight, HemisphereLight, PerspectiveCamera, Scene, SRGBColorSpace, WebGLRenderer } from 'three';

export function createRuntime(canvas, { fov = 40, z = 10, still = false, palette }) {
  let renderer;
  try {
    renderer = new WebGLRenderer({ canvas, alpha: true, antialias: true, powerPreference: 'low-power' });
  } catch {
    return null; // no WebGL: the card keeps its flat look
  }
  // Phones have dense screens; 1.75 looks sharp and saves a lot of fill rate over 3.
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 1.75));
  renderer.outputColorSpace = SRGBColorSpace;

  const scene = new Scene();
  const camera = new PerspectiveCamera(fov, 1, 0.1, 100);
  camera.position.set(0, 0, z);

  const ambient = new AmbientLight('#ffffff', 0.9);
  const hemi = new HemisphereLight('#fff6ea', '#e9e3d4', 0.7);
  const key = new DirectionalLight('#fff3e6', 1.9);
  key.position.set(4, 6, 6);
  const fill = new DirectionalLight('#ffffff', 0.35);
  fill.position.set(-5, -2, 3);
  scene.add(ambient, hemi, key, fill);
  const light = (p) => {
    ambient.intensity = p.dark ? 0.55 : 0.9;
    hemi.groundColor.set(p.dark ? '#1f1e1d' : '#e9e3d4');
    key.intensity = p.dark ? 1.6 : 1.9;
    fill.color.set(p.accent);
  };
  light(palette);

  const rt = {
    scene, camera, renderer, still, width: 1, height: 1,
    onFrame: () => {}, onResize: () => {},
    // Size of the visible area at z = 0, in world units.
    view() {
      const h = 2 * camera.position.z * Math.tan((camera.fov * Math.PI) / 360);
      return { w: h * camera.aspect, h };
    },
    render() { renderer.render(scene, camera); },
    setPalette(p) { light(p); },
  };

  const resize = () => {
    rt.width = canvas.clientWidth || 1;
    rt.height = canvas.clientHeight || 1;
    renderer.setSize(rt.width, rt.height, false);
    camera.aspect = rt.width / rt.height;
    camera.updateProjectionMatrix();
    rt.onResize();
    if (!raf) rt.render();
  };
  const ro = typeof ResizeObserver !== 'undefined' ? new ResizeObserver(resize) : null;
  ro?.observe(canvas);

  let raf = 0;
  let last = 0;
  let t = 0;
  const loop = (now) => {
    const dt = Math.min(0.05, (now - last) / 1000 || 0);
    last = now;
    t += dt;
    rt.onFrame(t, dt);
    rt.render();
    raf = requestAnimationFrame(loop);
  };
  rt.play = () => {
    if (still || raf) return;
    last = performance.now();
    raf = requestAnimationFrame(loop);
  };
  rt.pause = () => { cancelAnimationFrame(raf); raf = 0; };
  // With reduced motion, scenes jump to their final state and draw a single frame on each change.
  rt.frame = () => { if (!raf) { rt.onFrame(still ? 99 : t, 0); rt.render(); } };
  rt.start = () => { resize(); still ? rt.frame() : rt.play(); };
  rt.dispose = () => {
    rt.pause();
    ro?.disconnect();
    scene.traverse((o) => { o.geometry?.dispose(); });
    renderer.dispose();
  };
  return rt;
}
