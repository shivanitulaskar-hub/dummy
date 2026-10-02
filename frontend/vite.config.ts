/// <reference types="vitest/config" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// In development the Vite server proxies /api to the Spring Boot backend, mirroring nginx in production,
// so the browser always talks to a single origin.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: process.env.VITE_API_PROXY_TARGET ?? 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  build: {
    outDir: 'dist',
    sourcemap: false,
    rollupOptions: {
      output: {
        // Separate long-lived vendor chunks so app updates don't invalidate the browser cache for libraries.
        manualChunks: {
          react: ['react', 'react-dom', 'react-router-dom'],
          mui: ['@mui/material', '@mui/icons-material', '@emotion/react', '@emotion/styled'],
          query: ['@tanstack/react-query', 'axios'],
        },
      },
    },
  },
  test: {
    environment: 'node',
  },
});
