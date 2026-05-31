import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

export default defineConfig({
  plugins: [
    vue(),
    AutoImport({
      resolvers: [ElementPlusResolver({ importStyle: 'css' })],
      dts: 'src/auto-imports.d.ts'
    }),
    Components({
      resolvers: [ElementPlusResolver({ importStyle: 'css' })],
      dts: 'src/components.d.ts'
    })
  ],
  build: {
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (id.includes('node_modules')) {
            if (id.includes('element-plus')) return 'element-plus'
            if (id.includes('echarts')) return 'echarts'
            if (id.includes('/vue') || id.includes('@vue') || id.includes('pinia') || id.includes('vue-router')) {
              return 'vue-vendor'
            }
            return 'vendor'
          }
          if (id.includes('/src/views/Bi') || id.includes('/src/components/bi/')) return 'domain-bi'
          if (id.includes('/src/views/Saga')) return 'domain-saga'
          if (id.includes('/src/views/AgencyPurchase') || id.includes('/src/views/PilotClosure')) {
            return 'domain-pilot'
          }
          if (id.includes('/src/views/Clearing') || id.includes('/src/views/BankFlow')) return 'domain-account'
        }
      }
    }
  },
  server: {
    port: 5173,
    proxy: {
      '/api/v1': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
