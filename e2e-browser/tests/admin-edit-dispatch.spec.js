const { test, expect } = require('@playwright/test');
const { loginAsAdmin, loginAsUser, PAYMENT_SCREENSHOT } = require('./helpers');

// This page ("Modify or Delete a Medicine Dispatch Record", /admin/dispatch-records) is exactly
// the page that regressed when /transactions/history's response shape changed from a bare array
// to a PagedResponse — the page silently rendered nothing after Search. Unit tests alone didn't
// catch it because they mock the API layer; this suite renders the real page against the real
// backend so a future contract mismatch fails here instead of shipping silently.

test.describe('Modify or Delete a Medicine Dispatch Record', () => {
  test('search over a wide date range returns and renders real dispatch records', async ({ page }) => {
    // Seed at least one real transaction so the search has something to find, regardless
    // of what other tests/demo data already exist.
    await loginAsUser(page, 'john');
    await page.goto('/user/submit');
    await expect(page.getByRole('heading', { name: /submit medicine dispatch/i })).toBeVisible();

    // Fill the cascading selects: stock type -> pharma -> type -> spec -> quantity -> notes -> screenshot
    await page.locator('#pharma-select').selectOption({ index: 1 });
    await page.locator('#type-select').selectOption({ index: 1 });
    await page.locator('#spec-select').selectOption({ index: 1 });
    const qtyInput = page.locator('#quantity-input');
    await qtyInput.fill('0.1');
    await page.locator('#notes-input').fill(`Playwright e2e dispatch note ${Date.now()}`);
    await page.locator('#screenshot-input').setInputFiles(PAYMENT_SCREENSHOT);
    const submitBtn = page.getByRole('button', { name: /submit medicine dispatch/i });
    await expect(submitBtn).toBeEnabled();
    await submitBtn.click();
    await expect(page.getByRole('alert')).toContainText(/submitted successfully/i, { timeout: 10000 });

    // Now, as admin, search the dispatch-records page over a wide range and confirm real
    // rows render (this is the exact flow that silently broke in the regression).
    await loginAsAdmin(page);
    await page.goto('/admin/dispatch-records');
    await expect(page.getByRole('heading', { name: /modify or delete a medicine dispatch record/i })).toBeVisible();

    const fromInput = page.locator('#from-date');
    await fromInput.fill('2020-01-01');
    await page.getByRole('button', { name: /^search$/i }).click();

    // The regression manifested as neither the table nor the empty-state message ever
    // appearing — assert the table renders with at least one data row (header + >=1 row).
    await expect(page.getByRole('table')).toBeVisible({ timeout: 10000 });
    const rowCount = await page.getByRole('row').count();
    expect(rowCount).toBeGreaterThan(1);
  });

  test('shows the empty state (not a blank page) for a date range with no records', async ({ page }) => {
    await loginAsAdmin(page);
    await page.goto('/admin/dispatch-records');

    await page.locator('#from-date').fill('2000-01-01');
    await page.locator('#to-date').fill('2000-01-02');
    await page.getByRole('button', { name: /^search$/i }).click();

    await expect(page.getByText(/no dispatch records found/i)).toBeVisible({ timeout: 10000 });
    await expect(page.getByRole('table')).not.toBeVisible();
  });

  test('a From date after the To date shows a validation error and disables Search', async ({ page }) => {
    await loginAsAdmin(page);
    await page.goto('/admin/dispatch-records');

    await page.locator('#from-date').fill('2026-01-10');
    await page.locator('#to-date').fill('2026-01-01');

    await expect(page.getByText(/"from" date must be before or equal to "to" date/i)).toBeVisible();
    await expect(page.getByRole('button', { name: /^search$/i })).toBeDisabled();
  });

  test('editing a dispatch record note persists after save', async ({ page }) => {
    await loginAsAdmin(page);
    await page.goto('/admin/dispatch-records');
    await page.locator('#from-date').fill('2020-01-01');
    await page.getByRole('button', { name: /^search$/i }).click();
    await expect(page.getByRole('table')).toBeVisible({ timeout: 10000 });

    const firstRow = page.getByRole('row').nth(1);
    await firstRow.getByRole('button', { name: /edit notes/i }).click();
    const newNote = `Edited by Playwright ${Date.now()}`;
    const textarea = firstRow.getByRole('textbox', { name: /edit notes/i });
    await textarea.fill(newNote);
    await firstRow.getByRole('button', { name: /^save$/i }).click();

    await expect(firstRow.getByText(newNote)).toBeVisible({ timeout: 10000 });
  });

  test('clicking Cancel on an in-progress edit discards the change', async ({ page }) => {
    await loginAsAdmin(page);
    await page.goto('/admin/dispatch-records');
    await page.locator('#from-date').fill('2020-01-01');
    await page.getByRole('button', { name: /^search$/i }).click();
    await expect(page.getByRole('table')).toBeVisible({ timeout: 10000 });

    const firstRow = page.getByRole('row').nth(1);
    const originalNote = await firstRow.locator('td').nth(5).innerText();
    await firstRow.getByRole('button', { name: /edit notes/i }).click();
    await firstRow.getByRole('textbox', { name: /edit notes/i }).fill('This should not be saved');
    await firstRow.getByRole('button', { name: /^cancel$/i }).click();

    await expect(firstRow.getByRole('textbox', { name: /edit notes/i })).not.toBeVisible();
    await expect(firstRow.getByText(originalNote, { exact: true })).toBeVisible();
  });

  test.describe('delete flow', () => {
    async function submitAndFindRow(page, note) {
      await loginAsUser(page, 'john');
      await page.goto('/user/submit');
      await page.locator('#pharma-select').selectOption({ index: 1 });
      await page.locator('#type-select').selectOption({ index: 1 });
      await page.locator('#spec-select').selectOption({ index: 1 });
      await page.locator('#quantity-input').fill('0.1');
      await page.locator('#notes-input').fill(note);
      await page.locator('#screenshot-input').setInputFiles(PAYMENT_SCREENSHOT);
      await page.getByRole('button', { name: /submit medicine dispatch/i }).click();
      await expect(page.getByRole('alert')).toContainText(/submitted successfully/i, { timeout: 10000 });

      await loginAsAdmin(page);
      await page.goto('/admin/dispatch-records');
      await page.locator('#from-date').fill(new Date().toISOString().slice(0, 10));
      await page.getByRole('button', { name: /^search$/i }).click();
      await expect(page.getByText(note)).toBeVisible({ timeout: 10000 });
      return page.getByRole('row', { name: new RegExp(note) });
    }

    test('clicking Delete shows an inline confirmation', async ({ page }) => {
      const note = `Dispatch delete-confirm ${Date.now()}`;
      const row = await submitAndFindRow(page, note);

      await row.getByRole('button', { name: /^delete$/i }).click();

      await expect(row.getByText(/are you sure/i)).toBeVisible();
      await expect(row.getByRole('button', { name: /confirm delete/i })).toBeVisible();
      await expect(row.getByRole('button', { name: /^cancel$/i })).toBeVisible();
    });

    test('clicking Cancel dismisses the confirmation without deleting', async ({ page }) => {
      const note = `Dispatch delete-cancel ${Date.now()}`;
      const row = await submitAndFindRow(page, note);

      await row.getByRole('button', { name: /^delete$/i }).click();
      await row.getByRole('button', { name: /^cancel$/i }).click();

      await expect(row.getByRole('button', { name: /^delete$/i })).toBeVisible();
      await expect(page.getByText(note)).toBeVisible();
    });

    test('confirming Delete removes the dispatch record from the list', async ({ page }) => {
      const note = `Dispatch delete-confirmed ${Date.now()}`;
      const row = await submitAndFindRow(page, note);

      await row.getByRole('button', { name: /^delete$/i }).click();
      await row.getByRole('button', { name: /confirm delete/i }).click();

      await expect(page.getByText(note)).not.toBeVisible({ timeout: 10000 });
    });
  });
});
