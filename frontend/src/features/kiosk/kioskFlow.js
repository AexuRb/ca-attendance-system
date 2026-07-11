export const KIOSK_RESET_DELAY_MS = 4_000

export function maskStudentNumber(studentNo) {
  const value = String(studentNo || '').trim()
  return value ? value.slice(-4) : '----'
}

export function createKioskRequestId(uuidFactory = () => globalThis.crypto?.randomUUID?.()) {
  const generated = uuidFactory?.()
  if (generated) return String(generated)
  return `kiosk-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`
}

export function createKioskResetTimer({
  durationMs = KIOSK_RESET_DELAY_MS,
  onTick,
  onReset
}) {
  let intervalId = null
  let timeoutId = null
  let deadline = 0

  function cancel() {
    if (intervalId !== null) globalThis.clearInterval(intervalId)
    if (timeoutId !== null) globalThis.clearTimeout(timeoutId)
    intervalId = null
    timeoutId = null
  }

  function start() {
    cancel()
    deadline = Date.now() + durationMs
    onTick(Math.ceil(durationMs / 1_000))
    intervalId = globalThis.setInterval(() => {
      onTick(Math.max(0, Math.ceil((deadline - Date.now()) / 1_000)))
    }, 1_000)
    timeoutId = globalThis.setTimeout(() => {
      cancel()
      onReset()
    }, durationMs)
  }

  return { start, cancel }
}
