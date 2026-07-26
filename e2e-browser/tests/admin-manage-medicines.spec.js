const { test, expect } = require('@playwright/test');
const { loginAsAdmin } = require('./helpers');

test.describe('Manage Medicines', () => {
  test.beforeEach(async ({ page }) => {
    await loginAsAdmin(page);
    await page.goto('/admin/medicines');
  });

  test('shows the page heading and existing medicines table', async ({ page }) => {
    await expect(page.getByRole('heading', { name: /manage medicines/i })).toBeVisible();
    await expect(page.getByRole('heading', { name: /existing medicines/i })).toBeVisible();
  });

  test('adding a new pharma company shows it as an option in the medicine form', async ({ page }) => {
    const companyName = `Playwright Pharma ${Date.now()}`;
    await page.locator('#company-name-input').fill(companyName);
    await page.getByRole('button', { name: /add pharma company/i }).click();

    await expect(page.getByRole('alert')).toContainText(/created successfully/i, { timeout: 10000 });
    await expect(page.locator('#med-pharma-select').getByRole('option', { name: companyName })).toBeAttached();
  });

  test('adding a new tablet medicine shows it in the existing medicines table', async ({ page }) => {
    const companyName = `Playwright Pharma ${Date.now()}`;
    await page.locator('#company-name-input').fill(companyName);
    await page.getByRole('button', { name: /add pharma company/i }).click();
    await expect(page.getByRole('alert')).toContainText(/created successfully/i, { timeout: 10000 });

    const medName = `Playwright Tablet ${Date.now()}`;
    await page.locator('#med-pharma-select').selectOption({ label: companyName });
    await page.locator('#med-name-input').fill(medName);
    await page.locator('#med-type-select').selectOption('TABLET');
    await page.locator('#med-spec-input').fill('250');
    await page.locator('#med-price-input').fill('99');
    await page.getByRole('button', { name: /add medicine/i }).click();

    await expect(page.getByRole('alert')).toContainText(/created successfully/i, { timeout: 10000 });
    await expect(page.getByRole('cell', { name: medName, exact: true })).toBeVisible();
  });

  test('selecting VIAL type reveals the concentration field', async ({ page }) => {
    await expect(page.locator('#med-conc-input')).not.toBeVisible();
    await page.locator('#med-type-select').selectOption('VIAL');
    await expect(page.locator('#med-conc-input')).toBeVisible();
  });
});
