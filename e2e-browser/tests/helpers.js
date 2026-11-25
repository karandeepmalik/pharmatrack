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

/**
 * Approves or rejects a single dispatch card on the Review Adjustments page
 * (/admin/transactions). Individual per-card decision buttons were removed in favor of
 * select-then-bulk-act, so even a single-item decision now goes through the bulk flow — this
 * is that flow's one-liner. Assumes `card` (a `.transaction-card` locator) is already visible.
 */
async function decideCard(page, card, approved) {
  await card.getByRole('checkbox', { name: /select transaction/i }).check();
  await page.getByRole('button', { name: approved ? /approve selected/i : /reject selected/i }).click();
}

async function approveCard(page, card) {
  await decideCard(page, card, true);
}

async function rejectCard(page, card) {
  await decideCard(page, card, false);
}

/**
 * Opens a MultiSelectDropdown filter (by the id passed to its `id` prop, e.g. "user-filter"),
 * checks the checkbox matching optionName, then closes the panel (Escape) — MultiSelectDropdown
 * .jsx's checkbox-panel replacement for every FILTER-purpose <select> in the app (native
 * <select multiple> was rejected: it needs ctrl/cmd-click to pick more than one option and has
 * poor touch support). Closing afterward matters, not just for test hygiene: the panel is
 * absolutely positioned and overlaps whatever sits below it (e.g. a Search button in a
 * one-row form), so a still-open panel genuinely blocks clicks there — exactly what a real user
 * would also need to dismiss first. To select more than one option from the same dropdown
 * before closing it, open it directly (`page.locator('#id-toggle').click()`) and `.check()`
 * each checkbox in its panel before pressing Escape.
 */
async function pickMultiSelectOption(page, filterId, optionName) {
  await page.locator(`#${filterId}-toggle`).click();
  const panel = page.getByRole('listbox').filter({ has: page.getByRole('checkbox', { name: optionName }) });
  await panel.getByRole('checkbox', { name: optionName }).check();
  await page.keyboard.press('Escape');
}

/** Clicks the "All ..." checkbox in a MultiSelectDropdown filter's panel, clearing its selection, then closes it. */
async function clearMultiSelect(page, filterId, allLabel = /^all/i) {
  await page.locator(`#${filterId}-toggle`).click();
  const panel = page.getByRole('listbox').filter({ has: page.getByRole('checkbox', { name: allLabel }) });
  await panel.getByRole('checkbox', { name: allLabel }).check();
  await page.keyboard.press('Escape');
}

module.exports = {
  CREDENTIALS, PAYMENT_SCREENSHOT, login, loginAsAdmin, loginAsUser, scrollUntilVisible,
  approveCard, rejectCard, pickMultiSelectOption, clearMultiSelect,
};
