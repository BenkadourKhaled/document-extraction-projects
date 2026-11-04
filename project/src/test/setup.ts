import '@testing-library/jest-dom/vitest';
import { afterAll, afterEach, beforeAll } from 'vitest';
import { server } from './mocks/server';

// jsdom does not implement matchMedia, used by useMediaQuery.
// jsdom does not implement object URLs used by the dropzone preview.
if (!URL.createObjectURL) {
  URL.createObjectURL = () => 'blob:test';
}
if (!URL.revokeObjectURL) {
  URL.revokeObjectURL = () => undefined;
}

if (!window.matchMedia) {
  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: (query: string): MediaQueryList => ({
      matches: false,
      media: query,
      onchange: null,
      addListener: () => undefined,
      removeListener: () => undefined,
      addEventListener: () => undefined,
      removeEventListener: () => undefined,
      dispatchEvent: () => false,
    }),
  });
}

// Radix ScrollArea/AlertDialog use ResizeObserver, which jsdom does not provide.
if (!('ResizeObserver' in globalThis)) {
  class ResizeObserverMock {
    observe() {}
    unobserve() {}
    disconnect() {}
  }

  Object.defineProperty(globalThis, 'ResizeObserver', {
    configurable: true,
    writable: true,
    value: ResizeObserverMock,
  });
}

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());
