import {
  createRouter,
  createWebHashHistory
} from 'vue-router'

const routePlaceholder = { render: () => null }

export const adminModuleRouteNames = Object.freeze({
  overview: 'today',
  reviews: 'reviews',
  records: 'records',
  members: 'members',
  stats: 'stats',
  trainings: 'trainings',
  schedules: 'schedules',
  repairs: 'repairs',
  data: 'data',
  settings: 'settings',
  logs: 'logs',
  profile: 'profile'
})

const routeNameToTab = Object.freeze(Object.fromEntries(
  Object.entries(adminModuleRouteNames).map(([tab, routeName]) => [routeName, tab])
))

export function adminModuleLocation(tab, query = {}) {
  const module = adminModuleRouteNames[tab] || adminModuleRouteNames.overview
  return {
    name: 'admin-module',
    params: { module },
    query
  }
}

export function tabFromRoute(route) {
  if (route?.name !== 'admin-module') return null
  return routeNameToTab[String(route.params?.module || '')] || null
}

export function createAppRouter(history = createWebHashHistory()) {
  return createRouter({
    history,
    routes: [
      { path: '/', redirect: '/kiosk' },
      { path: '/kiosk', name: 'kiosk', component: routePlaceholder },
      { path: '/login', name: 'login', component: routePlaceholder },
      { path: '/password-change', name: 'password-change', component: routePlaceholder },
      {
        path: '/admin/:module',
        name: 'admin-module',
        component: routePlaceholder,
        beforeEnter(to) {
          return routeNameToTab[String(to.params.module || '')]
            ? true
            : adminModuleLocation('overview')
        }
      },
      { path: '/:pathMatch(.*)*', redirect: '/kiosk' }
    ]
  })
}

export const router = createAppRouter()
