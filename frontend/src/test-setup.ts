// sockjs-client references Node.js `global` which doesn't exist in browsers.
// This polyfill must run before any test bundle is loaded.
(window as any)['global'] = window;
