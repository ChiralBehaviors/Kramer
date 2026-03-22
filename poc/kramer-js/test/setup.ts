// SPDX-License-Identifier: Apache-2.0
// Polyfill ResizeObserver for JSDOM
if (typeof globalThis.ResizeObserver === 'undefined') {
  globalThis.ResizeObserver = class ResizeObserver {
    private callback: ResizeObserverCallback;
    constructor(callback: ResizeObserverCallback) {
      this.callback = callback;
    }
    observe() {
      // Fire once with a mock entry for test rendering
      this.callback([{
        contentRect: { width: 800, height: 600 } as DOMRectReadOnly,
        target: document.createElement('div'),
        borderBoxSize: [],
        contentBoxSize: [],
        devicePixelContentBoxSize: [],
      }], this);
    }
    unobserve() {}
    disconnect() {}
  };
}
