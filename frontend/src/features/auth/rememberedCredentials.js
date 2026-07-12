export const REMEMBERED_ACCOUNT_KEY = 'ca-attendance-remembered-account'

function desktopBridge() {
  return typeof window !== 'undefined' ? window.desktopAPI : null
}

function localStorageAvailable() {
  return typeof window !== 'undefined' && window.localStorage
}

function readRememberedAccount() {
  try {
    return localStorageAvailable()?.getItem(REMEMBERED_ACCOUNT_KEY) || ''
  } catch {
    return ''
  }
}

function writeRememberedAccount(account) {
  try {
    if (!localStorageAvailable()) return
    if (account) window.localStorage.setItem(REMEMBERED_ACCOUNT_KEY, account)
    else window.localStorage.removeItem(REMEMBERED_ACCOUNT_KEY)
  } catch {
    // Browser storage is optional and must never block login.
  }
}

export function hasSecureCredentialStorage() {
  const bridge = desktopBridge()
  return Boolean(
    bridge?.isDesktop &&
    typeof bridge.loadRememberedCredentials === 'function' &&
    typeof bridge.saveRememberedCredentials === 'function' &&
    typeof bridge.clearRememberedCredentials === 'function'
  )
}

export async function loadRememberedLogin() {
  if (hasSecureCredentialStorage()) {
    try {
      const credentials = await desktopBridge().loadRememberedCredentials()
      if (credentials?.account && credentials?.password) {
        writeRememberedAccount('')
        return {
          account: String(credentials.account),
          password: String(credentials.password),
          remembersPassword: true,
          mode: 'secure'
        }
      }
    } catch {
      // A moved or unavailable encrypted store falls back to the account only.
    }
  }

  const account = readRememberedAccount()
  return {
    account,
    password: '',
    remembersPassword: false,
    mode: account ? 'account-only' : 'empty'
  }
}

export async function persistRememberedLogin({ account, password, remember }) {
  const normalizedAccount = String(account || '').trim()
  const normalizedPassword = String(password || '')

  if (!remember) {
    if (hasSecureCredentialStorage()) {
      try {
        await desktopBridge().clearRememberedCredentials()
      } catch {
        // Clearing browser state still succeeds if the desktop store is unavailable.
      }
    }
    writeRememberedAccount('')
    return { mode: 'cleared', savedPassword: false }
  }

  if (hasSecureCredentialStorage()) {
    try {
      await desktopBridge().saveRememberedCredentials({
        account: normalizedAccount,
        password: normalizedPassword
      })
      writeRememberedAccount('')
      return { mode: 'secure', savedPassword: true }
    } catch {
      writeRememberedAccount(normalizedAccount)
      return { mode: 'failed', savedPassword: false }
    }
  }

  writeRememberedAccount(normalizedAccount)
  return { mode: 'account-only', savedPassword: false }
}
