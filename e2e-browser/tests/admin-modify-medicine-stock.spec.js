const { test, expect } = require('@playwright/test');
const { loginAsAdmin } = require('./helpers');

test.describe('Modify Medicine Stock', () => {
  test.beforeEach(async ({ page }) => {
    await loginAsAdmin(page);
    await page.goto('/admin/modify-medicine-stock');
  });

  test('shows the page heading and required form fields', async ({ page }) => {
    await expect(page.getByRole('heading', { name: /modify medicine stock/i })).toBeVisible();
    await expect(page.locator('#user-select')).toBeVisible();
    await expect(page.locator('#medicine-select')).toBeVisible();
    await expect(page.locator('#type-select')).toBeVisible();
    await expect(page.locator('#qty-input')).toBeVisible();
    await expect(page.locator('#note-input')).toBeVisible();
  });

  test('selecting a user and medicine reveals the current-quantity badge', async ({ page }) => {
    await page.locator('#user-select').selectOption({ index: 1 });
    await page.locator('#medicine-select').selectOption({ index: 1 });
    await expect(page.getByText(/current .*quantity for user/i)).toBeVisible();
  });

  test('adding medicine stock with a note succeeds and shows a success message', async ({ page }) => {
    await page.locator('#user-select').selectOption({ index: 1 });
    await page.locator('#medicine-select').selectOption({ index: 1 });
    await page.locator('#type-select').selectOption('ADD');
    await page.locator('#qty-input').fill('1');
    await page.locator('#note-input').fill(`Playwright ADD test note ${Date.now()}`);
    await page.getByRole('button', { name: /add medicine stock/i }).click();

    await expect(page.getByRole('alert')).toContainText(/added successfully/i, { timeout: 10000 });
  });

  test('submitting without a note is rejected client-side (submit button stays disabled)', async ({ page }) => {
    await page.locator('#user-select').selectOption({ index: 1 });
    await page.locator('#medicine-select').selectOption({ index: 1 });
    await page.locator('#qty-input').fill('1');
    // Leave note empty
    const submitBtn = page.getByRole('button', { name: /add medicine stock|reduce medicine stock/i });
    await expect(submitBtn).toBeDisabled();
  });

  test('the submit button label switches between Add and Reduce Medicine Stock', async ({ page }) => {
    await expect(page.getByRole('button', { name: /^add medicine stock$/i })).toBeVisible();
    await page.locator('#type-select').selectOption('REDUCE');
    await expect(page.getByRole('button', { name: /^reduce medicine stock$/i })).toBeVisible();
  });

  test('reducing medicine stock succeeds after first adding enough to reduce', async ({ page }) => {
    await page.locator('#user-select').selectOption({ index: 1 });
    await page.locator('#medicine-select').selectOption({ index: 1 });

    // Ensure there's enough to reduce: add first.
    await page.locator('#type-select').selectOption('ADD');
    await page.locator('#qty-input').fill('5');
    await page.locator('#note-input').fill(`Playwright pre-REDUCE ADD ${Date.now()}`);
    await page.getByRole('button', { name: /add medicine stock/i }).click();
    await expect(page.getByRole('alert')).toContainText(/added successfully/i, { timeout: 10000 });

    await page.locator('#type-select').selectOption('REDUCE');
    await page.locator('#qty-input').fill('1');
    await page.locator('#note-input').fill(`Playwright REDUCE test note ${Date.now()}`);
    await page.getByRole('button', { name: /reduce medicine stock/i }).click();

    await expect(page.getByRole('alert')).toContainText(/reduced successfully/i, { timeout: 10000 });
  });

  test('the quantity field caps its max at the current available quantity when reducing', async ({ page }) => {
    await page.locator('#user-select').selectOption({ index: 1 });
    await page.locator('#medicine-select').selectOption({ index: 1 });

    const badgeText = await page.getByText(/current .*quantity for user/i).innerText();
    const currentQty = badgeText.match(/([\d.]+)\s*units/i)?.[1];

    await page.locator('#type-select').selectOption('REDUCE');

    if (currentQty != null) {
      const max = await page.locator('#qty-input').getAttribute('max');
      expect(Number(max)).toBeCloseTo(Number(currentQty), 5);
    }
  });

  test('checking In Transit reveals the Days in transit field, defaulting to 2', async ({ page }) => {
    await expect(page.locator('#transit-days-input')).not.toBeVisible();
    await page.locator('#in-transit-checkbox').check();
    await expect(page.locator('#transit-days-input')).toBeVisible();
    await expect(page.locator('#transit-days-input')).toHaveValue('2');
  });

  test('unchecking In Transit hides the Days in transit field again', async ({ page }) => {
    await page.locator('#in-transit-checkbox').check();
    await expect(page.locator('#transit-days-input')).toBeVisible();
    await page.locator('#in-transit-checkbox').uncheck();
    await expect(page.locator('#transit-days-input')).not.toBeVisible();
  });

  test('submitting an ADD with In Transit checked and a custom transit-days value succeeds', async ({ page }) => {
    await page.locator('#user-select').selectOption({ index: 1 });
    await page.locator('#medicine-select').selectOption({ index: 1 });
    await page.locator('#type-select').selectOption('ADD');
    await page.locator('#qty-input').fill('2');
    await page.locator('#note-input').fill(`Playwright in-transit ADD ${Date.now()}`);
    await page.locator('#in-transit-checkbox').check();
    await page.locator('#transit-days-input').fill('5');
    await page.getByRole('button', { name: /add medicine stock/i }).click();

    await expect(page.getByRole('alert')).toContainText(/added successfully/i, { timeout: 10000 });
  });

  test('reducing more than the available quantity shows a 409 "Insufficient stock" error', async ({ page, context }) => {
    await page.locator('#user-select').selectOption({ index: 1 });
    await page.locator('#medicine-select').selectOption({ index: 1 });

    const badgeText = await page.getByText(/current .*quantity for user/i).innerText();
    const currentQty = Number(badgeText.match(/([\d.]+)\s*units/i)?.[1] ?? 0);
    test.skip(currentQty < 0.2, 'not enough existing stock on this user/medicine to build the race below');

    // The quantity field's max attribute mirrors currentQty, so the browser's native HTML5
    // validation blocks ever submitting a raw over-limit value through this form — there's no
    // way to reach the server-side 409 by just typing a bigger number. The only real path to it
    // is a race: this tab's `max` is a snapshot from page load, so if the same stock gets
    // reduced elsewhere (e.g. a second concurrent admin session) between that snapshot and this
    // tab's submit, the still-valid-looking amount can exceed what's actually left server-side.
    await page.locator('#type-select').selectOption('REDUCE');
    await page.locator('#qty-input').fill(String(currentQty));
    await page.locator('#note-input').fill(`Playwright race REDUCE ${Date.now()}`);

    const page2 = await context.newPage();
    await page2.goto('/admin/modify-medicine-stock');
    await page2.locator('#user-select').selectOption({ index: 1 });
    await page2.locator('#medicine-select').selectOption({ index: 1 });
    await page2.locator('#type-select').selectOption('REDUCE');
    await page2.locator('#qty-input').fill(String(currentQty));
    await page2.locator('#note-input').fill(`Playwright race drain ${Date.now()}`);
    await page2.getByRole('button', { name: /reduce medicine stock/i }).click();
    await expect(page2.getByRole('alert')).toContainText(/reduced successfully/i, { timeout: 10000 });
    await page2.close();

    // page's form still shows the pre-drain max, so this submits successfully client-side —
    // the server must be the one to catch that the stock is now gone.
    await page.getByRole('button', { name: /reduce medicine stock/i }).click();
    await expect(page.getByRole('alert')).toContainText(/insufficient stock/i, { timeout: 10000 });

    // Cleanup: page2's drain left this user/medicine at ~0, which is real state shared with
    // every other spec that submits a dispatch via the same default cascading-select — restore
    // it, or every later test submitting against this user/medicine finds Submit permanently
    // disabled (maxQty=0). Mirrors the ADD-then-REDUCE pattern already used elsewhere in this
    // file to guarantee enough stock to reduce.
    await page.locator('#type-select').selectOption('ADD');
    await page.locator('#qty-input').fill(String(currentQty));
    await page.locator('#note-input').fill(`Playwright race cleanup restore ${Date.now()}`);
    await page.getByRole('button', { name: /add medicine stock/i }).click();
    await expect(page.getByRole('alert')).toContainText(/added successfully/i, { timeout: 10000 });
  });

  test('checking Internal Movement does not block a normal ADD submission', async ({ page }) => {
    await page.locator('#user-select').selectOption({ index: 1 });
    await page.locator('#medicine-select').selectOption({ index: 1 });
    await page.locator('#type-select').selectOption('ADD');
    await page.locator('#qty-input').fill('1');
    await page.locator('#note-input').fill(`Playwright internal-movement ADD ${Date.now()}`);
    await page.locator('#internal-movement-checkbox').check();
    await page.getByRole('button', { name: /add medicine stock/i }).click();

    await expect(page.getByRole('alert')).toContainText(/added successfully/i, { timeout: 10000 });
  });
});
