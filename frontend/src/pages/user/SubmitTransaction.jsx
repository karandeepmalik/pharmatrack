import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import * as api from '../../api/api';
import { NOTES_CONSTRAINTS } from '../../constants';
import useScreenshot from '../../hooks/useScreenshot';
import ScreenshotUpload from '../../components/ScreenshotUpload';
import TransactionPreview from '../../components/TransactionPreview';

export default function SubmitTransaction() {
  // ── Server data ──────────────────────────────────────────────────────
  const [inventory, setInventory] = useState([]);
  const [loading, setLoading]     = useState(true);

  // ── Inventory type selector ──────────────────────────────────────────
  const [selectedInventoryType, setSelectedInventoryType] = useState('REGULAR');

  // ── Cascading selects ────────────────────────────────────────────────
  const [selectedPharma, setSelectedPharma] = useState('');
  const [selectedType, setSelectedType]     = useState('');
  const [selectedSpec, setSelectedSpec]     = useState('');
  const [quantity, setQuantity]             = useState('');

  // ── Price override ───────────────────────────────────────────────────
  const [priceOverride, setPriceOverride] = useState('');

  // ── Notes ────────────────────────────────────────────────────────────
  const [notes, setNotes] = useState('');

  // ── Screenshots (all state lives in hook) ────────────────────────────
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

  // ── Derived option lists (filtered by inventory type) ─────────────────
  const filteredInventory = inventory.filter(
    (i) => i.inventoryType === selectedInventoryType
  );

  const pharmaOptions = [...new Map(
    filteredInventory.map((i) => [i.pharmaId, { id: i.pharmaId, name: i.pharmaName }])
  ).values()];

  const typeOptions = selectedPharma
    ? [...new Set(
        filteredInventory
          .filter((i) => String(i.pharmaId) === selectedPharma)
          .map((i) => i.medicineType)
      )]
    : [];

  const specOptions = selectedPharma && selectedType
    ? [...new Set(
        filteredInventory
          .filter((i) => String(i.pharmaId) === selectedPharma && i.medicineType === selectedType)
          .map((i) => i.specification)
      )]
    : [];

  const selectedItem = filteredInventory.find(
    (i) =>
      String(i.pharmaId) === selectedPharma &&
      i.medicineType === selectedType &&
      String(i.specification) === selectedSpec
  );

  const maxQty = selectedItem?.quantity ?? 0;
  const validScreenshots = screenshot.screenshots.filter((s) => s.file != null);

  const isFormValid =
    Boolean(selectedPharma) &&
    Boolean(selectedType) &&
    Boolean(selectedSpec) &&
    Boolean(quantity) &&
    Number(quantity) >= 1 &&
    Number(quantity) <= maxQty &&
    notes.trim().length >= NOTES_CONSTRAINTS.MIN_LENGTH &&
    screenshot.hasAnyScreenshot &&
    !screenshot.hasAnyError;

  // ── Handlers ─────────────────────────────────────────────────────────
  const handleInventoryTypeChange = (e) => {
    setSelectedInventoryType(e.target.value);
    setSelectedPharma('');
    setSelectedType('');
    setSelectedSpec('');
    setQuantity('');
    setPriceOverride('');
  };

  const handlePharmaChange = (e) => {
    setSelectedPharma(e.target.value);
    setSelectedType('');
    setSelectedSpec('');
    setQuantity('');
    setPriceOverride('');
  };

  const handleTypeChange = (e) => {
    setSelectedType(e.target.value);
    setSelectedSpec('');
    setQuantity('');
    setPriceOverride('');
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
        screenshotFiles: validScreenshots.map((s) => s.file),
        pricePerUnit: priceOverride !== '' ? parseInt(priceOverride, 10) : undefined,
        inventoryType: selectedInventoryType,
      });

      setSuccessMessage('Inventory adjustment submitted successfully and is pending admin approval.');
      setSelectedInventoryType('REGULAR');
      setSelectedPharma(''); setSelectedType(''); setSelectedSpec('');
      setQuantity(''); setNotes(''); setPriceOverride('');
      screenshot.clearAll();

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
        <h1>Submit Inventory Adjustment</h1>
        <Link to="/user/dashboard" className="btn btn-secondary">← Back</Link>
      </div>

      {successMessage && (
        <div role="alert" className="alert alert-success">{successMessage}</div>
      )}
      {errorMessage && (
        <div role="alert" className="alert alert-error">{errorMessage}</div>
      )}

      <form onSubmit={handleSubmit} noValidate>
        {/* Inventory Type */}
        <div className="form-group">
          <label htmlFor="inventory-type-select">Inventory Type</label>
          <select
            id="inventory-type-select"
            value={selectedInventoryType}
            onChange={handleInventoryTypeChange}
          >
            <option value="REGULAR">Regular</option>
            <option value="ADMIN_STOCK">Admin Stock</option>
          </select>
        </div>

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
            {selectedType === 'VIAL' ? 'Volume (ml)' : 'Specification (mg (10 Tablets))'}
          </label>
          <select id="spec-select" value={selectedSpec}
            disabled={!selectedType}
            onChange={(e) => {
              const newSpec = e.target.value;
              setSelectedSpec(newSpec);
              setQuantity('');
              const newItem = inventory.find(
                (i) => String(i.pharmaId) === selectedPharma &&
                       i.medicineType === selectedType &&
                       String(i.specification) === newSpec
              );
              setPriceOverride(newItem ? String(newItem.price) : '');
            }}>
            <option value="">-- Select Specification --</option>
            {specOptions.map((s) => {
              const label = selectedType === 'VIAL' ? `${s} ml` : `${s} mg (10 Tablets)`;
              return <option key={s} value={String(s)}>{label}</option>;
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

        {/* Price per Unit — only shown when an item is selected */}
        {selectedItem && (
          <div className="form-group">
            <label htmlFor="price-input">Price per Unit (Rs)</label>
            <input
              id="price-input"
              type="number"
              min="0"
              value={priceOverride}
              onChange={(e) => setPriceOverride(e.target.value)}
            />
          </div>
        )}

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

        {/* Screenshot upload — mandatory */}
        <ScreenshotUpload
          screenshots={screenshot.screenshots}
          canAddMore={screenshot.canAddMore}
          fileInputRef={screenshot.fileInputRef}
          onAdd={screenshot.addScreenshot}
          onRemove={screenshot.removeScreenshot}
          required
        />

        {/* Preview — delegated to component */}
        <TransactionPreview
          item={selectedItem}
          quantity={quantity}
          notes={notes}
          screenshotCount={validScreenshots.length}
          pricePerUnit={priceOverride !== '' ? parseInt(priceOverride, 10) : undefined}
        />

        <button type="submit" disabled={!isFormValid || submitting} className="btn btn-primary">
          {submitting ? 'Submitting…' : 'Submit Inventory Adjustment'}
        </button>
      </form>
    </div>
  );
}
