const { contextBridge } = require('electron');

contextBridge.exposeInMainWorld('desktopAPI', Object.freeze({
  isDesktop: true
}));
