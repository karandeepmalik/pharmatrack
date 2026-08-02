const { test, expect } = require('@playwright/test');
const { loginAsUser, PAYMENT_SCREENSHOT } = require('./helpers');

test.describe('Submit Medicine Dispatch', () => {
  test.beforeEach(async ({ page }) => {
    await loginAsUser(page, 'john');
    await page.goto('/user/submit');
  });

  test('shows the page heading and cascading form fields', async ({ page }) => {
    await expect(page.getByRole('heading', { name: /submit medicine dispatch/i })).toBeVisible();
    await expect(page.locator('#medicine-stock-type-select')).toBeVisible();
    await expect(page.locator('#pharma-select')).toBeVisible();
  });

  test('submit button stays disabled until the required fields and a screenshot are provided', async ({ page }) => {
    const submitBtn = page.getByRole('button', { name: /submit medicine dispatch/i });
    await expect(submitBtn).toBeDisabled();

    await page.locator('#pharma-select').selectOption({ index: 1 });
    await page.locator('#type-select').selectOption({ index: 1 });
    await page.locator('#spec-select').selectOption({ index: 1 });
    await page.locator('#quantity-input').fill('0.1');
    await page.locator('#notes-input').fill(`No screenshot yet ${Date.now()}`);
    await expect(submitBtn).toBeDisabled(); // still no screenshot

    await page.locator('#screenshot-input').setInputFiles(PAYMENT_SCREENSHOT);
    await expect(submitBtn).toBeEnabled();
  });

  test('submitting a valid dispatch shows a pending-approval success message', async ({ page }) => {
    await page.locator('#pharma-select').selectOption({ index: 1 });
    await page.locator('#type-select').selectOption({ index: 1 });
    await page.locator('#spec-select').selectOption({ index: 1 });
    await page.locator('#quantity-input').fill('0.1');
    await page.locator('#notes-input').fill(`Playwright full submit flow ${Date.now()}`);
    await page.locator('#screenshot-input').setInputFiles(PAYMENT_SCREENSHOT);
    await page.getByRole('button', { name: /submit medicine dispatch/i }).click();

    await expect(page.getByRole('alert')).toContainText(/pending admin approval/i, { timeout: 10000 });
  });

  test('a quantity greater than the available stock keeps the submit button disabled', async ({ page }) => {
    await page.locator('#pharma-select').selectOption({ index: 1 });
    await page.locator('#type-select').selectOption({ index: 1 });
    await page.locator('#spec-select').selectOption({ index: 1 });
    const qtyInput = page.locator('#quantity-input');
    const max = await qtyInput.getAttribute('max');
    await qtyInput.fill(String(Number(max) + 1000));
    await page.locator('#notes-input').fill(`Over-limit attempt ${Date.now()}`);
    await page.locator('#screenshot-input').setInputFiles(PAYMENT_SCREENSHOT);

    await expect(page.getByRole('button', { name: /submit medicine dispatch/i })).toBeDisabled();
  });

  test('uploading a screenshot shows it attached with a remove option', async ({ page }) => {
    await page.locator('#screenshot-input').setInputFiles(PAYMENT_SCREENSHOT);
    await expect(page.getByText(/1 screenshot attached/i)).toBeVisible();
    await expect(page.getByRole('button', { name: /remove screenshot 1/i })).toBeVisible();
  });

  test('removing an attached screenshot updates the count and can bring it back to zero', async ({ page }) => {
    await page.locator('#screenshot-input').setInputFiles(PAYMENT_SCREENSHOT);
    await expect(page.getByText(/1 screenshot attached/i)).toBeVisible();

    await page.getByRole('button', { name: /remove screenshot 1/i }).click();

    await expect(page.getByText(/screenshot attached/i)).not.toBeVisible();
    await expect(page.getByRole('button', { name: /submit medicine dispatch/i })).toBeDisabled();
  });

  test('uploading a second screenshot shows 2 attached and an "Add Another" option', async ({ page }) => {
    await page.locator('#screenshot-input').setInputFiles(PAYMENT_SCREENSHOT);
    await expect(page.getByText(/1 screenshot attached/i)).toBeVisible();
    await expect(page.getByRole('button', { name: /add another payment screenshot/i })).toBeVisible();

    await page.locator('#screenshot-input').setInputFiles(PAYMENT_SCREENSHOT);
    await expect(page.getByText(/2 screenshots attached/i)).toBeVisible();
    await expect(page.getByRole('button', { name: /remove screenshot 1/i })).toBeVisible();
    await expect(page.getByRole('button', { name: /remove screenshot 2/i })).toBeVisible();
  });

  test('selecting an item reveals an editable Price per Unit field pre-filled from the medicine price', async ({ page }) => {
    await expect(page.locator('#price-input')).not.toBeVisible();

    await page.locator('#pharma-select').selectOption({ index: 1 });
    await page.locator('#type-select').selectOption({ index: 1 });
    await page.locator('#spec-select').selectOption({ index: 1 });

    const priceInput = page.locator('#price-input');
    await expect(priceInput).toBeVisible();
    const prefilled = await priceInput.inputValue();
    expect(Number(prefilled)).toBeGreaterThan(0);
  });

  test('overriding Price per Unit is included in a successful submission', async ({ page }) => {
    await page.locator('#pharma-select').selectOption({ index: 1 });
    await page.locator('#type-select').selectOption({ index: 1 });
    await page.locator('#spec-select').selectOption({ index: 1 });
    await page.locator('#quantity-input').fill('0.1');
    await page.locator('#price-input').fill('12345');
    await page.locator('#notes-input').fill(`Price override test ${Date.now()}`);
    await page.locator('#screenshot-input').setInputFiles(PAYMENT_SCREENSHOT);
    await page.getByRole('button', { name: /submit medicine dispatch/i }).click();

    await expect(page.getByRole('alert')).toContainText(/pending admin approval/i, { timeout: 10000 });
  });

  // Regression: a successful submit used to be immediately followed, in the same try/catch, by an
  // unrelated stock-refresh call. A slow/failing refresh could stamp a misleading "failed to
  // submit" error alert on top of the genuine success alert. After the dispatch succeeds, only
  // the success alert should ever be present — never both at once.
  test('a successful submission shows only the success alert, never an error alert alongside it', async ({ page }) => {
    await page.locator('#pharma-select').selectOption({ index: 1 });
    await page.locator('#type-select').selectOption({ index: 1 });
    await page.locator('#spec-select').selectOption({ index: 1 });
    await page.locator('#quantity-input').fill('0.1');
    await page.locator('#notes-input').fill(`No stray error alert ${Date.now()}`);
    await page.locator('#screenshot-input').setInputFiles(PAYMENT_SCREENSHOT);
    await page.getByRole('button', { name: /submit medicine dispatch/i }).click();

    await expect(page.getByRole('alert')).toContainText(/pending admin approval/i, { timeout: 10000 });
    await expect(page.getByRole('alert')).toHaveCount(1);
    await expect(page.getByText(/failed to submit/i)).not.toBeVisible();
  });
});
