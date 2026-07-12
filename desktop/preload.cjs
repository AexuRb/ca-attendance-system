const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('desktopAPI', Object.freeze({
  isDesktop: true,
  loadRememberedCredentials: () => ipcRenderer.invoke('ca-attendance:credentials:load'),
  saveRememberedCredentials: credentials => ipcRenderer.invoke('ca-attendance:credentials:save', credentials),
  clearRememberedCredentials: () => ipcRenderer.invoke('ca-attendance:credentials:clear')
}));
