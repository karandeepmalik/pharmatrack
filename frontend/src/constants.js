/**
 * Shared constants for the pharma inventory application.
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
};

// ── Transaction notes constraints (mirrors backend validation) ─────────

export const NOTES_CONSTRAINTS = {
  MIN_LENGTH: 5,
  MAX_LENGTH: 500,
};

// ── Transaction statuses ───────────────────────────────────────────────

export const TRANSACTION_STATUSES = ['ALL', 'PENDING', 'APPROVED', 'REJECTED'];

// ── Inventory type display label ────────────────────────────────────────

/** Human-readable label for an inventoryType value ('REGULAR_MEDICINE_STOCK' / 'ADMIN_MEDICINE_STOCK'). */
export const inventoryTypeLabel = (type) =>
  type === 'ADMIN_MEDICINE_STOCK' ? 'Admin Stock' : 'Regular Stock';
