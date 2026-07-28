const { test, expect } = require('@playwright/test');
const { loginAsAdmin, loginAsUser, PAYMENT_SCREENSHOT, scrollUntilVisible } = require('./helpers');

async function submitAndApprove(page, who, note) {
  await loginAsUser(page, who);
  await page.goto('/user/submit');
  await page.locator('#pharma-select').selectOption({ index: 1 });
  await page.locator('#type-select').selectOption({ index: 1 });
  await page.locator('#spec-select').selectOption({ index: 1 });
  await page.locator('#quantity-input').fill('0.1');
  await page.locator('#notes-input').fill(note);
  await page.locator('#screenshot-input').setInputFiles(PAYMENT_SCREENSHOT);
  await page.getByRole('button', { name: /submit medicine dispatch/i }).click();
  await expect(page.getByRole('alert')).toContainText(/submitted successfully/i, { timeout: 10000 });

  await loginAsAdmin(page);
  await page.goto('/admin/transactions');
  await page.getByRole('button', { name: /^pending$/i }).click();
  const card = page.locator('.transaction-card', { hasText: note });
  await scrollUntilVisible(page, card, { maxScrolls: 60 });
  await card.getByRole('button', { name: /approve/i }).click();
  await expect(page.locator('.transaction-card', { hasText: note })).not.toBeVisible({ timeout: 10000 });
}

