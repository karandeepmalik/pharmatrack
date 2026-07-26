const { test, expect } = require('@playwright/test');
const { loginAsUser, PAYMENT_SCREENSHOT } = require('./helpers');

test.describe('Submit Medicine Dispatch', () => {
  test.beforeEach(async ({ page }) => {
    await loginAsUser(page, 'john');
    await page.goto('/user/submit');
  });

  test('shows the page heading and cascading form fields', async ({ page }) => {
    await expect(page.getByRole('heading', { name: /submit medicine dispatch/i })).toBeVisible();
    await expect(page.locator('#medicine-stock-type-select')).toBeVisible();
    await expect(page.locator('#pharma-select')).toBeVisible();
  });

  test('submit button stays disabled until the required fields and a screenshot are provided', async ({ page }) => {
    const submitBtn = page.getByRole('button', { name: /submit medicine dispatch/i });
    await expect(submitBtn).toBeDisabled();

    await page.locator('#pharma-select').selectOption({ index: 1 });
    await page.locator('#type-select').selectOption({ index: 1 });
    await page.locator('#spec-select').selectOption({ index: 1 });
    await page.locator('#quantity-input').fill('0.1');
    await page.locator('#notes-input').fill(`No screenshot yet ${Date.now()}`);
    await expect(submitBtn).toBeDisabled(); // still no screenshot

    await page.locator('#screenshot-input').setInputFiles(PAYMENT_SCREENSHOT);
    await expect(submitBtn).toBeEnabled();
  });

  test('submitting a valid dispatch shows a pending-approval success message', async ({ page }) => {
    await page.locator('#pharma-select').selectOption({ index: 1 });
    await page.locator('#type-select').selectOption({ index: 1 });
    await page.locator('#spec-select').selectOption({ index: 1 });
    await page.locator('#quantity-input').fill('0.1');
    await page.locator('#notes-input').fill(`Playwright full submit flow ${Date.now()}`);
    await page.locator('#screenshot-input').setInputFiles(PAYMENT_SCREENSHOT);
    await page.getByRole('button', { name: /submit medicine dispatch/i }).click();

    await expect(page.getByRole('alert')).toContainText(/pending admin approval/i, { timeout: 10000 });
  });

  test('a quantity greater than the available stock keeps the submit button disabled', async ({ page }) => {
    await page.locator('#pharma-select').selectOption({ index: 1 });
    await page.locator('#type-select').selectOption({ index: 1 });
    await page.locator('#spec-select').selectOption({ index: 1 });
    const qtyInput = page.locator('#quantity-input');
    const max = await qtyInput.getAttribute('max');
    await qtyInput.fill(String(Number(max) + 1000));
    await page.locator('#notes-input').fill(`Over-limit attempt ${Date.now()}`);
    await page.locator('#screenshot-input').setInputFiles(PAYMENT_SCREENSHOT);

    await expect(page.getByRole('button', { name: /submit medicine dispatch/i })).toBeDisabled();
  });

  test('uploading a screenshot shows it attached with a remove option', async ({ page }) => {
    await page.locator('#screenshot-input').setInputFiles(PAYMENT_SCREENSHOT);
    await expect(page.getByText(/1 screenshot attached/i)).toBeVisible();
    await expect(page.getByRole('button', { name: /remove screenshot 1/i })).toBeVisible();
  });
});
