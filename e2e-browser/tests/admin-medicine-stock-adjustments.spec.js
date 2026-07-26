const { test, expect } = require('@playwright/test');
const { loginAsAdmin } = require('./helpers');

test.describe('Medicine Stock Modifications History', () => {
  test.beforeEach(async ({ page }) => {
    await loginAsAdmin(page);
    await page.goto('/admin/medicine-stock-adjustments');
  });

  test('shows the page heading and does not show results before Search', async ({ page }) => {
    await expect(page.getByRole('heading', { name: /medicine stock modifications history/i })).toBeVisible();
    await expect(page.getByRole('table')).not.toBeVisible();
  });

  test('searching over a wide date range renders results', async ({ page }) => {
    await page.locator('#from-date').fill('2020-01-01');
    await page.getByRole('button', { name: /^search$/i }).click();

    await expect(
      page.getByText(/^results \(\d+\)$/i).or(page.getByText(/no stock modifications found/i))
    ).toBeVisible({ timeout: 10000 });
  });

  test('a freshly made stock adjustment appears in the history after searching', async ({ page }) => {
    // Create a real, freshly-timestamped adjustment via Modify Medicine Stock first.
    const note = `Playwright history note ${Date.now()}`;
    await page.goto('/admin/modify-medicine-stock');
    await page.locator('#user-select').selectOption({ index: 1 });
    await page.locator('#medicine-select').selectOption({ index: 1 });
    await page.locator('#type-select').selectOption('ADD');
    await page.locator('#qty-input').fill('1');
    await page.locator('#note-input').fill(note);
    await page.getByRole('button', { name: /add medicine stock/i }).click();
    await expect(page.getByRole('alert')).toContainText(/added successfully/i, { timeout: 10000 });

    await page.goto('/admin/medicine-stock-adjustments');
    await page.locator('#from-date').fill(new Date().toISOString().slice(0, 10));
    await page.getByRole('button', { name: /^search$/i }).click();
    await expect(page.getByText(note)).toBeVisible({ timeout: 10000 });
  });

  test('empty date range with no matching data shows the empty state, not a blank page', async ({ page }) => {
    await page.locator('#from-date').fill('2000-01-01');
    await page.locator('#to-date').fill('2000-01-02');
    await page.getByRole('button', { name: /^search$/i }).click();

    await expect(page.getByText(/no stock modifications found for the selected date range/i)).toBeVisible({ timeout: 10000 });
  });

  test.describe('delete flow', () => {
    async function createAdjustment(page, note) {
      await page.goto('/admin/modify-medicine-stock');
      await page.locator('#user-select').selectOption({ index: 1 });
      await page.locator('#medicine-select').selectOption({ index: 1 });
      await page.locator('#type-select').selectOption('ADD');
      await page.locator('#qty-input').fill('1');
      await page.locator('#note-input').fill(note);
      await page.getByRole('button', { name: /add medicine stock/i }).click();
      await expect(page.getByRole('alert')).toContainText(/added successfully/i, { timeout: 10000 });
    }

    async function findRow(page, note) {
      await page.goto('/admin/medicine-stock-adjustments');
      await page.locator('#from-date').fill(new Date().toISOString().slice(0, 10));
      await page.getByRole('button', { name: /^search$/i }).click();
      await expect(page.getByText(note)).toBeVisible({ timeout: 10000 });
      return page.getByRole('row', { name: new RegExp(note) });
    }

    test('clicking Delete shows an inline confirmation with a warning about reversal', async ({ page }) => {
      const note = `Delete-confirm note ${Date.now()}`;
      await createAdjustment(page, note);
      const row = await findRow(page, note);

      await row.getByRole('button', { name: /^delete$/i }).click();

      await expect(row.getByText(/this will reverse the medicinestock change/i)).toBeVisible();
      await expect(row.getByRole('button', { name: /confirm delete/i })).toBeVisible();
      await expect(row.getByRole('button', { name: /^cancel$/i })).toBeVisible();
    });

    test('clicking Cancel dismisses the confirmation without deleting', async ({ page }) => {
      const note = `Delete-cancel note ${Date.now()}`;
      await createAdjustment(page, note);
      const row = await findRow(page, note);

      await row.getByRole('button', { name: /^delete$/i }).click();
      await row.getByRole('button', { name: /^cancel$/i }).click();

      await expect(row.getByRole('button', { name: /^delete$/i })).toBeVisible();
      await expect(page.getByText(note)).toBeVisible();
    });

    test('confirming Delete removes the record from the list', async ({ page }) => {
      const note = `Delete-confirmed note ${Date.now()}`;
      await createAdjustment(page, note);
      const row = await findRow(page, note);

      await row.getByRole('button', { name: /^delete$/i }).click();
      await row.getByRole('button', { name: /confirm delete/i }).click();

      await expect(page.getByText(note)).not.toBeVisible({ timeout: 10000 });
    });
  });
});
