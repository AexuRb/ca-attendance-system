import { createApp } from 'vue'
import App from './App.vue'
import { router } from './app/router.js'
import './styles.css'
import './design-tokens.css'
import './admin-blueprint.css'
import './kiosk-portal.css'
import './login-access.css'

createApp(App).use(router).mount('#app')
