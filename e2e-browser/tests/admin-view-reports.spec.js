const { test, expect } = require('@playwright/test');
const { loginAsAdmin } = require('./helpers');

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
