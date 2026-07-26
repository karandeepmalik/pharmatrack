const { test, expect } = require('@playwright/test');
const { loginAsUser } = require('./helpers');

test.describe('User Dashboard', () => {
  test.beforeEach(async ({ page }) => {
    await loginAsUser(page, 'john');
  });

  test('shows the dashboard heading and welcome message', async ({ page }) => {
    await expect(page.getByRole('heading', { name: /^dashboard$/i })).toBeVisible();
    await expect(page.getByText(/welcome, john doe/i)).toBeVisible();
  });

  test('has nav cards to Submit Medicine Dispatch and Medicine Dispatch History', async ({ page }) => {
    await expect(page.getByRole('link', { name: /submit medicine dispatch/i })).toHaveAttribute('href', '/user/submit');
    await expect(page.getByRole('link', { name: /medicine dispatch history/i })).toHaveAttribute('href', '/user/transactions');
  });

  test('clicking Submit Medicine Dispatch navigates to the submission form', async ({ page }) => {
    await page.getByRole('link', { name: /submit medicine dispatch/i }).click();
    await expect(page).toHaveURL(/\/user\/submit/);
    await expect(page.getByRole('heading', { name: /submit medicine dispatch/i })).toBeVisible();
  });

  test('clicking Medicine Dispatch History navigates to the user history page', async ({ page }) => {
    await page.getByRole('link', { name: /medicine dispatch history/i }).click();
    await expect(page).toHaveURL(/\/user\/transactions/);
    await expect(page.getByRole('heading', { name: /medicine dispatch history/i })).toBeVisible();
  });
});
