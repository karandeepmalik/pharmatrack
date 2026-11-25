const { test, expect } = require('@playwright/test');
const { loginAsAdmin, pickMultiSelectOption } = require('./helpers');

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
    await pickMultiSelectOption(page, 'user-filter-select', 'john.doe');
    await expect(page.getByRole('cell', { name: 'jane.smith', exact: true })).not.toBeVisible();
  });

  test('filtering by medicine narrows both stock tables to that medicine only', async ({ page }) => {
    await expect(page.getByRole('cell', { name: 'john.doe', exact: true }).first()).toBeVisible({ timeout: 10000 });

    await page.locator('#spec-filter-select-toggle').click();
    const panel = page.getByRole('listbox', { name: 'Filter by Medicine' });
    // First real option after the "All Medicines" checkbox.
    const chosenMedicine = await panel.getByRole('checkbox').nth(1).evaluate(el => el.parentElement.textContent.trim());
    await panel.getByRole('checkbox').nth(1).check();

    const rows = page.locator('table.data-table tbody tr');
    const rowCount = await rows.count();
    expect(rowCount).toBeGreaterThan(0);
    for (let i = 0; i < rowCount; i++) {
      await expect(rows.nth(i)).toContainText(chosenMedicine);
    }
  });

  test('combining medicine and user filters narrows to the intersection', async ({ page }) => {
    await expect(page.getByRole('cell', { name: 'john.doe', exact: true }).first()).toBeVisible({ timeout: 10000 });
    await page.locator('#spec-filter-select-toggle').click();
    await page.getByRole('listbox', { name: 'Filter by Medicine' }).getByRole('checkbox').nth(1).check();
    await pickMultiSelectOption(page, 'user-filter-select', 'john.doe');
    await expect(page.getByRole('cell', { name: 'jane.smith', exact: true })).not.toBeVisible();
  });
});
