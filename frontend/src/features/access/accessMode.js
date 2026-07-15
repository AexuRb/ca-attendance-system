const LOCAL_CONTEXT = Object.freeze({
  mode: 'LOCAL',
  kioskAvailable: true,
  allowedRemoteRoles: []
})

export function normalizeAccessContext(value = LOCAL_CONTEXT) {
  const remote = value?.mode === 'REMOTE_ADMIN'
  return {
    mode: remote ? 'REMOTE_ADMIN' : 'LOCAL',
    kioskAvailable: remote ? false : value?.kioskAvailable !== false,
    allowedRemoteRoles: Array.isArray(value?.allowedRemoteRoles) ? value.allowedRemoteRoles : []
  }
}

export function inferredAccessContext(location = window.location) {
  const hostname = String(location?.hostname || '').toLowerCase()
  const port = String(location?.port || '')
  const loopback = hostname === '127.0.0.1' || hostname === 'localhost' || hostname === '::1'
  const defaultRemoteConnector = loopback && port === '8081'
  return normalizeAccessContext(loopback && !defaultRemoteConnector
    ? LOCAL_CONTEXT
    : { mode: 'REMOTE_ADMIN', kioskAvailable: false, allowedRemoteRoles: ['PRESIDENT', 'ADMIN'] })
}

export function isRemoteAccess(context) {
  return context?.mode === 'REMOTE_ADMIN'
}

export function routeForAccessMode(context, routeName, authenticated) {
  if (!isRemoteAccess(context) || routeName !== 'kiosk') return ''
  return authenticated ? '/admin/today' : '/login'
}
