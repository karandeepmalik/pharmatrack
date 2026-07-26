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
});
