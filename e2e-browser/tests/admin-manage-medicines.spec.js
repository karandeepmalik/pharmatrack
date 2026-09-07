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

  test.describe('edit medicine', () => {
    async function addTabletMedicine(page, name) {
      await page.locator('#med-pharma-select').selectOption({ index: 1 });
      await page.locator('#med-name-input').fill(name);
      await page.locator('#med-type-select').selectOption('TABLET');
      await page.locator('#med-spec-input').fill('100');
      await page.locator('#med-price-input').fill('500');
      await page.getByRole('button', { name: /^add medicine$/i }).click();
      await expect(page.getByRole('alert')).toContainText(/created successfully/i, { timeout: 10000 });
      return page.getByRole('row', { name: new RegExp(name) });
    }

    test('editing name and price persists after save', async ({ page }) => {
      const name = `Playwright Edit Tablet ${Date.now()}`;
      const row = await addTabletMedicine(page, name);

      await row.getByRole('button', { name: /^edit$/i }).click();
      // Once the name field's value changes, `row` (matched by the original name) can no longer
      // be re-resolved — locate the currently-editing row by a stable marker (the edit input's
      // own presence) instead of by text that's about to change.
      const editingRow = page.locator('tbody tr').filter({ has: page.getByLabel(/edit medicine name/i) });
      const updatedName = `${name} (Updated)`;
      await editingRow.getByLabel(/edit medicine name/i).fill(updatedName);
      await editingRow.getByLabel(/edit price/i).fill('750');
      await editingRow.getByRole('button', { name: /^save$/i }).click();

      const updatedRow = page.getByRole('row', { name: new RegExp(updatedName.replace(/[()]/g, '\\$&')) });
      await expect(updatedRow).toBeVisible({ timeout: 10000 });
      await expect(updatedRow.getByRole('cell', { name: '750', exact: true })).toBeVisible();
    });

    test('changing type to VIAL reveals the concentration field while editing', async ({ page }) => {
      const name = `Playwright Edit Type ${Date.now()}`;
      const row = await addTabletMedicine(page, name);

      await row.getByRole('button', { name: /^edit$/i }).click();
      await expect(row.getByLabel(/edit concentration/i)).not.toBeVisible();

      await row.getByLabel(/edit medicine type/i).selectOption('VIAL');
      await expect(row.getByLabel(/edit concentration/i)).toBeVisible();
      await row.getByLabel(/edit concentration/i).fill('20');
      await row.getByRole('button', { name: /^save$/i }).click();

      await expect(row.getByRole('cell', { name: 'VIAL', exact: true })).toBeVisible({ timeout: 10000 });
      await expect(row.getByRole('cell', { name: '20', exact: true })).toBeVisible();
    });

    test('cancelling an edit discards changes', async ({ page }) => {
      const name = `Playwright Edit Cancel ${Date.now()}`;
      const row = await addTabletMedicine(page, name);

      await row.getByRole('button', { name: /^edit$/i }).click();
      // Once the name field's value changes, `row` (matched by the original name) can no longer
      // be re-resolved — locate the currently-editing row by a stable marker (the edit input's
      // own presence) instead of by text that's about to change.
      const editingRow = page.locator('tbody tr').filter({ has: page.getByLabel(/edit medicine name/i) });
      await editingRow.getByLabel(/edit medicine name/i).fill('Should not be saved');
      await editingRow.getByRole('button', { name: /^cancel$/i }).click();

      await expect(page.getByLabel(/edit medicine name/i)).not.toBeVisible();
      await expect(page.getByText(name, { exact: true })).toBeVisible();
    });

    test('reassigning to a different pharma company persists after save', async ({ page }) => {
      const otherCompanyName = `Playwright Other Pharma ${Date.now()}`;
      await page.locator('#company-name-input').fill(otherCompanyName);
      await page.getByRole('button', { name: /add pharma company/i }).click();
      await expect(page.getByRole('alert')).toContainText(/created successfully/i, { timeout: 10000 });

      const name = `Playwright Edit Pharma ${Date.now()}`;
      const row = await addTabletMedicine(page, name);

      await row.getByRole('button', { name: /^edit$/i }).click();
      await row.getByLabel(/edit pharma company/i).selectOption({ label: otherCompanyName });
      await row.getByRole('button', { name: /^save$/i }).click();

      await expect(row.getByRole('cell', { name: otherCompanyName, exact: true })).toBeVisible({ timeout: 10000 });
    });
  });
});
