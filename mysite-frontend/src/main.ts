import '@/styles/main.css'
import '@/styles/glass.css'
import '@/styles/transitions.css'
import '@/styles/typography.css'
import '@/styles/code.css'
import 'katex/dist/katex.min.css'

import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { createUnhead, VueHeadMixin, headSymbol } from '@unhead/vue'
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
const unhead = createUnhead()

app.use(createPinia())
app.use(router)
app.provide(headSymbol, unhead)
app.mixin(VueHeadMixin)

app.mount('#app')
