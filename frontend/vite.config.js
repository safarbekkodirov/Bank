import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// 🟡 PROXY — эмне үчүн?
//    Backend 8080-де иштейт, frontend 5173-тө. Эгерде түз
//    http://localhost:8080/api деп жазсак → BROWSER «CORS» деп
//    токтотот (эки башка порт = эки башка «сайт» деп санайт).
//    proxy кылсак: браузер баарын 5173 аркылуу жасайт,
//    Vite өзү тынчынча backend'ке өтүп берет. CORS ЖОК.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
      '/actuator': { target: 'http://localhost:8080', changeOrigin: true }
    }
  }
})
