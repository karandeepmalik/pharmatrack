const { test, expect } = require('@playwright/test');
const { loginAsAdmin } = require('./helpers');

test.describe('Manage Users', () => {
  test.beforeEach(async ({ page }) => {
    await loginAsAdmin(page);
    await page.goto('/admin/users');
  });

  test('shows the page heading and the seeded demo users', async ({ page }) => {
    await expect(page.getByRole('heading', { name: /manage users/i })).toBeVisible();
    await expect(page.getByRole('table')).toBeVisible();
    await expect(page.getByText('john.doe')).toBeVisible();
    await expect(page.getByText('jane.smith')).toBeVisible();
  });

  test('creating a new user shows it in the table', async ({ page }) => {
    const username = `pw_user_${Date.now()}`;
    await page.locator('#fullName').fill('Playwright Test User');
    await page.locator('#username').fill(username);
    await page.locator('#email').fill(`${username}@example.com`);
    await page.locator('#password').fill('TestPass@123');
    await page.locator('#role').selectOption('USER');
    await page.getByRole('button', { name: /create user/i }).click();

    await expect(page.getByRole('alert')).toContainText(/created successfully/i, { timeout: 10000 });
    await expect(page.getByRole('cell', { name: username, exact: true })).toBeVisible();
  });

  test('creating a user with a duplicate username shows an error', async ({ page }) => {
    await page.locator('#fullName').fill('Duplicate Attempt');
    await page.locator('#username').fill('john.doe');
    await page.locator('#email').fill(`dup_${Date.now()}@example.com`);
    await page.locator('#password').fill('TestPass@123');
    await page.getByRole('button', { name: /create user/i }).click();

    await expect(page.getByRole('alert')).toBeVisible({ timeout: 10000 });
    await expect(page.getByRole('alert')).not.toContainText(/created successfully/i);
  });

  test('deactivating and reactivating a freshly created user toggles its status badge', async ({ page }) => {
    const username = `pw_toggle_${Date.now()}`;
    await page.locator('#fullName').fill('Toggle Test User');
    await page.locator('#username').fill(username);
    await page.locator('#email').fill(`${username}@example.com`);
    await page.locator('#password').fill('TestPass@123');
    await page.getByRole('button', { name: /create user/i }).click();
    await expect(page.getByRole('alert')).toContainText(/created successfully/i, { timeout: 10000 });

    const row = page.getByRole('row', { name: new RegExp(username) });
    await expect(row.getByText('Active')).toBeVisible();

    await row.getByRole('button', { name: /deactivate/i }).click();
    await expect(row.getByText('Inactive')).toBeVisible({ timeout: 10000 });

    await row.getByRole('button', { name: /^activate$/i }).click();
    await expect(row.getByText('Active')).toBeVisible({ timeout: 10000 });
  });

  test('changing a freshly created user password succeeds', async ({ page }) => {
    const username = `pw_changepass_${Date.now()}`;
    await page.locator('#fullName').fill('Password Change User');
    await page.locator('#username').fill(username);
    await page.locator('#email').fill(`${username}@example.com`);
    await page.locator('#password').fill('TestPass@123');
    await page.getByRole('button', { name: /create user/i }).click();
    await expect(page.getByRole('alert')).toContainText(/created successfully/i, { timeout: 10000 });

    const row = page.getByRole('row', { name: new RegExp(username) });
    await row.getByRole('button', { name: /change password/i }).click();
    // The inline password field renders as a sibling row, not nested inside `row` — scope to
    // the page instead.
    await page.getByLabel(new RegExp(`new password for ${username}`, 'i')).fill('NewPass@456');
    await page.getByRole('button', { name: /set password/i }).click();

    await expect(page.getByRole('alert')).toContainText(/updated successfully/i, { timeout: 10000 });
  });
});
