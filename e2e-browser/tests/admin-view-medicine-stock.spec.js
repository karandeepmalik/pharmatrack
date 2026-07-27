const { test, expect } = require('@playwright/test');
const { loginAsAdmin } = require('./helpers');

test.describe('View Available Medicine Stock', () => {
  test.beforeEach(async ({ page }) => {
    await loginAsAdmin(page);
    await page.goto('/admin/medicine-stock');
  });

  test('shows the page heading and both stock sections', async ({ page }) => {
    await expect(page.getByRole('heading', { name: /view available medicine stock/i })).toBeVisible();
    await expect(page.getByRole('heading', { name: /regular stock \(user allocations\)/i })).toBeVisible({ timeout: 10000 });
    await expect(page.getByRole('heading', { name: /admin stock \(system stock\)/i })).toBeVisible();
  });

  test('shows the seeded demo users in the regular stock table', async ({ page }) => {
    await expect(page.getByRole('cell', { name: 'john.doe', exact: true }).first()).toBeVisible({ timeout: 10000 });
  });

  test('filtering by user narrows the regular stock table', async ({ page }) => {
    await expect(page.getByRole('cell', { name: 'john.doe', exact: true }).first()).toBeVisible({ timeout: 10000 });
    await page.locator('#user-filter-select').selectOption('john.doe');
    await expect(page.getByRole('cell', { name: 'jane.smith', exact: true })).not.toBeVisible();
  });

  test('filtering by medicine narrows both stock tables to that medicine only', async ({ page }) => {
    await expect(page.getByRole('cell', { name: 'john.doe', exact: true }).first()).toBeVisible({ timeout: 10000 });

    const options = await page.locator('#spec-filter-select option').allTextContents();
    // First real option (index 1) after "All Medicines" (index 0).
    const chosenMedicine = options[1];
    await page.locator('#spec-filter-select').selectOption({ index: 1 });

    const rows = page.locator('table.data-table tbody tr');
    const rowCount = await rows.count();
    expect(rowCount).toBeGreaterThan(0);
    for (let i = 0; i < rowCount; i++) {
      await expect(rows.nth(i)).toContainText(chosenMedicine);
    }
  });

  test('combining medicine and user filters narrows to the intersection', async ({ page }) => {
    await expect(page.getByRole('cell', { name: 'john.doe', exact: true }).first()).toBeVisible({ timeout: 10000 });
    await page.locator('#spec-filter-select').selectOption({ index: 1 });
    await page.locator('#user-filter-select').selectOption('john.doe');
    await expect(page.getByRole('cell', { name: 'jane.smith', exact: true })).not.toBeVisible();
  });
});
