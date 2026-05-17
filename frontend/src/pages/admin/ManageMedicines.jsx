import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import * as api from '../../api/api';

export default function ManageMedicines() {
  // ── Pharma companies ──────────────────────────────────────────────────
  const [companies, setCompanies]         = useState([]);
  const [medicines, setMedicines]         = useState([]);

  // ── Add Pharma Company form ───────────────────────────────────────────
  const [companyName, setCompanyName]     = useState('');
  const [companyDesc, setCompanyDesc]     = useState('');
  const [companySuccess, setCompanySuccess] = useState('');
  const [companyError, setCompanyError]   = useState('');
  const [savingCompany, setSavingCompany] = useState(false);

  // ── Add Medicine form ─────────────────────────────────────────────────
  const [medPharmaId, setMedPharmaId]         = useState('');
  const [medName, setMedName]                 = useState('');
  const [medType, setMedType]                 = useState('');
  const [medSpec, setMedSpec]                 = useState('');
  const [medConc, setMedConc]                 = useState('');
  const [medPrice, setMedPrice]               = useState('');
  const [medSuccess, setMedSuccess]           = useState('');
  const [medError, setMedError]               = useState('');
  const [savingMedicine, setSavingMedicine]   = useState(false);

  useEffect(() => {
    loadData();
  }, []);

  const loadData = () => {
    api.getMedicines().then((r) => setMedicines(r.data)).catch(() => {});
    api.getPharmaCompanies().then((r) => setCompanies(r.data)).catch(() => {});
  };

  // ── Handlers ─────────────────────────────────────────────────────────

  const handleAddCompany = async (e) => {
    e.preventDefault();
    setSavingCompany(true);
    setCompanyError('');
    setCompanySuccess('');
    try {
      await api.createPharmaCompany({ name: companyName.trim(), description: companyDesc.trim() });
      setCompanySuccess(`Pharma company "${companyName.trim()}" created successfully.`);
      setCompanyName('');
      setCompanyDesc('');
      loadData();
    } catch (err) {
      setCompanyError(err.response?.data?.message || 'Failed to create pharma company.');
    } finally {
      setSavingCompany(false);
    }
  };

  const handleAddMedicine = async (e) => {
    e.preventDefault();
    setSavingMedicine(true);
    setMedError('');
    setMedSuccess('');
    try {
      const payload = {
        pharmaCompanyId: Number(medPharmaId),
        name: medName.trim(),
        type: medType,
        specification: Number(medSpec),
        price: Number(medPrice),
      };
      if (medConc.trim()) payload.concentrationMgPerMl = Number(medConc);
      await api.createMedicine(payload);
      setMedSuccess(`Medicine "${medName.trim()}" created successfully.`);
      setMedPharmaId(''); setMedName(''); setMedType('');
      setMedSpec(''); setMedConc(''); setMedPrice('');
      loadData();
    } catch (err) {
      setMedError(err.response?.data?.message || 'Failed to create medicine.');
    } finally {
      setSavingMedicine(false);
    }
  };

  const isMedicineFormValid =
    Boolean(medPharmaId) && Boolean(medName.trim()) && Boolean(medType) &&
    Boolean(medSpec) && Boolean(medPrice);

  // ── Render ────────────────────────────────────────────────────────────
  return (
    <div className="page manage-medicines-page">
      <div className="page-header">
        <h1>Manage Medicines</h1>
        <Link to="/admin/dashboard" className="btn btn-secondary">← Back</Link>
      </div>

      {/* ── Add Pharma Company ──────────────────────────────────────── */}
      <section aria-labelledby="add-company-heading">
        <h2 id="add-company-heading">Add Pharma Company</h2>

        {companySuccess && (
          <div role="alert" className="alert alert-success">{companySuccess}</div>
        )}
        {companyError && (
          <div role="alert" className="alert alert-error">{companyError}</div>
        )}

        <form onSubmit={handleAddCompany} noValidate>
          <div className="form-group">
            <label htmlFor="company-name-input">Company Name <span className="required">*</span></label>
            <input
              id="company-name-input"
              type="text"
              value={companyName}
              onChange={(e) => setCompanyName(e.target.value)}
              placeholder="e.g. Shield FX"
              required
            />
          </div>
          <div className="form-group">
            <label htmlFor="company-desc-input">Description</label>
            <input
              id="company-desc-input"
              type="text"
              value={companyDesc}
              onChange={(e) => setCompanyDesc(e.target.value)}
              placeholder="Optional description"
            />
          </div>
          <button
            type="submit"
            disabled={!companyName.trim() || savingCompany}
            className="btn btn-primary"
          >
            {savingCompany ? 'Saving…' : 'Add Pharma Company'}
          </button>
        </form>
      </section>

      <hr />

      {/* ── Add Medicine ─────────────────────────────────────────────── */}
      <section aria-labelledby="add-medicine-heading">
        <h2 id="add-medicine-heading">Add Medicine</h2>

        {medSuccess && (
          <div role="alert" className="alert alert-success">{medSuccess}</div>
        )}
        {medError && (
          <div role="alert" className="alert alert-error">{medError}</div>
        )}

        <form onSubmit={handleAddMedicine} noValidate>
          <div className="form-group">
            <label htmlFor="med-pharma-select">Pharma Company <span className="required">*</span></label>
            <select
              id="med-pharma-select"
              value={medPharmaId}
              onChange={(e) => setMedPharmaId(e.target.value)}
              required
            >
              <option value="">-- Select Pharma --</option>
              {companies.map((c) => (
                <option key={c.id} value={String(c.id)}>{c.name}</option>
              ))}
            </select>
          </div>

          <div className="form-group">
            <label htmlFor="med-name-input">Medicine Name <span className="required">*</span></label>
            <input
              id="med-name-input"
              type="text"
              value={medName}
              onChange={(e) => setMedName(e.target.value)}
              placeholder="e.g. Shield FX Vial 10 ml"
              required
            />
          </div>

          <div className="form-group">
            <label htmlFor="med-type-select">Medicine Type <span className="required">*</span></label>
            <select
              id="med-type-select"
              value={medType}
              onChange={(e) => setMedType(e.target.value)}
              required
            >
              <option value="">-- Select Type --</option>
              <option value="VIAL">VIAL</option>
              <option value="TABLET">TABLET</option>
              <option value="CAPSULE">CAPSULE</option>
              <option value="SYRUP">SYRUP</option>
            </select>
          </div>

          <div className="form-group">
            <label htmlFor="med-spec-input">Specification <span className="required">*</span></label>
            <input
              id="med-spec-input"
              type="number"
              min="0"
              step="any"
              value={medSpec}
              onChange={(e) => setMedSpec(e.target.value)}
              placeholder="e.g. 10"
              required
            />
          </div>

          {medType === 'VIAL' && (
            <div className="form-group">
              <label htmlFor="med-conc-input">Concentration (mg/ml)</label>
              <input
                id="med-conc-input"
                type="number"
                min="0"
                step="any"
                value={medConc}
                onChange={(e) => setMedConc(e.target.value)}
                placeholder="e.g. 20 (optional)"
              />
            </div>
          )}

          <div className="form-group">
            <label htmlFor="med-price-input">Price (Rs) <span className="required">*</span></label>
            <input
              id="med-price-input"
              type="number"
              min="0"
              value={medPrice}
              onChange={(e) => setMedPrice(e.target.value)}
              placeholder="e.g. 4000"
              required
            />
          </div>

          <button
            type="submit"
            disabled={!isMedicineFormValid || savingMedicine}
            className="btn btn-primary"
          >
            {savingMedicine ? 'Saving…' : 'Add Medicine'}
          </button>
        </form>
      </section>

      <hr />

      {/* ── Existing Medicines Table ──────────────────────────────────── */}
      <section aria-labelledby="medicines-table-heading">
        <h2 id="medicines-table-heading">Existing Medicines</h2>
        {medicines.length === 0 ? (
          <p>No medicines found.</p>
        ) : (
          <div className="table-wrapper">
            <table className="data-table">
              <thead>
                <tr>
                  <th>ID</th>
                  <th>Name</th>
                  <th>Type</th>
                  <th>Specification</th>
                  <th>Concentration (mg/ml)</th>
                  <th>Price (Rs)</th>
                  <th>Pharma Company</th>
                </tr>
              </thead>
              <tbody>
                {medicines.map((m) => (
                  <tr key={m.id}>
                    <td>{m.id}</td>
                    <td>{m.name}</td>
                    <td>{m.type}</td>
                    <td>{m.specification}</td>
                    <td>{m.concentrationMgPerMl ?? '—'}</td>
                    <td>{m.price}</td>
                    <td>{m.pharmaCompany?.name ?? '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  );
}
