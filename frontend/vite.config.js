import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// strictPort: always use 5173, the address the backend's CORS settings allow
export default defineConfig({
  plugins: [react()],
  server: { port: 5173, strictPort: true },
});