test.describe('View Reports', () => {
  test.beforeEach(async ({ page }) => {
    await loginAsAdmin(page);
    await page.goto('/admin/reports');
  });

  test('shows the page heading and report dropdown', async ({ page }) => {
    await expect(page.getByRole('heading', { name: /view reports/i })).toBeVisible();
    await expect(page.locator('#report-select')).toBeVisible();
  });

  test('Generate Report is disabled until a report type is chosen', async ({ page }) => {
    await expect(page.getByRole('button', { name: /generate report/i })).toBeDisabled();
    await page.locator('#report-select').selectOption('medicine-stock-by-user');
    await expect(page.getByRole('button', { name: /generate report/i })).toBeEnabled();
  });

  test('generates the Current Medicine Stock Per User report', async ({ page }) => {
    await page.locator('#report-select').selectOption('medicine-stock-by-user');
    await page.getByRole('button', { name: /generate report/i }).click();

    await expect(page.getByRole('heading', { name: /current medicine stock per user/i })).toBeVisible({ timeout: 10000 });
    await expect(page.locator('pre.report-content')).not.toBeEmpty();
  });

  test('generates the Medicine Stock Valuation report for a chosen date', async ({ page }) => {
    await page.locator('#report-select').selectOption('medicine-stock-valuation');
    await page.locator('#valuation-date-input').fill(new Date().toISOString().slice(0, 10));
    await page.getByRole('button', { name: /generate report/i }).click();

    await expect(page.getByRole('heading', { name: /medicine stock valuation/i })).toBeVisible({ timeout: 10000 });
    await expect(page.locator('pre.report-content')).not.toBeEmpty();
  });

  test('generates the Daily Report for a chosen date', async ({ page }) => {
    await page.locator('#report-select').selectOption('daily');
    await page.locator('#daily-date-input').fill(new Date().toISOString().slice(0, 10));
    await page.getByRole('button', { name: /generate report/i }).click();

    await expect(page.getByRole('heading', { name: /daily report/i })).toBeVisible({ timeout: 10000 });
    await expect(page.locator('pre.report-content')).not.toBeEmpty();
  });

  test('generates the Sales Report for a chosen date range', async ({ page }) => {
    await page.locator('#report-select').selectOption('today-sales');
    const today = new Date().toISOString().slice(0, 10);
    await page.locator('#sales-from-input').fill(today);
    await page.locator('#sales-to-input').fill(today);
    await page.getByRole('button', { name: /generate report/i }).click();

    await expect(page.getByRole('heading', { name: /^sales report$/i })).toBeVisible({ timeout: 10000 });
    await expect(page.locator('pre.report-content')).not.toBeEmpty();
  });

  test('shows User and Medicine Spec filter dropdowns for the Sales Report', async ({ page }) => {
    await page.locator('#report-select').selectOption('today-sales');
    await expect(page.getByLabel(/^user$/i)).toBeVisible();
    await expect(page.getByLabel(/medicine spec/i)).toBeVisible();
    await expect(page.getByLabel(/^user$/i)).toHaveValue('ALL');
    await expect(page.getByLabel(/medicine spec/i)).toHaveValue('ALL');
  });

  test('filtering the Sales Report by user narrows it to only that user\'s sales', async ({ page }) => {
    test.setTimeout(90000);
    const janeNote = `Sales-filter jane ${Date.now()}`;
    await submitAndApprove(page, 'jane', janeNote);
    const johnNote = `Sales-filter john ${Date.now()}`;
    await submitAndApprove(page, 'john', johnNote);

    await page.goto('/admin/reports');
    await page.locator('#report-select').selectOption('today-sales');
    const today = new Date().toISOString().slice(0, 10);
    await page.locator('#sales-from-input').fill(today);
    await page.locator('#sales-to-input').fill(today);
    await page.getByLabel(/^user$/i).selectOption('jane.smith');
    await page.getByRole('button', { name: /generate report/i }).click();

    await expect(page.locator('pre.report-content')).toContainText('Jane Smith', { timeout: 10000 });
    await expect(page.locator('pre.report-content')).not.toContainText('John Doe');
  });

  test('filtering the Sales Report by medicine spec excludes other medicines\' sales', async ({ page }) => {
    test.setTimeout(120000);
    // Derive the two dispatches' selections FROM the report page's own dropdown (rather than
    // assuming SubmitTransaction and this page's medicine catalogs are ordered the same way —
    // they're populated from different endpoints) so this test is robust regardless of seed
    // data ordering or accumulated Playwright test medicines from earlier runs.
    await page.locator('#report-select').selectOption('today-sales');
    const specSelect = page.getByLabel(/medicine spec/i);
    const label1 = await specSelect.locator('option').nth(1).innerText();
    const label2 = await specSelect.locator('option').nth(2).innerText();

    // Labels are "Vial N ml" or "Tablet N mg (10 Tablets)" — parse type + spec number back out
    // so SubmitTransaction's #type-select (VIAL/TABLET) and #spec-select (matching label) can be
    // driven directly from this page's own data.
    const parseLabel = (label) => {
      const vial = label.match(/^Vial (\d+) ml$/i);
      if (vial) return { type: 'VIAL', specLabel: `${vial[1]} ml` };
      const tablet = label.match(/^Tablet (\d+) mg/i);
      return { type: 'TABLET', specLabel: `${tablet[1]} mg (10 Tablets)` };
    };
    const target = parseLabel(label1);
    const other = parseLabel(label2);

    async function submitFor(typeAndSpec, note) {
      await loginAsUser(page, 'john');
      await page.goto('/user/submit');
      await page.locator('#pharma-select').selectOption({ index: 1 });
      await page.locator('#type-select').selectOption(typeAndSpec.type);
      await page.locator('#spec-select').selectOption({ label: typeAndSpec.specLabel });
      await page.locator('#quantity-input').fill('0.1');
      await page.locator('#notes-input').fill(note);
      await page.locator('#screenshot-input').setInputFiles(PAYMENT_SCREENSHOT);
      await page.getByRole('button', { name: /submit medicine dispatch/i }).click();
      await expect(page.getByRole('alert')).toContainText(/submitted successfully/i, { timeout: 10000 });

      await loginAsAdmin(page);
      await page.goto('/admin/transactions');
      await page.getByRole('button', { name: /^pending$/i }).click();
      const card = page.locator('.transaction-card', { hasText: note });
      await scrollUntilVisible(page, card, { maxScrolls: 60 });
      await card.getByRole('button', { name: /approve/i }).click();
      await expect(page.locator('.transaction-card', { hasText: note })).not.toBeVisible({ timeout: 10000 });
    }

    const targetNote = `Sales-filter-target ${Date.now()}`;
    const otherNote = `Sales-filter-other ${Date.now()}`;
    await submitFor(target, targetNote);
    await submitFor(other, otherNote);

    await page.goto('/admin/reports');
    await page.locator('#report-select').selectOption('today-sales');
    const today = new Date().toISOString().slice(0, 10);
    await page.locator('#sales-from-input').fill(today);
    await page.locator('#sales-to-input').fill(today);
    await page.getByLabel(/medicine spec/i).selectOption({ label: label1 });
    await page.getByRole('button', { name: /generate report/i }).click();

    await expect(page.locator('pre.report-content')).toContainText(targetNote, { timeout: 10000 });
    await expect(page.locator('pre.report-content')).not.toContainText(otherNote);
  });

  test('an invalid Sales Report date range (From after To) disables Generate Report and shows an error', async ({ page }) => {
    await page.locator('#report-select').selectOption('today-sales');
    await page.locator('#sales-from-input').fill('2026-01-10');
    await page.locator('#sales-to-input').fill('2026-01-01');

    await expect(page.getByText(/"from" date must be before or equal to "to" date/i)).toBeVisible();
    await expect(page.getByRole('button', { name: /generate report/i })).toBeDisabled();
  });

  test('Copy to Clipboard copies the generated report content', async ({ page, context }) => {
    await context.grantPermissions(['clipboard-read', 'clipboard-write']);
    await page.locator('#report-select').selectOption('medicine-stock-by-user');
    await page.getByRole('button', { name: /generate report/i }).click();
    await expect(page.locator('pre.report-content')).not.toBeEmpty({ timeout: 10000 });

    const reportText = await page.locator('pre.report-content').innerText();
    await page.getByRole('button', { name: /copy to clipboard/i }).click();

    const clipboardText = await page.evaluate(() => navigator.clipboard.readText());
    expect(clipboardText).toBe(reportText);
  });

  test.describe('Sales Trend Graph', () => {
    test.beforeEach(async ({ page }) => {
      await page.locator('#report-select').selectOption('sales-graph');
    });

    test('shows period and metric toggle buttons, defaulting to Daily/Quantity', async ({ page }) => {
      for (const period of ['Daily', 'Weekly', 'Monthly']) {
        await expect(page.getByRole('button', { name: period })).toBeVisible();
      }
      for (const metric of ['Quantity', 'Value (Rs)']) {
        await expect(page.getByRole('button', { name: metric })).toBeVisible();
      }
      await expect(page.getByRole('button', { name: 'Daily' })).toHaveClass(/btn-primary/);
      await expect(page.getByRole('button', { name: 'Quantity' })).toHaveClass(/btn-primary/);
    });

    test('loads chart data without an error and without the generic report controls', async ({ page }) => {
      // Sales Graph replaces the generic Generate Report button/date-range flow entirely —
      // it fetches on its own via its own From/To fields.
      await expect(page.getByRole('button', { name: /generate report/i })).not.toBeVisible();
      await expect(page.locator('svg[aria-label="Sales bar chart"]').or(page.getByText(/no approved sales found/i)))
        .toBeVisible({ timeout: 10000 });
      await expect(page.getByRole('alert')).not.toBeVisible();
    });

    test('switching to Weekly updates the active period button and the date range', async ({ page }) => {
      const fromBefore = await page.locator('#sg-from').inputValue();
      await page.getByRole('button', { name: 'Weekly' }).click();

      await expect(page.getByRole('button', { name: 'Weekly' })).toHaveClass(/btn-primary/);
      await expect(page.getByRole('button', { name: 'Daily' })).not.toHaveClass(/btn-primary/);
      await expect(async () => {
        expect(await page.locator('#sg-from').inputValue()).not.toBe(fromBefore);
      }).toPass({ timeout: 5000 });
    });

    test('switching to Monthly updates the active period button and widens the date range', async ({ page }) => {
      const fromBefore = await page.locator('#sg-from').inputValue();
      await page.getByRole('button', { name: 'Monthly' }).click();

      await expect(page.getByRole('button', { name: 'Monthly' })).toHaveClass(/btn-primary/);
      await expect(page.getByRole('button', { name: 'Daily' })).not.toHaveClass(/btn-primary/);
      await expect(async () => {
        const fromNow = await page.locator('#sg-from').inputValue();
        expect(fromNow).not.toBe(fromBefore);
        expect(new Date(fromNow).getTime()).toBeLessThan(new Date(fromBefore).getTime());
      }).toPass({ timeout: 5000 });
    });

    test('switching to Value (Rs) updates the active metric button', async ({ page }) => {
      await page.getByRole('button', { name: 'Value (Rs)' }).click();
      await expect(page.getByRole('button', { name: 'Value (Rs)' })).toHaveClass(/btn-primary/);
      await expect(page.getByRole('button', { name: 'Quantity' })).not.toHaveClass(/btn-primary/);
    });

    test('an invalid date range (From after To) shows a validation error', async ({ page }) => {
      await page.locator('#sg-from').fill('2026-01-10');
      await page.locator('#sg-to').fill('2026-01-01');
      await expect(page.getByText(/"from" date must be before or equal to "to" date/i)).toBeVisible();
    });
  });
});
