const { test, expect } = require('@playwright/test');
const { loginAsUser, loginAsAdmin, PAYMENT_SCREENSHOT } = require('./helpers');

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

  // Regression: the Price per Unit default used to be looked up from the raw, unfiltered
  // medicineStock list — ignoring which Stock Type (Regular vs Admin) was actually selected.
  // When the same (pharma, type, spec) exists as two different catalog entries — one the user
  // holds as REGULAR_MEDICINE_STOCK, the other as ADMIN_MEDICINE_STOCK, at a different price —
  // the wrong bucket's price would silently win regardless of the Stock Type dropdown.
  test('Price per Unit default respects the selected Stock Type when both buckets share a spec', async ({ page }) => {
    test.setTimeout(60000);
    const suffix = Date.now();
    const dupName = `Playwright Duplicate Tablet 12mg ${suffix}`;
    const dupPrice = 9999;

    // Create a second Tablet-12mg catalog entry under the same pharma, at a different price.
    await loginAsAdmin(page);
    await page.goto('/admin/medicines');
    await page.locator('#med-pharma-select').selectOption({ index: 1 });
    await page.locator('#med-name-input').fill(dupName);
    await page.locator('#med-type-select').selectOption('TABLET');
    await page.locator('#med-spec-input').fill('12');
    await page.locator('#med-price-input').fill(String(dupPrice));
    await page.getByRole('button', { name: /add medicine/i }).click();
    await expect(page.getByRole('alert')).toContainText(/created successfully/i, { timeout: 10000 });

    // Give john ADMIN_MEDICINE_STOCK of that duplicate entry.
    await page.goto('/admin/modify-medicine-stock');
    const johnOption = page.locator('#user-select option', { hasText: 'John Doe' });
    await page.locator('#user-select').selectOption(await johnOption.getAttribute('value'));
    const dupOption = page.locator(`#medicine-select option`, { hasText: dupName });
    const dupValue = await dupOption.getAttribute('value');
    await page.locator('#medicine-select').selectOption(dupValue);
    await page.locator('#medicine-stock-type-select').selectOption('ADMIN_MEDICINE_STOCK');
    await page.locator('#type-select').selectOption('ADD');
    await page.locator('#qty-input').fill('5');
    await page.locator('#note-input').fill(`Playwright dup-price setup ${suffix}`);
    await page.getByRole('button', { name: /add medicine stock/i }).click();
    await expect(page.getByRole('alert')).toContainText(/added successfully/i, { timeout: 10000 });

    // Regular bucket's own Tablet 12mg must show its own (different, seeded) price.
    await loginAsUser(page, 'john');
    await page.goto('/user/submit');
    await page.locator('#pharma-select').selectOption({ index: 1 });
    await page.locator('#type-select').selectOption('TABLET');
    await page.locator('#spec-select').selectOption({ label: '12 mg (10 Tablets)' });
    const regularPrice = await page.locator('#price-input').inputValue();
    expect(Number(regularPrice)).not.toBe(dupPrice);

    // Admin bucket's Tablet 12mg must show the duplicate's own price, not the Regular one.
    await page.locator('#medicine-stock-type-select').selectOption('ADMIN_MEDICINE_STOCK');
    await page.locator('#pharma-select').selectOption({ index: 1 });
    await page.locator('#type-select').selectOption('TABLET');
    await page.locator('#spec-select').selectOption({ label: '12 mg (10 Tablets)' });
    await expect(page.locator('#price-input')).toHaveValue(String(dupPrice));
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
