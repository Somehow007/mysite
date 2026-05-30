import '@/styles/main.css'
import '@/styles/glass.css'
import '@/styles/transitions.css'
import '@/styles/typography.css'
import '@/styles/code.css'
import '@/styles/artalk.css'

import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { createUnhead, VueHeadMixin, headSymbol } from '@unhead/vue'
import App from './app/App.vue'
import router from './app/router'

const app = createApp(App)
const unhead = createUnhead()

app.use(createPinia())
app.use(router)
app.provide(headSymbol, unhead)
app.mixin(VueHeadMixin)

app.mount('#app')
