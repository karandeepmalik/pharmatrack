const { test, expect } = require('@playwright/test');
const { CREDENTIALS, loginAsAdmin, loginAsUser } = require('./helpers');

test.describe('Login', () => {
  test('shows the PharmaTrack login form', async ({ page }) => {
    await page.goto('/login');
    await expect(page.getByRole('heading', { name: 'PharmaTrack' })).toBeVisible();
    await expect(page.locator('#username')).toBeVisible();
    await expect(page.locator('#password')).toBeVisible();
    await expect(page.getByRole('button', { name: /sign in/i })).toBeVisible();
  });

  test('invalid credentials show an error and do not navigate away', async ({ page }) => {
    await page.goto('/login');
    await page.locator('#username').fill('admin');
    await page.locator('#password').fill('wrong-password');
    await page.getByRole('button', { name: /sign in/i }).click();

    await expect(page.getByRole('alert')).toHaveText(/invalid username or password/i);
    await expect(page).toHaveURL(/\/login/);
  });

  test('admin login redirects to the admin dashboard', async ({ page }) => {
    await loginAsAdmin(page);
    await expect(page.getByRole('heading', { name: /admin dashboard/i })).toBeVisible();
    await expect(page.getByText(/welcome, admin/i)).toBeVisible();
  });

  test('regular user login redirects to the user dashboard', async ({ page }) => {
    await loginAsUser(page, 'john');
    await expect(page.getByRole('heading', { name: /^dashboard$/i })).toBeVisible();
    await expect(page.getByText(/welcome, john/i)).toBeVisible();
  });
});

test.describe('Route protection', () => {
  test('unauthenticated visit to an admin page redirects to login', async ({ page }) => {
    await page.goto('/admin/dashboard');
    await expect(page).toHaveURL(/\/login/);
  });

  test('unauthenticated visit to a user page redirects to login', async ({ page }) => {
    await page.goto('/user/transactions');
    await expect(page).toHaveURL(/\/login/);
  });

  test('unknown route redirects to login', async ({ page }) => {
    await page.goto('/this-route-does-not-exist');
    await expect(page).toHaveURL(/\/login/);
  });
});

test.describe('Sign out', () => {
  test('signing out from the admin dashboard returns to login and re-protects admin routes', async ({ page }) => {
    await loginAsAdmin(page);
    await page.getByRole('button', { name: /sign out/i }).click();
    await expect(page).toHaveURL(/\/login/);

    await page.goto('/admin/dashboard');
    await expect(page).toHaveURL(/\/login/);
  });
});
