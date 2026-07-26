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

  test('the submit button label switches between Add and Reduce Medicine Stock', async ({ page }) => {
    await expect(page.getByRole('button', { name: /^add medicine stock$/i })).toBeVisible();
    await page.locator('#type-select').selectOption('REDUCE');
    await expect(page.getByRole('button', { name: /^reduce medicine stock$/i })).toBeVisible();
  });

  test('reducing medicine stock succeeds after first adding enough to reduce', async ({ page }) => {
    await page.locator('#user-select').selectOption({ index: 1 });
    await page.locator('#medicine-select').selectOption({ index: 1 });

    // Ensure there's enough to reduce: add first.
    await page.locator('#type-select').selectOption('ADD');
    await page.locator('#qty-input').fill('5');
    await page.locator('#note-input').fill(`Playwright pre-REDUCE ADD ${Date.now()}`);
    await page.getByRole('button', { name: /add medicine stock/i }).click();
    await expect(page.getByRole('alert')).toContainText(/added successfully/i, { timeout: 10000 });

    await page.locator('#type-select').selectOption('REDUCE');
    await page.locator('#qty-input').fill('1');
    await page.locator('#note-input').fill(`Playwright REDUCE test note ${Date.now()}`);
    await page.getByRole('button', { name: /reduce medicine stock/i }).click();

    await expect(page.getByRole('alert')).toContainText(/reduced successfully/i, { timeout: 10000 });
  });

  test('the quantity field caps its max at the current available quantity when reducing', async ({ page }) => {
    await page.locator('#user-select').selectOption({ index: 1 });
    await page.locator('#medicine-select').selectOption({ index: 1 });

    const badgeText = await page.getByText(/current .*quantity for user/i).innerText();
    const currentQty = badgeText.match(/([\d.]+)\s*units/i)?.[1];

    await page.locator('#type-select').selectOption('REDUCE');

    if (currentQty != null) {
      const max = await page.locator('#qty-input').getAttribute('max');
      expect(Number(max)).toBeCloseTo(Number(currentQty), 5);
    }
  });

  test('checking In Transit reveals the Days in transit field, defaulting to 2', async ({ page }) => {
    await expect(page.locator('#transit-days-input')).not.toBeVisible();
    await page.locator('#in-transit-checkbox').check();
    await expect(page.locator('#transit-days-input')).toBeVisible();
    await expect(page.locator('#transit-days-input')).toHaveValue('2');
  });

  test('unchecking In Transit hides the Days in transit field again', async ({ page }) => {
    await page.locator('#in-transit-checkbox').check();
    await expect(page.locator('#transit-days-input')).toBeVisible();
    await page.locator('#in-transit-checkbox').uncheck();
    await expect(page.locator('#transit-days-input')).not.toBeVisible();
  });

  test('submitting an ADD with In Transit checked and a custom transit-days value succeeds', async ({ page }) => {
    await page.locator('#user-select').selectOption({ index: 1 });
    await page.locator('#medicine-select').selectOption({ index: 1 });
    await page.locator('#type-select').selectOption('ADD');
    await page.locator('#qty-input').fill('2');
    await page.locator('#note-input').fill(`Playwright in-transit ADD ${Date.now()}`);
    await page.locator('#in-transit-checkbox').check();
    await page.locator('#transit-days-input').fill('5');
    await page.getByRole('button', { name: /add medicine stock/i }).click();

    await expect(page.getByRole('alert')).toContainText(/added successfully/i, { timeout: 10000 });
  });

  test('checking Internal Movement does not block a normal ADD submission', async ({ page }) => {
    await page.locator('#user-select').selectOption({ index: 1 });
    await page.locator('#medicine-select').selectOption({ index: 1 });
    await page.locator('#type-select').selectOption('ADD');
    await page.locator('#qty-input').fill('1');
    await page.locator('#note-input').fill(`Playwright internal-movement ADD ${Date.now()}`);
    await page.locator('#internal-movement-checkbox').check();
    await page.getByRole('button', { name: /add medicine stock/i }).click();

    await expect(page.getByRole('alert')).toContainText(/added successfully/i, { timeout: 10000 });
  });
});
