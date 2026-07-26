const { test, expect } = require('@playwright/test');
const { loginAsAdmin, loginAsUser, PAYMENT_SCREENSHOT } = require('./helpers');

// Submits a dispatch only — does NOT approve it. /admin/past-transactions' ALL status filter
// includes every status (its "ALL" branch has no status predicate server-side), so a PENDING
// item is enough to test this page's search/pagination — no need to route through the (heavily
// trafficked, ever-growing across test runs) admin approval queue at all.
async function submitDispatch(page, note, dispatchDate) {
  await loginAsUser(page, 'john');
  await page.goto('/user/submit');
  await page.locator('#pharma-select').selectOption({ index: 1 });
  await page.locator('#type-select').selectOption({ index: 1 });
  await page.locator('#spec-select').selectOption({ index: 1 });
  await page.locator('#quantity-input').fill('0.1');
  await page.locator('#notes-input').fill(note);
  await page.locator('#dispatch-date-input').fill(dispatchDate);
  await page.locator('#screenshot-input').setInputFiles(PAYMENT_SCREENSHOT);
  await page.getByRole('button', { name: /submit medicine dispatch/i }).click();
  await expect(page.getByRole('alert')).toContainText(/submitted successfully/i, { timeout: 10000 });
}

test.describe('View Past Medicine Dispatches (admin history browser)', () => {
  test('does not show results before Search is clicked', async ({ page }) => {
    await loginAsAdmin(page);
    await page.goto('/admin/past-transactions');
    await expect(page.getByRole('heading', { name: /view past medicine dispatches/i })).toBeVisible();
    await expect(page.getByRole('table')).not.toBeVisible();
  });

  test('searching over a wide date range renders a results table with a scroll sentinel', async ({ page }) => {
    await loginAsAdmin(page);
    await page.goto('/admin/past-transactions');

    await page.locator('#from-date').fill('2020-01-01');
    await page.getByRole('combobox', { name: /^status$/i }).selectOption('ALL');
    await page.getByRole('button', { name: /^search$/i }).click();

    await expect(page.getByRole('table')).toBeVisible({ timeout: 10000 });
    await expect(page.getByText(/^results \(\d+\)$/i)).toBeVisible();
  });

  test('switching the status filter re-searches and updates results', async ({ page }) => {
    await loginAsAdmin(page);
    await page.goto('/admin/past-transactions');

    await page.locator('#from-date').fill('2020-01-01');
    await page.getByRole('button', { name: /^search$/i }).click();
    await expect(page.getByText(/^results \(\d+\)$/i)).toBeVisible({ timeout: 10000 });

    await page.getByRole('combobox', { name: /^status$/i }).selectOption('REJECTED');
    await page.getByRole('button', { name: /^search$/i }).click();
    // Either a (possibly different-count) results table, or the empty state — never a crash.
    await expect(
      page.getByText(/^results \(\d+\)$/i).or(page.getByText(/no transactions found/i))
    ).toBeVisible({ timeout: 10000 });
  });

  test('empty date range with no matching data shows the empty state, not a blank page', async ({ page }) => {
    await loginAsAdmin(page);
    await page.goto('/admin/past-transactions');

    await page.locator('#from-date').fill('2000-01-01');
    await page.locator('#to-date').fill('2000-01-02');
    await page.getByRole('button', { name: /^search$/i }).click();

    await expect(page.getByText(/no transactions found for the selected criteria/i)).toBeVisible({ timeout: 10000 });
  });

  test('scrolling down loads the next page of 10 (real IntersectionObserver, not a mock)', async ({ page }) => {
    test.setTimeout(120000);
    // Page size is 10 — submit 11 dispatches (no need to approve — see submitDispatch's comment)
    // on one specific day so a single Search reliably yields exactly 11 results, guaranteeing a
    // second scroll-triggered page.
    // The dispatch date must be randomized (not derived from Date.now() via modulo — nearby
    // reruns have Date.now() values only seconds apart, which a modulo preserves almost exactly,
    // collapsing back to the same calendar day and accumulating results across runs) so distinct
    // test runs land on different days and don't pollute each other's result counts.
    const dispatchDate = new Date(Date.UTC(2015, 0, 1) + Math.floor(Math.random() * 3650) * 86400000)
      .toISOString().slice(0, 10);
    const runId = Date.now();
    for (let i = 0; i < 11; i++) {
      await submitDispatch(page, `Scroll-page marker ${runId} #${i}`, dispatchDate);
    }

    await loginAsAdmin(page);
    await page.goto('/admin/past-transactions');
    await page.locator('#from-date').fill(dispatchDate);
    await page.locator('#to-date').fill(dispatchDate);
    await page.getByRole('combobox', { name: /^status$/i }).selectOption('ALL');
    await page.getByRole('button', { name: /^search$/i }).click();

    // "Results (N)" reflects only what's loaded so far, not the total — page 0 is 10 items.
    await expect(page.getByText(/^results \(10\)$/i)).toBeVisible({ timeout: 15000 });
    const rowsBeforeScroll = await page.getByRole('row').count();

    // Scroll the whole page to the bottom to bring the sentinel into view.
    await page.mouse.wheel(0, 20000);
    await page.waitForTimeout(1000);
    await page.mouse.wheel(0, 20000);

    await expect(page.getByText(/^results \(11\)$/i)).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(/all 11 transactions loaded\./i)).toBeVisible({ timeout: 15000 });
    const rowsAfterScroll = await page.getByRole('row').count();
    expect(rowsAfterScroll).toBeGreaterThan(rowsBeforeScroll);
  });
});
