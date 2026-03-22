// SPDX-License-Identifier: Apache-2.0
// Polyfill ResizeObserver for JSDOM
if (typeof globalThis.ResizeObserver === 'undefined') {
  globalThis.ResizeObserver = class ResizeObserver {
    private callback: ResizeObserverCallback;
    constructor(callback: ResizeObserverCallback) {
      this.callback = callback;
    }
    observe(target: Element) {
      // Fire once with a mock entry
      this.callback([{
        contentRect: { width: 800, height: 600 } as DOMRectReadOnly,
        target,
        borderBoxSize: [],
        contentBoxSize: [],
        devicePixelContentBoxSize: [],
      }], this);
    }
    unobserve() {}
    disconnect() {}
  };
}
