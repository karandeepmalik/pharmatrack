import React from 'react';

/**
 * Read-only preview card shown before the user submits.
 * Extracted from SubmitTransaction to uphold SRP.
 *
 * @param {Object}   props
 * @param {Object}   props.item           - selected inventory item
 * @param {string}   props.quantity       - quantity string from form input
 * @param {string}   props.notes          - adjustment notes text
 * @param {number}   props.screenshotCount - number of valid screenshots attached
 * @param {number}   props.pricePerUnit   - price per unit (overridden or item's default price)
 */
export default function TransactionPreview({ item, quantity, notes, screenshotCount = 0, pricePerUnit }) {
  if (!item || !quantity || notes.trim().length < 5) return null;

  const displayPrice = pricePerUnit != null ? pricePerUnit : item?.price;

  return (
    <div className="preview-card" role="region" aria-label="Submission preview">
      <h3>Preview</h3>
      <p>
        <strong>Medicine:</strong>{' '}
        {item.medicineName} ({item.medicineType}, {item.medicineType==='VIAL'?`${item.concentrationMgPerMl??item.specification} mg/ml`:`${item.specification} mg (10 Tablets)`})
      </p>
      <p>
        <strong>Quantity:</strong> {quantity}
      </p>
      {displayPrice != null && (
        <p>
          <strong>Price per Unit:</strong> Rs {displayPrice?.toLocaleString()}
        </p>
      )}
      <p>
        <strong>Medicine Dispatch Note:</strong> {notes}
      </p>
      {screenshotCount > 0 && (
        <p>
          <strong>Payment Screenshots:</strong> {screenshotCount} attached ✓
        </p>
      )}
    </div>
  );
}
