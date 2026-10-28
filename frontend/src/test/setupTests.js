import '@testing-library/jest-dom';
import { configure } from '@testing-library/react';

// Generous async timeout: a loaded CI/laptop must not turn slow renders into flaky failures
configure({ asyncUtilTimeout: 5000 });

// jsdom has no matchMedia; layout hooks used by shared components expect it
if (!window.matchMedia) {
  window.matchMedia = (query) => ({
    matches: false, media: query, onchange: null, addListener() {}, removeListener() {},
    addEventListener() {}, removeEventListener() {}, dispatchEvent() { return false; },
  });
}

// React Router 6 prints v7-migration notices on every render; they are noise in test output
const originalWarn = console.warn;
console.warn = (...args) => {
  if (typeof args[0] === 'string' && args[0].includes('React Router Future Flag')) return;
  originalWarn(...args);
};
