const TOKEN_KEY = 'ca_attendance_token'

export function getToken() {
  return localStorage.getItem(TOKEN_KEY) || ''
}

export function setToken(token) {
  if (token) localStorage.setItem(TOKEN_KEY, token)
  else localStorage.removeItem(TOKEN_KEY)
}

export async function api(path, options = {}) {
  const isFormData = typeof FormData !== 'undefined' && options.body instanceof FormData
  const headers = {
    ...(options.body && !isFormData ? { 'Content-Type': 'application/json' } : {}),
    ...(getToken() ? { Authorization: `Bearer ${getToken()}` } : {}),
    ...(options.headers || {})
  }
  const res = await fetch(path, { ...options, headers })
  if (!res.ok) {
    let message = `请求失败：${res.status}`
    try {
      const data = await res.json()
      message = data.message || message
    } catch {
      // keep default
    }
    throw new Error(message)
  }
  if (res.status === 204) return null
  const type = res.headers.get('content-type') || ''
  if (type.includes('application/json')) return res.json()
  return res.blob()
}

export function post(path, body) {
  return api(path, { method: 'POST', body: JSON.stringify(body || {}) })
}

export function put(path, body) {
  return api(path, { method: 'PUT', body: JSON.stringify(body || {}) })
}

export function del(path) {
  return api(path, { method: 'DELETE' })
}
