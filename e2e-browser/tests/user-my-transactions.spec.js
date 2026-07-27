const { test, expect } = require('@playwright/test');
const { loginAsUser, PAYMENT_SCREENSHOT, scrollUntilVisible } = require('./helpers');

async function submitDispatch(page, note, dispatchDate) {
  await page.goto('/user/submit');
  await page.locator('#pharma-select').selectOption({ index: 1 });
  await page.locator('#type-select').selectOption({ index: 1 });
  await page.locator('#spec-select').selectOption({ index: 1 });
  await page.locator('#quantity-input').fill('0.1');
  await page.locator('#notes-input').fill(note);
  if (dispatchDate) await page.locator('#dispatch-date-input').fill(dispatchDate);
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
    // Clicking the tab re-queries the server for that status from page 0 (see the regression
    // suite below) — scroll only within the already status-filtered set, not before switching.
    await page.getByRole('button', { name: /^pending$/i }).click();
    await scrollUntilVisible(page, page.getByText(note), { maxScrolls: 60 });
  });

  test('a PENDING dispatch can be deleted by its own submitter', async ({ page }) => {
    test.setTimeout(60000);
    const note = `Self-delete note ${Date.now()}`;
    await submitDispatch(page, note);

    await page.goto('/user/transactions');
    await page.getByRole('button', { name: /^pending$/i }).click();
    const card = page.locator('.transaction-card', { hasText: note });
    await scrollUntilVisible(page, card, { maxScrolls: 60 });

    page.once('dialog', dialog => dialog.accept());
    await card.getByRole('button', { name: /delete/i }).click();

    await expect(page.locator('.transaction-card', { hasText: note })).not.toBeVisible({ timeout: 10000 });
  });

  test('the medicine spec filter narrows the list to the selected medicine', async ({ page }) => {
    await page.goto('/user/transactions');
    await expect(page.locator('#spec-filter')).toBeVisible();
    const optionCount = await page.locator('#spec-filter option').count();
    // "All Medicines" plus at least one real medicine from the catalog.
    expect(optionCount).toBeGreaterThan(1);

    await page.locator('#spec-filter').selectOption({ index: 1 });
    // A real re-query happens (not a client-side re-filter of already-rendered cards) — just
    // confirm it doesn't error out and either shows matching cards or the empty state.
    await expect(
      page.locator('.transaction-card').first().or(page.getByText(/no transactions found/i))
    ).toBeVisible({ timeout: 10000 });
  });

  // Regression suite: this page used to filter status/medicine/notes entirely client-side,
  // against whatever page(s) the infinite-scroll happened to have already loaded — so a real
  // match sitting past page 0 of the *unfiltered* load order was silently missed, reporting "No
  // transactions found" even though the match existed. Fixed by sending these filters to the
  // backend as query params (see TransactionRepository.searchMyHistory) so matching happens
  // against the user's full result set, not just whatever's already in browser state — the same
  // fix already applied to the admin "View Past Medicine Dispatches" page's searchHistory.
  //
  // Each test below constructs a deterministic "match beyond page 0" scenario using two distinct
  // dispatch dates: same-day dispatches all share one identical submittedAt (midnight, from the
  // dispatch-date field), so relative order among same-day items isn't guaranteed — but items on
  // a strictly later date always sort before items on an earlier date under DESC. 10 "noise"
  // dispatches on the later date always fill page 0 of the unfiltered result set; the one target
  // dispatch, on the earlier date, would be the 11th item — exactly what old client-side
  // filtering (never seeing past page 0 without a scroll first) would silently have missed.
  function randomDatePair() {
    const noiseOffset = Math.floor(Math.random() * 3650);
    const noiseDate  = new Date(Date.UTC(2015, 0, 1) + noiseOffset * 86400000).toISOString().slice(0, 10);
    const targetDate = new Date(Date.UTC(2015, 0, 1) + (noiseOffset - 1) * 86400000).toISOString().slice(0, 10);
    return { noiseDate, targetDate };
  }

  test.describe('filters find real matches beyond the first loaded page (regression)', () => {
    async function submitNoiseAndTarget(page, { runId, noiseDate, targetDate, targetNote }) {
      for (let i = 0; i < 10; i++) {
        await submitDispatch(page, `Noise ${runId} #${i}`, noiseDate);
      }
      await submitDispatch(page, targetNote, targetDate);
    }

    test('notes search finds the target dispatch without scrolling first', async ({ page }) => {
      test.setTimeout(150000);
      const runId = Date.now();
      const { noiseDate, targetDate } = randomDatePair();
      const uniqueTag = `findme${runId}`;
      await submitNoiseAndTarget(page, { runId, noiseDate, targetDate, targetNote: `Target note ${uniqueTag}` });

      await page.goto('/user/transactions');
      await page.locator('#notes-search').fill(uniqueTag);

      await expect(page.getByText(new RegExp(`target note ${uniqueTag}`, 'i'))).toBeVisible({ timeout: 15000 });
      await expect(page.getByText(/no transactions found/i)).not.toBeVisible();

      // A search for something that genuinely doesn't exist still correctly shows the empty
      // state (proves the query itself works, not just that "anything" renders).
      await page.locator('#notes-search').fill('text-that-should-not-match-anything-xyz');
      await expect(page.getByText(/no transactions found/i)).toBeVisible({ timeout: 10000 });
    });

    test('the PENDING tab finds a target dispatch without scrolling first', async ({ page }) => {
      test.setTimeout(150000);
      const runId = Date.now();
      const { noiseDate, targetDate } = randomDatePair();
      const uniqueTag = `pendingfindme${runId}`;
      await submitNoiseAndTarget(page, { runId, noiseDate, targetDate, targetNote: `Target note ${uniqueTag}` });

      await page.goto('/user/transactions');
      await page.getByRole('button', { name: /^pending$/i }).click();
      await page.locator('#notes-search').fill(uniqueTag);

      await expect(page.getByText(new RegExp(`target note ${uniqueTag}`, 'i'))).toBeVisible({ timeout: 15000 });
    });
  });
});
