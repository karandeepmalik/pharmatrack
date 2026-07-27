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

  test('adding a new VIAL medicine with a concentration shows it in the existing medicines table', async ({ page }) => {
    const companyName = `Playwright Pharma ${Date.now()}`;
    await page.locator('#company-name-input').fill(companyName);
    await page.getByRole('button', { name: /add pharma company/i }).click();
    await expect(page.getByRole('alert')).toContainText(/created successfully/i, { timeout: 10000 });

    const medName = `Playwright Vial ${Date.now()}`;
    await page.locator('#med-pharma-select').selectOption({ label: companyName });
    await page.locator('#med-name-input').fill(medName);
    await page.locator('#med-type-select').selectOption('VIAL');
    await page.locator('#med-spec-input').fill('10');
    await page.locator('#med-conc-input').fill('20');
    await page.locator('#med-price-input').fill('4000');
    await page.getByRole('button', { name: /add medicine/i }).click();

    await expect(page.getByRole('alert')).toContainText(/created successfully/i, { timeout: 10000 });
    const row = page.getByRole('row', { name: new RegExp(medName) });
    await expect(row).toBeVisible();
    await expect(row.getByRole('cell', { name: '20', exact: true })).toBeVisible();
  });

  test('adding a pharma company with a duplicate name shows an error', async ({ page }) => {
    const companyName = `Playwright Dup Pharma ${Date.now()}`;
    await page.locator('#company-name-input').fill(companyName);
    await page.getByRole('button', { name: /add pharma company/i }).click();
    await expect(page.getByRole('alert')).toContainText(/created successfully/i, { timeout: 10000 });

    await page.locator('#company-name-input').fill(companyName);
    await page.getByRole('button', { name: /add pharma company/i }).click();

    await expect(page.getByRole('alert')).toBeVisible({ timeout: 10000 });
    await expect(page.getByRole('alert')).not.toContainText(/created successfully/i);
  });

  test('Add Medicine stays disabled until all required fields are filled', async ({ page }) => {
    const submitBtn = page.getByRole('button', { name: /^add medicine$/i });
    await expect(submitBtn).toBeDisabled();

    await page.locator('#med-pharma-select').selectOption({ index: 1 });
    await expect(submitBtn).toBeDisabled();
    await page.locator('#med-name-input').fill('Incomplete Medicine');
    await expect(submitBtn).toBeDisabled();
    await page.locator('#med-type-select').selectOption('TABLET');
    await expect(submitBtn).toBeDisabled();
    await page.locator('#med-spec-input').fill('100');
    await expect(submitBtn).toBeDisabled();
    await page.locator('#med-price-input').fill('50');
    await expect(submitBtn).toBeEnabled();
  });
});
