// @ts-check
const { defineConfig, devices } = require('@playwright/test');

// Points at the local docker-compose stack by default (frontend :80, proxying /api to
// the backend — see frontend/nginx.conf). Override for other environments, e.g.:
//   FRONTEND_URL=https://pharmatrack-frontend-xhlza2c2ua-el.a.run.app npx playwright test
const FRONTEND_URL = process.env.FRONTEND_URL || 'http://localhost';

module.exports = defineConfig({
  testDir: './tests',
  fullyParallel: false, // tests share seeded demo accounts/state — safer sequential by default
  workers: 1,
  retries: process.env.CI ? 1 : 0,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: FRONTEND_URL,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
  ],
});
