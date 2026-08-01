const { test, expect } = require('@playwright/test');
const { loginAsAdmin, loginAsUser, PAYMENT_SCREENSHOT, scrollUntilVisible } = require('./helpers');

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

  test('a From date after the To date shows a validation error and disables Search', async ({ page }) => {
    await loginAsAdmin(page);
    await page.goto('/admin/past-transactions');
    await page.locator('#from-date').fill('2026-01-10');
    await page.locator('#to-date').fill('2026-01-01');

    await expect(page.getByText(/"from" date must be before or equal to "to" date/i)).toBeVisible();
    await expect(page.getByRole('button', { name: /^search$/i })).toBeDisabled();
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
    // The page defaults its Status filter to APPROVED, and a freshly reset DB (make down-v) has
    // no APPROVED transactions until something actually gets approved — earlier specs in this
    // run may only ever leave PENDING records. Submit-and-approve one here so the default-status
    // search below is guaranteed at least one match instead of depending on other spec files'
    // incidental run order (which is what made this test rely on Playwright's CI-only retry to
    // pass — it failed outright on a single-pass local run).
    const note = `Status-switch note ${Date.now()}`;
    await submitDispatch(page, note, new Date().toISOString().slice(0, 10));
    await loginAsAdmin(page);
    await page.goto('/admin/transactions');
    await page.getByRole('button', { name: /^pending$/i }).click();
    const card = page.locator('.transaction-card', { hasText: note });
    await scrollUntilVisible(page, card, { maxScrolls: 60 });
    await card.getByRole('button', { name: /approve/i }).click();
    await expect(page.locator('.transaction-card', { hasText: note })).not.toBeVisible({ timeout: 10000 });

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

    // "Results (N)" reflects the total matching count from the server (totalElements),
    // available immediately from page 0's response — not just what's been scroll-loaded so far
    // (only 10 of the 11 rows are actually rendered at this point).
    await expect(page.getByText(/^results \(11\)$/i)).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(/showing 10 of 11 matching dispatches/i)).toBeVisible();
    // Total Quantity also reflects the full matching set (11 dispatches x 0.1 each), available
    // immediately even though only 10 rows are rendered at this point.
    await expect(page.getByText(/total quantity: 1\.1/i)).toBeVisible();
    const rowsBeforeScroll = await page.getByRole('row').count();

    // Scroll the whole page to the bottom to bring the sentinel into view.
    await page.mouse.wheel(0, 20000);
    await page.waitForTimeout(1000);
    await page.mouse.wheel(0, 20000);

    await expect(page.getByText(/^results \(11\)$/i)).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(/all 11 transactions loaded\./i)).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(/showing \d+ of \d+ matching dispatches/i)).not.toBeVisible();
    const rowsAfterScroll = await page.getByRole('row').count();
    expect(rowsAfterScroll).toBeGreaterThan(rowsBeforeScroll);
  });

  // Regression suite: this page previously filtered User/Medicine/Notes entirely client-side,
  // against whatever page(s) happened to already be scroll-loaded — so a search over a date
  // range with more than one page of *unfiltered* results would silently report "no transactions
  // found" for a filter whose only match sat past page 0 of the raw (unfiltered) load order.
  // Fixed by sending these filters to the backend as query params (see
  // TransactionRepository.searchHistory) so matching happens against the full result set, not
  // just whatever's already in the browser's state.
  //
  // A second, independent bug was found and fixed in the same change: the notes filter's SQL
  // (LOWER(CONCAT('%', :notes, '%'))) 500'd on real Postgres specifically when :notes was null
  // (the common case — no notes filter active on most searches), because Postgres's JDBC driver
  // can't infer a type for a null argument to CONCAT and defaults it to bytea. Fixed by building
  // the full LIKE pattern in Java instead of at the SQL level. Neither bug reproduces against H2
  // (used in backend unit tests) — only real Postgres, which is what this suite runs against.
  //
  // Each test below constructs a deterministic "match beyond page 0" scenario using two distinct
  // dispatch dates rather than same-day ordering: same-day dispatches all share one identical
  // submittedAt (midnight, from the dispatch-date field), so relative order among same-day items
  // is not guaranteed — but items on a strictly later date always sort before items on an earlier
  // date under DESC. 10 "noise" dispatches on the later date always fill page 0 of the unfiltered
  // (ALL) result set; the one target dispatch, on the earlier date, would be the 11th item and
  // was exactly what old client-side filtering — never seeing past page 0 without a scroll —
  // would silently have missed.
  // Each test picks its own random pair of adjacent-ish dates so repeated runs (and runs of
  // different tests in this block) don't accumulate onto the same dates and break the "exactly
  // one match" assertions — the same reasoning as the scroll-pagination test above.
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

    test('notes filter finds the target dispatch without a 500 or a false empty state', async ({ page }) => {
      test.setTimeout(150000);
      const runId = Date.now();
      const { noiseDate, targetDate } = randomDatePair();
      const uniqueTag = `findme${runId}`;
      await submitNoiseAndTarget(page, { runId, noiseDate, targetDate, targetNote: `Target note ${uniqueTag}` });

      await loginAsAdmin(page);
      await page.goto('/admin/past-transactions');
      await page.locator('#from-date').fill(targetDate);
      await page.locator('#to-date').fill(noiseDate);
      await page.getByRole('combobox', { name: /^status$/i }).selectOption('ALL');
      await page.locator('#notes-search').fill(uniqueTag);
      await page.getByRole('button', { name: /^search$/i }).click();

      await expect(page.getByText(new RegExp(`target note ${uniqueTag}`, 'i'))).toBeVisible({ timeout: 15000 });
      await expect(page.getByText(/no transactions found/i)).not.toBeVisible();
      // Exactly one match: confirms the filter is scoping correctly, not just "not crashing".
      await expect(page.getByText(/^results \(1\)$/i)).toBeVisible();
    });

    test('user filter finds a match submitted by a different user than the noise dispatches', async ({ page }) => {
      test.setTimeout(150000);
      const runId = Date.now();
      const { noiseDate, targetDate } = randomDatePair();
      // Noise dispatches are submitted by john.doe (submitDispatch's default); the target is
      // submitted by jane.smith so filtering by jane.smith isolates exactly one match.
      for (let i = 0; i < 10; i++) {
        await submitDispatch(page, `Noise ${runId} #${i}`, noiseDate);
      }
      await loginAsUser(page, 'jane');
      await page.goto('/user/submit');
      await page.locator('#pharma-select').selectOption({ index: 1 });
      await page.locator('#type-select').selectOption({ index: 1 });
      await page.locator('#spec-select').selectOption({ index: 1 });
      await page.locator('#quantity-input').fill('0.1');
      await page.locator('#notes-input').fill(`Target by jane ${runId}`);
      await page.locator('#dispatch-date-input').fill(targetDate);
      await page.locator('#screenshot-input').setInputFiles(PAYMENT_SCREENSHOT);
      await page.getByRole('button', { name: /submit medicine dispatch/i }).click();
      await expect(page.getByRole('alert')).toContainText(/submitted successfully/i, { timeout: 10000 });

      await loginAsAdmin(page);
      await page.goto('/admin/past-transactions');
      await page.locator('#from-date').fill(targetDate);
      await page.locator('#to-date').fill(noiseDate);
      await page.getByRole('combobox', { name: /^status$/i }).selectOption('ALL');
      await page.getByRole('combobox', { name: /^user$/i }).selectOption('jane.smith');
      await page.getByRole('button', { name: /^search$/i }).click();

      await expect(page.getByText(new RegExp(`target by jane ${runId}`, 'i'))).toBeVisible({ timeout: 15000 });
      await expect(page.getByText(/no transactions found/i)).not.toBeVisible();
      await expect(page.getByText(/^results \(1\)$/i)).toBeVisible();
    });
  });

  test('changing a filter after a search hides the table until Search is pressed again', async ({ page }) => {
    await loginAsAdmin(page);
    await page.goto('/admin/past-transactions');
    await page.locator('#from-date').fill('2020-01-01');
    await page.getByRole('combobox', { name: /^status$/i }).selectOption('ALL');
    await page.getByRole('button', { name: /^search$/i }).click();
    await expect(page.getByText(/^results \(\d+\)$/i)).toBeVisible({ timeout: 10000 });

    await page.locator('#notes-search').fill('anything');
    await expect(page.getByRole('table')).not.toBeVisible();

    await page.getByRole('button', { name: /^search$/i }).click();
    await expect(
      page.getByText(/^results \(\d+\)$/i).or(page.getByText(/no transactions found/i))
    ).toBeVisible({ timeout: 10000 });
  });
});
