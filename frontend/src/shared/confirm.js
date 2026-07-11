import { reactive } from 'vue'

const defaults = {
  open: false,
  title: '',
  message: '',
  confirmLabel: '确认',
  cancelLabel: '取消',
  requiredText: '',
  inputLabel: '',
  inputPlaceholder: '',
  inputRequired: false,
  returnInput: false,
  tone: 'danger'
}

let resolver = null

export const confirmationState = reactive({ ...defaults })

function finish(value) {
  const currentResolver = resolver
  resolver = null
  Object.assign(confirmationState, defaults)
  currentResolver?.(value)
}

export function requestConfirmation(options = {}) {
  if (resolver) finish(false)
  Object.assign(confirmationState, defaults, options, { open: true })
  return new Promise(resolve => {
    resolver = resolve
  })
}

export function requestTextInput(options = {}) {
  return requestConfirmation({
    confirmLabel: '确认',
    tone: 'warning',
    ...options,
    inputRequired: true,
    returnInput: true
  })
}

export function acceptConfirmation(input = '') {
  if (!confirmationState.open) return false
  if (confirmationState.requiredText && input !== confirmationState.requiredText) return false
  const trimmedInput = input.trim()
  if (confirmationState.inputRequired && !trimmedInput) return false
  finish(confirmationState.returnInput ? trimmedInput : true)
  return true
}

export function cancelConfirmation() {
  if (!confirmationState.open) return
  finish(false)
}

export function resetConfirmation() {
  if (resolver) resolver(false)
  resolver = null
  Object.assign(confirmationState, defaults)
}
