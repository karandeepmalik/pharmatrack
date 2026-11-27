const { test, expect } = require('@playwright/test');
const { loginAsAdmin, loginAsUser, PAYMENT_SCREENSHOT, scrollUntilVisible } = require('./helpers');

test.describe('Add User Dispatches (admin)', () => {
  test.beforeEach(async ({ page }) => {
    await loginAsAdmin(page);
    await page.goto('/admin/add-user-dispatch');
  });

  test('shows the page heading, back link, and a User selector with no form until a user is chosen', async ({ page }) => {
    await expect(page.getByRole('heading', { name: /add user dispatches/i })).toBeVisible();
    await expect(page.getByRole('link', { name: /← back/i })).toHaveAttribute('href', '/admin/dashboard');
    await expect(page.locator('#target-user-select')).toBeVisible();
    await expect(page.locator('#pharma-select')).not.toBeVisible();
  });

  test('selecting a user reveals the dispatch form addressed to that user', async ({ page }) => {
    const johnOption = page.locator('#target-user-select option', { hasText: 'John Doe' });
    await page.locator('#target-user-select').selectOption(await johnOption.getAttribute('value'));

    await expect(page.getByRole('heading', { name: /adding a dispatch for john doe/i })).toBeVisible();
    await expect(page.locator('#pharma-select')).toBeVisible();
  });

  test('submitting a dispatch on behalf of a user shows a success message naming that user', async ({ page }) => {
    test.setTimeout(60000);
    const johnOption = page.locator('#target-user-select option', { hasText: 'John Doe' });
    await page.locator('#target-user-select').selectOption(await johnOption.getAttribute('value'));

    await page.locator('#pharma-select').selectOption({ index: 1 });
    await page.locator('#type-select').selectOption({ index: 1 });
    await page.locator('#spec-select').selectOption({ index: 1 });
    await page.locator('#quantity-input').fill('0.1');
    await page.locator('#notes-input').fill(`Playwright admin-add-user-dispatch ${Date.now()}`);
    await page.locator('#screenshot-input').setInputFiles(PAYMENT_SCREENSHOT);
    await page.getByRole('button', { name: /add dispatch/i }).click();

    await expect(page.getByRole('alert')).toContainText(/john doe/i, { timeout: 10000 });
    await expect(page.getByRole('alert')).toContainText(/pending admin approval/i);
  });

  // The actual point of the feature: a dispatch the admin submits on behalf of a user must
  // reduce THAT user's own stock and show up in THEIR OWN dispatch history, exactly as if the
  // user had submitted it themselves — not stay invisible to them or sit under the admin's account.
  test('a dispatch submitted on behalf of john.doe shows up in his own Medicine Dispatch History', async ({ page }) => {
    test.setTimeout(60000);
    const uniqueNote = `Playwright admin-on-behalf history check ${Date.now()}`;

    const johnOption = page.locator('#target-user-select option', { hasText: 'John Doe' });
    await page.locator('#target-user-select').selectOption(await johnOption.getAttribute('value'));

    await page.locator('#pharma-select').selectOption({ index: 1 });
    await page.locator('#type-select').selectOption({ index: 1 });
    await page.locator('#spec-select').selectOption({ index: 1 });
    await page.locator('#quantity-input').fill('0.1');
    await page.locator('#notes-input').fill(uniqueNote);
    await page.locator('#screenshot-input').setInputFiles(PAYMENT_SCREENSHOT);
    await page.getByRole('button', { name: /add dispatch/i }).click();
    await expect(page.getByRole('alert')).toContainText(/pending admin approval/i, { timeout: 10000 });

    await loginAsUser(page, 'john');
    await page.goto('/user/transactions');
    await page.locator('#notes-search').fill(uniqueNote);
    await expect(page.getByText(uniqueNote)).toBeVisible({ timeout: 15000 });
  });

  test('switching the selected user remounts the form and clears any in-progress selections', async ({ page }) => {
    // Pharma may auto-select a default (e.g. Shield FX) if the newly selected user also
    // stocks it, so assert against Medicine Type instead — DispatchForm never auto-selects
    // that for anyone, so a non-empty value here can only have survived from the prior user.
    const johnOption = page.locator('#target-user-select option', { hasText: 'John Doe' });
    await page.locator('#target-user-select').selectOption(await johnOption.getAttribute('value'));
    await page.locator('#type-select').selectOption({ index: 1 });
    await expect(page.locator('#type-select')).not.toHaveValue('');

    const janeOption = page.locator('#target-user-select option', { hasText: 'Jane Smith' });
    await page.locator('#target-user-select').selectOption(await janeOption.getAttribute('value'));

    await expect(page.getByRole('heading', { name: /adding a dispatch for jane smith/i })).toBeVisible();
    await expect(page.locator('#type-select')).toHaveValue('');
  });
});
