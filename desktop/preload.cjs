const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('desktopAPI', Object.freeze({
  isDesktop: true,
  resetAdminPassword: request => ipcRenderer.invoke('desktop:reset-admin', request)
}));
