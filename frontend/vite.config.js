import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// In dev, /api is proxied to the gateway so the browser talks to one origin (same as nginx in prod).
export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000,
    proxy: { '/api': process.env.GATEWAY_URL || 'http://localhost:8080' },
  },
});
