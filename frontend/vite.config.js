import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'node:path'

export default defineConfig({
  plugins: [vue()],
  test: {
    environment: 'node',
    clearMocks: true,
    exclude: ['e2e/**', '**/node_modules/**', '**/.git/**']
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  },
  build: {
    outDir: resolve(__dirname, '../backend/src/main/resources/static'),
    emptyOutDir: true
  }
})
