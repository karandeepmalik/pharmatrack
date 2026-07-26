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
});
