import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  // npm run dev: always port 5173, the address the backend's CORS settings allow
  server: { port: 5173, strictPort: true },
  // npm run preview: serves the production build and forwards /api to the local backend,
  // the same way CloudFront forwards /api/* to Elastic Beanstalk on AWS
  preview: {
    port: 4173,
    strictPort: true,
    proxy: { '/api': 'http://localhost:8080' },
  },
});
