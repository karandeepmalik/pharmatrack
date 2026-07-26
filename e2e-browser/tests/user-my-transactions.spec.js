const { test, expect } = require('@playwright/test');
const { loginAsUser, PAYMENT_SCREENSHOT, scrollUntilVisible } = require('./helpers');

async function submitDispatch(page, note) {
  await page.goto('/user/submit');
  await page.locator('#pharma-select').selectOption({ index: 1 });
  await page.locator('#type-select').selectOption({ index: 1 });
  await page.locator('#spec-select').selectOption({ index: 1 });
  await page.locator('#quantity-input').fill('0.1');
  await page.locator('#notes-input').fill(note);
  await page.locator('#screenshot-input').setInputFiles(PAYMENT_SCREENSHOT);
  await page.getByRole('button', { name: /submit medicine dispatch/i }).click();
  await expect(page.getByRole('alert')).toContainText(/pending admin approval/i, { timeout: 10000 });
}

test.describe('Medicine Dispatch History (user)', () => {
  test.beforeEach(async ({ page }) => {
    await loginAsUser(page, 'john');
  });

  test('shows the page heading and filter tabs', async ({ page }) => {
    await page.goto('/user/transactions');
    await expect(page.getByRole('heading', { name: /medicine dispatch history/i })).toBeVisible();
    for (const tab of ['ALL', 'PENDING', 'APPROVED', 'REJECTED']) {
      await expect(page.getByRole('button', { name: new RegExp(`^${tab}$`, 'i') })).toBeVisible();
    }
  });

  test('a freshly submitted dispatch appears in the history list', async ({ page }) => {
    test.setTimeout(60000);
    const note = `My history note ${Date.now()}`;
    await submitDispatch(page, note);

    await page.goto('/user/transactions');
    // Page size is 10 and this test run may have created many same-day dispatches earlier,
    // pushing this one past page 0 — scroll (real infinite-scroll, not a mock) until it loads.
    await scrollUntilVisible(page, page.getByText(note), { maxScrolls: 60 });
  });

  test('PENDING tab filters to only pending dispatches', async ({ page }) => {
    test.setTimeout(60000);
    const note = `Pending-tab note ${Date.now()}`;
    await submitDispatch(page, note);

    await page.goto('/user/transactions');
    await scrollUntilVisible(page, page.getByText(note), { maxScrolls: 60 });
    await page.getByRole('button', { name: /^pending$/i }).click();
    await expect(page.getByText(note)).toBeVisible();
  });

  test('a PENDING dispatch can be deleted by its own submitter', async ({ page }) => {
    test.setTimeout(60000);
    const note = `Self-delete note ${Date.now()}`;
    await submitDispatch(page, note);

    await page.goto('/user/transactions');
    const card = page.locator('.transaction-card', { hasText: note });
    await scrollUntilVisible(page, card, { maxScrolls: 60 });
    await page.getByRole('button', { name: /^pending$/i }).click();
    await expect(card).toBeVisible();

    page.once('dialog', dialog => dialog.accept());
    await card.getByRole('button', { name: /delete/i }).click();

    await expect(page.locator('.transaction-card', { hasText: note })).not.toBeVisible({ timeout: 10000 });
  });

  test('searching notes filters the list client-side', async ({ page }) => {
    test.setTimeout(60000);
    const uniqueTag = `uniquetag${Date.now()}`;
    await submitDispatch(page, `Note with ${uniqueTag} inside it`);

    await page.goto('/user/transactions');
    // Load enough pages that the freshly submitted item is present in loaded state before
    // filtering — the notes search only filters what's already loaded, it doesn't re-query.
    // A long-running test session can accumulate a lot of same-day demo data, so this may take
    // many pages of scrolling.
    await scrollUntilVisible(page, page.getByText(uniqueTag, { exact: false }), { maxScrolls: 60 });

    await page.locator('#notes-search').fill(uniqueTag);
    await expect(page.getByText(uniqueTag, { exact: false })).toBeVisible();

    await page.locator('#notes-search').fill('text-that-should-not-match-anything-xyz');
    await expect(page.getByText(/no transactions found/i)).toBeVisible();
  });
});
