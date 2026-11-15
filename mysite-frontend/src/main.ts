import '@/styles/main.css'
import '@/styles/glass.css'
import '@/styles/transitions.css'
import '@/styles/typography.css'
import '@/styles/code.css'
import '@/styles/post.css'
import '@/styles/chat.css'
import 'katex/dist/katex.min.css'

import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { createHead } from '@unhead/vue/client'
import { VueHeadMixin, headSymbol } from '@unhead/vue'
import { gsap } from 'gsap'
import { ScrollTrigger } from 'gsap/ScrollTrigger'
import App from './app/App.vue'
import router from './app/router'

// Register GSAP plugins once at app level
gsap.registerPlugin(ScrollTrigger)

// Project-wide defaults: smooth easing, reasonable duration
gsap.defaults({
  duration: 0.5,
  ease: 'power2.out',
  overwrite: 'auto',
})

const app = createApp(App)
// 必须用 client 版 createHead：createUnhead 只是核心管理器，不带 DOM 渲染插件，
// 否则 useHead 解析正常但 title/meta 永远不会写进 document（dirty 常驻 true）
const unhead = createHead()

app.use(createPinia())
app.use(router)
app.provide(headSymbol, unhead)
app.mixin(VueHeadMixin)

app.mount('#app')
