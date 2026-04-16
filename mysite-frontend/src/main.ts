import './assets/main.css'
import './assets/blog.css'

import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'
import { initTheme } from './utils/theme'

const app = createApp(App)

app.use(createPinia())
app.use(router)

// 初始化主题（在应用挂载前）
initTheme()

app.mount('#app')
