const path = require('path');

const CREDENTIALS = {
  admin: { username: 'admin', password: 'Admin@123' },
  john: { username: 'john.doe', password: 'User@123' },
  jane: { username: 'jane.smith', password: 'User@123' },
};

const PAYMENT_SCREENSHOT = path.join(__dirname, '..', 'fixtures', 'payment-screenshot.png');

/** Logs in via the real login form and waits for the post-login redirect. */
async function login(page, { username, password }, expectedPath = /\/(admin|user)\/dashboard/) {
  await page.goto('/login');
  await page.locator('#username').fill(username);
  await page.locator('#password').fill(password);
  await page.getByRole('button', { name: /sign in/i }).click();
  await page.waitForURL(expectedPath);
}

async function loginAsAdmin(page) {
  await login(page, CREDENTIALS.admin, '**/admin/dashboard');
}

async function loginAsUser(page, who = 'john') {
  await login(page, CREDENTIALS[who], '**/user/dashboard');
}

/**
 * Repeatedly scrolls to the bottom of the page to trigger the scroll-sentinel's next-page
 * load, until `locator` becomes visible or there's no more data to load. Needed for lists
 * where PAGE_SIZE=10 and same-day submissions (a common occurrence in a test run that submits
 * many dispatches quickly, since submittedAt is truncated to the dispatch date) can push a
 * specific item past the first page — a real user submitting occasionally wouldn't hit this,
 * but a test run creating dozens of same-day records does.
 */
async function scrollUntilVisible(page, locator, { maxScrolls = 15 } = {}) {
  for (let i = 0; i < maxScrolls; i++) {
    if (await locator.isVisible()) return;
    await page.mouse.wheel(0, 20000);
    await page.waitForTimeout(400);
  }
  await locator.waitFor({ state: 'visible', timeout: 5000 });
}

module.exports = { CREDENTIALS, PAYMENT_SCREENSHOT, login, loginAsAdmin, loginAsUser, scrollUntilVisible };
