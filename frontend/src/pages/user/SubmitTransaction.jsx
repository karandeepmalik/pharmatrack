import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import * as api from '../../api/api';
import { NOTES_CONSTRAINTS } from '../../constants';
import useScreenshot from '../../hooks/useScreenshot';
import ScreenshotUpload from '../../components/ScreenshotUpload';
import TransactionPreview from '../../components/TransactionPreview';

/**
 * Page component for submitting an inventory adjustment request.
 *
 * Responsibilities (page layer only):
 *  - Load inventory data on mount
 *  - Manage cascading select state (pharma → type → spec → qty)
 *  - Manage notes state
 *  - Compute form validity
 *  - Call the API on submit
 *  - Render child components (ScreenshotUpload, TransactionPreview)
 *
 * Screenshot logic lives in useScreenshot hook.
 * Upload UI lives in ScreenshotUpload component.
 * Preview UI lives in TransactionPreview component.
 */
export default function SubmitTransaction() {
  // ── Server data ──────────────────────────────────────────────────────
  const [inventory, setInventory] = useState([]);
  const [loading, setLoading]     = useState(true);

  // ── Cascading selects ────────────────────────────────────────────────
  const [selectedPharma, setSelectedPharma] = useState('');
  const [selectedType, setSelectedType]     = useState('');
  const [selectedSpec, setSelectedSpec]     = useState('');
  const [quantity, setQuantity]             = useState('');

  // ── Notes ────────────────────────────────────────────────────────────
  const [notes, setNotes] = useState('');

  // ── Screenshot (all state lives in hook) ─────────────────────────────
  const screenshot = useScreenshot();

  // ── UI feedback ──────────────────────────────────────────────────────
  const [submitting, setSubmitting]         = useState(false);
  const [successMessage, setSuccessMessage] = useState('');
  const [errorMessage, setErrorMessage]     = useState('');

  // ── Data fetch ───────────────────────────────────────────────────────
  useEffect(() => {
    api.getAvailableInventory()
      .then((r) => setInventory(r.data))
      .catch(() => setErrorMessage('Failed to load inventory'))
      .finally(() => setLoading(false));
  }, []);

  // ── Derived option lists ──────────────────────────────────────────────
  const pharmaOptions = [...new Map(
    inventory.map((i) => [i.pharmaId, { id: i.pharmaId, name: i.pharmaName }])
  ).values()];

  const typeOptions = selectedPharma
    ? [...new Set(
        inventory
          .filter((i) => String(i.pharmaId) === selectedPharma)
          .map((i) => i.medicineType)
      )]
    : [];

  const specOptions = selectedPharma && selectedType
    ? [...new Set(
        inventory
          .filter((i) => String(i.pharmaId) === selectedPharma && i.medicineType === selectedType)
          .map((i) => i.specification)
      )]
    : [];

  const selectedItem = inventory.find(
    (i) =>
      String(i.pharmaId) === selectedPharma &&
      i.medicineType === selectedType &&
      String(i.specification) === selectedSpec
  );

  const maxQty = selectedItem?.quantity ?? 0;

  const isFormValid =
    Boolean(selectedPharma) &&
    Boolean(selectedType) &&
    Boolean(selectedSpec) &&
    Boolean(quantity) &&
    Number(quantity) >= 1 &&
    Number(quantity) <= maxQty &&
    notes.trim().length >= NOTES_CONSTRAINTS.MIN_LENGTH &&
    !screenshot.screenshotError;

  // ── Handlers ─────────────────────────────────────────────────────────
  const handlePharmaChange = (e) => {
    setSelectedPharma(e.target.value);
    setSelectedType('');
    setSelectedSpec('');
    setQuantity('');
  };

  const handleTypeChange = (e) => {
    setSelectedType(e.target.value);
    setSelectedSpec('');
    setQuantity('');
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!isFormValid) return;

    setSubmitting(true);
    setErrorMessage('');
    setSuccessMessage('');

    try {
      await api.submitTransaction({
        medicineId: selectedItem.medicineId,
        quantity: Number(quantity),
        notes: notes.trim(),
        screenshotFile: screenshot.screenshotFile,
      });

      setSuccessMessage('Adjustment submitted successfully and is pending admin approval.');
      setSelectedPharma(''); setSelectedType(''); setSelectedSpec('');
      setQuantity(''); setNotes('');
      screenshot.handleRemoveScreenshot();

      const r = await api.getAvailableInventory();
      setInventory(r.data);
    } catch (err) {
      setErrorMessage(
        err.response?.data?.message || 'Failed to submit adjustment. Please try again.'
      );
    } finally {
      setSubmitting(false);
    }
  };

  // ── Render ────────────────────────────────────────────────────────────
  if (loading) return <div className="loading">Loading inventory…</div>;

  return (
    <div className="page submit-transaction-page">
      <div className="page-header">
        <h1>Submit Adjustment</h1>
        <Link to="/user/dashboard" className="btn btn-secondary">← Back</Link>
      </div>

      {successMessage && (
        <div role="alert" className="alert alert-success">{successMessage}</div>
      )}
      {errorMessage && (
        <div role="alert" className="alert alert-error">{errorMessage}</div>
      )}

      <form onSubmit={handleSubmit} noValidate>
        {/* Pharma */}
        <div className="form-group">
          <label htmlFor="pharma-select">Pharma Company</label>
          <select id="pharma-select" value={selectedPharma} onChange={handlePharmaChange}>
            <option value="">-- Select Pharma --</option>
            {pharmaOptions.map((p) => (
              <option key={p.id} value={String(p.id)}>{p.name}</option>
            ))}
          </select>
        </div>

        {/* Type */}
        <div className="form-group">
          <label htmlFor="type-select">Medicine Type</label>
          <select id="type-select" value={selectedType}
            disabled={!selectedPharma} onChange={handleTypeChange}>
            <option value="">-- Select Type --</option>
            {typeOptions.map((t) => <option key={t} value={t}>{t}</option>)}
          </select>
        </div>

        {/* Specification */}
        <div className="form-group">
          <label htmlFor="spec-select">
            Specification ({selectedType === 'VIAL' ? 'mg/ml' : 'mg'})
          </label>
          <select id="spec-select" value={selectedSpec}
            disabled={!selectedType}
            onChange={(e) => { setSelectedSpec(e.target.value); setQuantity(''); }}>
            <option value="">-- Select Specification --</option>
            {specOptions.map((s) => {
              const unit = selectedType === 'VIAL' ? 'mg/ml' : 'mg';
              return <option key={s} value={String(s)}>{s} {unit}</option>;
            })}
          </select>
        </div>

        {/* Quantity */}
        <div className="form-group">
          <label htmlFor="quantity-input">
            Quantity {selectedItem && `(max ${maxQty})`}
          </label>
          <input id="quantity-input" type="number" min="1" max={maxQty}
            value={quantity} disabled={!selectedSpec}
            onChange={(e) => setQuantity(e.target.value)} />
        </div>

        {/* Notes */}
        <div className="form-group">
          <label htmlFor="notes-input">
            Adjustment Note <span className="required">*</span>
          </label>
          <textarea id="notes-input" rows={3}
            placeholder="e.g. Dispatched to clinic B for FIP treatment"
            value={notes} onChange={(e) => setNotes(e.target.value)}
            maxLength={NOTES_CONSTRAINTS.MAX_LENGTH} />
          <small>
            {notes.length}/{NOTES_CONSTRAINTS.MAX_LENGTH} characters
            (minimum {NOTES_CONSTRAINTS.MIN_LENGTH})
          </small>
        </div>

        {/* Screenshot upload — delegated to component */}
        <ScreenshotUpload
          fileInputRef={screenshot.fileInputRef}
          screenshotPreview={screenshot.screenshotPreview}
          screenshotError={screenshot.screenshotError}
          screenshotFile={screenshot.screenshotFile}
          onFileChange={screenshot.handleScreenshotChange}
          onRemove={screenshot.handleRemoveScreenshot}
        />

        {/* Preview — delegated to component */}
        <TransactionPreview
          item={selectedItem}
          quantity={quantity}
          notes={notes}
          screenshotFile={screenshot.screenshotFile}
        />

        <button type="submit" disabled={!isFormValid || submitting} className="btn btn-primary">
          {submitting ? 'Submitting…' : 'Submit Adjustment'}
        </button>
      </form>
    </div>
  );
}
