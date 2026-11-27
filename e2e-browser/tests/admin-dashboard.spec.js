const { test, expect } = require('@playwright/test');
const { loginAsAdmin } = require('./helpers');

test.beforeEach(async ({ page }) => {
  await loginAsAdmin(page);
});

test.describe('Admin Dashboard', () => {
  test('renders nav cards in the requested sequence', async ({ page }) => {
    const links = page.locator('.nav-card');
    await expect(links).toHaveCount(10);

    const hrefs = await links.evaluateAll(els => els.map(el => el.getAttribute('href')));
    expect(hrefs).toEqual([
      '/admin/transactions',
      '/admin/reports',
      '/admin/past-transactions',
      '/admin/dispatch-records',
      '/admin/modify-medicine-stock',
      '/admin/add-user-dispatch',
      '/admin/medicine-stock-adjustments',
      '/admin/users',
      '/admin/medicine-stock',
      '/admin/medicines',
    ]);
  });

  test('clicking Review Adjustments navigates to the approval queue', async ({ page }) => {
    await page.getByRole('link', { name: /review adjustments/i }).click();
    await expect(page).toHaveURL(/\/admin\/transactions/);
    await expect(page.getByRole('heading', { name: /review adjustments/i })).toBeVisible();
  });

  test('clicking Add User Dispatches navigates to the admin on-behalf-of submission page', async ({ page }) => {
    await page.getByRole('link', { name: /add user dispatches/i }).click();
    await expect(page).toHaveURL(/\/admin\/add-user-dispatch/);
    await expect(page.getByRole('heading', { name: /add user dispatches/i })).toBeVisible();
  });

  test('clicking View Past Medicine Dispatches navigates to the history browser', async ({ page }) => {
    await page.getByRole('link', { name: /view past medicine dispatches/i }).click();
    await expect(page).toHaveURL(/\/admin\/past-transactions/);
    await expect(page.getByRole('heading', { name: /view past medicine dispatches/i })).toBeVisible();
  });

  test('clicking Modify or Delete a Medicine Dispatch Record navigates correctly', async ({ page }) => {
    await page.getByRole('link', { name: /modify or delete a medicine dispatch record/i }).click();
    await expect(page).toHaveURL(/\/admin\/dispatch-records/);
    await expect(page.getByRole('heading', { name: /modify or delete a medicine dispatch record/i })).toBeVisible();
  });
});
