/**
 * Shared constants for the pharma medicine stock application.
 * Centralised here so validation rules stay in sync between
 * components and tests without magic numbers scattered across files.
 */

// ── Screenshot upload constraints ─────────────────────────────────────

export const SCREENSHOT_CONSTRAINTS = {
  /** Accepted image MIME types (mirrors backend ScreenshotProcessor) */
  ALLOWED_TYPES: ['image/png', 'image/jpeg', 'image/jpg', 'image/webp', 'image/gif'],
  /** Accepted MIME types as an <input accept=""> string */
  ACCEPT_ATTR: 'image/png,image/jpeg,image/jpg,image/webp,image/gif',
  /** Maximum file size in bytes (5 MB) */
  MAX_BYTES: 5 * 1024 * 1024,
  /** Human-readable size limit label */
  MAX_LABEL: '5 MB',
  /** Maximum number of screenshots attachable to a single dispatch (submit or admin edit) */
  MAX_COUNT: 8,
};

// ── Transaction notes constraints (mirrors backend validation) ─────────

export const NOTES_CONSTRAINTS = {
  MIN_LENGTH: 5,
  MAX_LENGTH: 500,
};

// ── Transaction statuses ───────────────────────────────────────────────

export const TRANSACTION_STATUSES = ['ALL', 'PENDING', 'APPROVED', 'REJECTED'];

// ── MedicineStock type display label ────────────────────────────────────────

/** Human-readable label for a medicineStockType value ('REGULAR_MEDICINE_STOCK' / 'ADMIN_MEDICINE_STOCK'). */
export const medicineStockTypeLabel = (type) =>
  type === 'ADMIN_MEDICINE_STOCK' ? 'Admin Stock' : 'Regular Stock';

// ── Medicine option labels (filter dropdowns) ───────────────────────────────

/**
 * Brand-inclusive label for a medicine in a filter dropdown — "<Pharma Company> Vial <spec> ml"
 * or "<Pharma Company> Tablet <spec> mg (10 Tablets)". Centralised here so every "Medicine Spec"
 * filter across the app (ViewPastTransactions, AdminEditDispatch, ViewMedicineStockAdjustments,
 * MyTransactions, ViewReports) shows the same brand-qualified label instead of each page
 * formatting its own type+spec-only variant.
 */
export const medicineOptionLabel = (m) => {
  const brand = m.pharmaCompany?.name ? `${m.pharmaCompany.name} ` : '';
  return m.type === 'VIAL'
    ? `${brand}Vial ${m.specification} ml`
    : `${brand}Tablet ${m.specification} mg (10 Tablets)`;
};

// ── Payment status ──────────────────────────────────────────────────────

/** Selectable payment status options, in display order. PAID is the default on submission. */
export const PAYMENT_STATUSES = ['PAID', 'UNPAID', 'PARTIAL_PAYMENT'];

/** Human-readable label for a paymentStatus value. */
export const paymentStatusLabel = (status) => {
  switch (status) {
    case 'UNPAID': return 'Unpaid';
    case 'PARTIAL_PAYMENT': return 'Partial Payment';
    default: return 'Paid';
  }
};
