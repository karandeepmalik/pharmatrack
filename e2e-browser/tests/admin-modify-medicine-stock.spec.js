const { test, expect } = require('@playwright/test');
const { loginAsAdmin } = require('./helpers');

test.describe('Modify Medicine Stock', () => {
  test.beforeEach(async ({ page }) => {
    await loginAsAdmin(page);
    await page.goto('/admin/modify-medicine-stock');
  });

  test('shows the page heading and required form fields', async ({ page }) => {
    await expect(page.getByRole('heading', { name: /modify medicine stock/i })).toBeVisible();
    await expect(page.locator('#user-select')).toBeVisible();
    await expect(page.locator('#medicine-select')).toBeVisible();
    await expect(page.locator('#type-select')).toBeVisible();
    await expect(page.locator('#qty-input')).toBeVisible();
    await expect(page.locator('#note-input')).toBeVisible();
  });

  test('selecting a user and medicine reveals the current-quantity badge', async ({ page }) => {
    await page.locator('#user-select').selectOption({ index: 1 });
    await page.locator('#medicine-select').selectOption({ index: 1 });
    await expect(page.getByText(/current .*quantity for user/i)).toBeVisible();
  });

  test('adding medicine stock with a note succeeds and shows a success message', async ({ page }) => {
    await page.locator('#user-select').selectOption({ index: 1 });
    await page.locator('#medicine-select').selectOption({ index: 1 });
    await page.locator('#type-select').selectOption('ADD');
    await page.locator('#qty-input').fill('1');
    await page.locator('#note-input').fill(`Playwright ADD test note ${Date.now()}`);
    await page.getByRole('button', { name: /add medicine stock/i }).click();

    await expect(page.getByRole('alert')).toContainText(/added successfully/i, { timeout: 10000 });
  });

  test('submitting without a note is rejected client-side (submit button stays disabled)', async ({ page }) => {
    await page.locator('#user-select').selectOption({ index: 1 });
    await page.locator('#medicine-select').selectOption({ index: 1 });
    await page.locator('#qty-input').fill('1');
    // Leave note empty
    const submitBtn = page.getByRole('button', { name: /add medicine stock|reduce medicine stock/i });
    await expect(submitBtn).toBeDisabled();
  });
});
