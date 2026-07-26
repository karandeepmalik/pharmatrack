const { test, expect } = require('@playwright/test');
const { loginAsAdmin, loginAsUser, PAYMENT_SCREENSHOT, scrollUntilVisible } = require('./helpers');

async function submitDispatch(page, note, dispatchDate) {
  await loginAsUser(page, 'john');
  await page.goto('/user/submit');
  await page.locator('#pharma-select').selectOption({ index: 1 });
  await page.locator('#type-select').selectOption({ index: 1 });
  await page.locator('#spec-select').selectOption({ index: 1 });
  await page.locator('#quantity-input').fill('0.1');
  await page.locator('#notes-input').fill(note);
  if (dispatchDate) await page.locator('#dispatch-date-input').fill(dispatchDate);
  await page.locator('#screenshot-input').setInputFiles(PAYMENT_SCREENSHOT);
  await page.getByRole('button', { name: /submit medicine dispatch/i }).click();
  await expect(page.getByRole('alert')).toContainText(/submitted successfully/i, { timeout: 10000 });
}

test.describe('Review Adjustments (admin approval queue)', () => {
  test('shows the ALL, PENDING, APPROVED, REJECTED filter tabs', async ({ page }) => {
    await loginAsAdmin(page);
    await page.goto('/admin/transactions');
    for (const tab of ['ALL', 'PENDING', 'APPROVED', 'REJECTED']) {
      await expect(page.getByRole('button', { name: new RegExp(`^${tab}$`, 'i') })).toBeVisible();
    }
  });

  test('a freshly submitted dispatch appears in the PENDING tab and can be approved', async ({ page }) => {
    test.setTimeout(60000);
    const note = `Approve-flow note ${Date.now()}`;
    await submitDispatch(page, note);

    await loginAsAdmin(page);
    await page.goto('/admin/transactions');
    await page.getByRole('button', { name: /^pending$/i }).click();

    // Many same-day items can share submittedAt after a long session of repeated test runs,
    // so a specific item isn't guaranteed to land on page 0 (scroll like a real user would).
    const card = page.locator('.transaction-card', { hasText: note });
    await scrollUntilVisible(page, card, { maxScrolls: 60 });
    await card.getByRole('button', { name: /approve/i }).click();

    // After approval the queue reloads from page 0 of the PENDING tab, so the now-APPROVED
    // record should disappear from PENDING.
    await expect(page.locator('.transaction-card', { hasText: note })).not.toBeVisible({ timeout: 10000 });

    await page.getByRole('button', { name: /^approved$/i }).click();
    await expect(page.locator('.transaction-card', { hasText: note })).toBeVisible({ timeout: 10000 });
  });

  test('a freshly submitted dispatch can be rejected and disappears from PENDING', async ({ page }) => {
    test.setTimeout(60000);
    const note = `Reject-flow note ${Date.now()}`;
    await submitDispatch(page, note);

    await loginAsAdmin(page);
    await page.goto('/admin/transactions');
    await page.getByRole('button', { name: /^pending$/i }).click();

    const card = page.locator('.transaction-card', { hasText: note });
    await scrollUntilVisible(page, card, { maxScrolls: 60 });
    await card.getByRole('button', { name: /reject/i }).click();

    await expect(page.locator('.transaction-card', { hasText: note })).not.toBeVisible({ timeout: 10000 });
    await page.getByRole('button', { name: /^rejected$/i }).click();
    await expect(page.locator('.transaction-card', { hasText: note })).toBeVisible({ timeout: 10000 });
  });

  test('ALL tab lists the most recently submitted dispatch first', async ({ page }) => {
    test.setTimeout(60000);
    // Use two distinct, clearly-ordered past dispatch dates rather than relying on wall-clock
    // gaps: submittedAt is derived from the dispatch date field (truncated to midnight), so two
    // same-day submissions a second apart are NOT distinguishable by timestamp — using different
    // calendar days makes the DESC ordering assertion deterministic instead of flaky.
    const olderNote = `Sort-order older ${Date.now()}`;
    await submitDispatch(page, olderNote, '2022-03-01');
    const newerNote = `Sort-order newer ${Date.now()}`;
    await submitDispatch(page, newerNote, '2022-03-02');

    await loginAsAdmin(page);
    await page.goto('/admin/transactions');
    await page.getByRole('button', { name: /^pending$/i }).click();

    const olderCard = page.locator('.transaction-card', { hasText: olderNote });
    const newerCard = page.locator('.transaction-card', { hasText: newerNote });
    // Both accumulate across repeated test runs (this test never resolves its own items), so
    // scroll to load enough pages for both to be present in the DOM before comparing order.
    await scrollUntilVisible(page, newerCard, { maxScrolls: 60 });
    await scrollUntilVisible(page, olderCard, { maxScrolls: 60 });

    const newerBox = await newerCard.boundingBox();
    const olderBox = await olderCard.boundingBox();
    expect(newerBox.y).toBeLessThan(olderBox.y);
  });
});
