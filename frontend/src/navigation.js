// Thin wrapper around window.location's navigation methods. Exists purely so tests can mock
// navigation via jest.mock(this module) — jsdom (matching real browsers) makes Location's own
// methods non-writable and non-configurable, so they can no longer be mocked by reassigning or
// redefining them directly on window.location itself.

export function redirectTo(url) {
  window.location.replace(url);
}

export function reloadPage() {
  window.location.reload();
}
