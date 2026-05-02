import React from 'react';

/**
 * Read-only preview card shown before the user submits.
 * Extracted from SubmitTransaction to uphold SRP.
 *
 * @param {Object}   props
 * @param {Object}   props.item           - selected inventory item
 * @param {string}   props.quantity       - quantity string from form input
 * @param {string}   props.notes          - adjustment notes text
 * @param {File|null} props.screenshotFile - attached file or null
 */
export default function TransactionPreview({ item, quantity, notes, screenshotFile }) {
  if (!item || !quantity || notes.trim().length < 5) return null;

  return (
    <div className="preview-card" role="region" aria-label="Submission preview">
      <h3>Preview</h3>
      <p>
        <strong>Medicine:</strong>{' '}
        {item.medicineName} ({item.medicineType}, {item.specification} mg)
      </p>
      <p>
        <strong>Quantity:</strong> {quantity}
      </p>
      <p>
        <strong>Adjustment Note:</strong> {notes}
      </p>
      {screenshotFile && (
        <p>
          <strong>Payment Screenshot:</strong> {screenshotFile.name} attached ✓
        </p>
      )}
    </div>
  );
}
