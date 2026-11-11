import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// En desarrollo, /api se redirige al backend Spring Boot (puerto 8080).
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    strictPort: true, // si 5173 esta ocupado, avisa en vez de abrir una segunda copia en 5174
    proxy: {
      '/api': 'http://localhost:8080'
    }
  }
});
