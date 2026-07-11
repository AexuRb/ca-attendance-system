export function queryText(query, key, fallback = '', maxLength = 120) {
  const raw = Array.isArray(query?.[key]) ? query[key][0] : query?.[key]
  if (raw === null || raw === undefined) return fallback
  return String(raw).trim().slice(0, maxLength)
}

export function queryOneOf(query, key, allowed, fallback = '') {
  const value = queryText(query, key, fallback)
  return allowed.includes(value) ? value : fallback
}

export function queryPositiveInt(query, key, fallback = 1, allowed = null) {
  const value = Number.parseInt(queryText(query, key, String(fallback)), 10)
  if (!Number.isInteger(value) || value < 1) return fallback
  if (allowed && !allowed.includes(value)) return fallback
  return value
}

export function queryDate(query, key, fallback) {
  const value = queryText(query, key, fallback, 10)
  return /^\d{4}-\d{2}-\d{2}$/.test(value) ? value : fallback
}

export function compactQuery(values) {
  return Object.fromEntries(Object.entries(values).filter(([, value]) => (
    value !== '' && value !== null && value !== undefined
  )))
}
