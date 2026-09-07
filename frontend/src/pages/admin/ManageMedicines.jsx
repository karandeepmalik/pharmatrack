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

  // ── Edit Medicine (inline, per row) ─────────────────────────────────────
  // { [id]: { active, pharmaCompanyId, name, type, specification, concentrationMgPerMl, price, saving, error } }
  const [editState, setEditState] = useState({});

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

  // ── Edit Medicine handlers ───────────────────────────────────────────

  const startEditMedicine = (m) => {
    setEditState(prev => ({
      ...prev,
      [m.id]: {
        active: true,
        pharmaCompanyId: String(m.pharmaCompany?.id ?? ''),
        name: m.name,
        type: m.type,
        specification: String(m.specification ?? ''),
        concentrationMgPerMl: m.concentrationMgPerMl != null ? String(m.concentrationMgPerMl) : '',
        price: String(m.price ?? ''),
        saving: false,
        error: '',
      },
    }));
  };

  const cancelEditMedicine = (id) => {
    setEditState(prev => ({ ...prev, [id]: { ...prev[id], active: false } }));
  };

  const handleEditFieldChange = (id, field, value) => {
    setEditState(prev => ({ ...prev, [id]: { ...prev[id], [field]: value } }));
  };

  const saveEditMedicine = async (id) => {
    const edit = editState[id] || {};
    setEditState(prev => ({ ...prev, [id]: { ...prev[id], saving: true, error: '' } }));
    try {
      const payload = {
        pharmaCompanyId: Number(edit.pharmaCompanyId),
        name: edit.name.trim(),
        type: edit.type,
        specification: Number(edit.specification),
        price: Number(edit.price),
      };
      if (edit.type === 'VIAL' && edit.concentrationMgPerMl.trim()) {
        payload.concentrationMgPerMl = Number(edit.concentrationMgPerMl);
      }
      await api.updateMedicine(id, payload);
      setEditState(prev => ({ ...prev, [id]: { active: false, saving: false, error: '' } }));
      loadData();
    } catch (err) {
      const msg = err.response?.data?.message || 'Failed to save changes.';
      setEditState(prev => ({ ...prev, [id]: { ...prev[id], saving: false, error: msg } }));
    }
  };

  const isEditValid = (edit) =>
    Boolean(edit.pharmaCompanyId) && Boolean(edit.name?.trim()) && Boolean(edit.type) &&
    Boolean(edit.specification) && Boolean(edit.price);

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
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {medicines.map((m) => {
                  const edit = editState[m.id] || {};
                  return (
                    <tr key={m.id}>
                      <td>{m.id}</td>
                      <td>
                        {edit.active ? (
                          <input
                            aria-label="Edit medicine name"
                            type="text"
                            value={edit.name}
                            onChange={(e) => handleEditFieldChange(m.id, 'name', e.target.value)}
                          />
                        ) : (
                          m.name
                        )}
                      </td>
                      <td>
                        {edit.active ? (
                          <select
                            aria-label="Edit medicine type"
                            value={edit.type}
                            onChange={(e) => handleEditFieldChange(m.id, 'type', e.target.value)}>
                            <option value="VIAL">VIAL</option>
                            <option value="TABLET">TABLET</option>
                            <option value="CAPSULE">CAPSULE</option>
                            <option value="SYRUP">SYRUP</option>
                          </select>
                        ) : (
                          m.type
                        )}
                      </td>
                      <td>
                        {edit.active ? (
                          <input
                            aria-label="Edit specification"
                            type="number"
                            min="0"
                            step="any"
                            value={edit.specification}
                            onChange={(e) => handleEditFieldChange(m.id, 'specification', e.target.value)}
                            style={{ width: '5rem' }}
                          />
                        ) : (
                          m.specification
                        )}
                      </td>
                      <td>
                        {edit.active ? (
                          edit.type === 'VIAL' && (
                            <input
                              aria-label="Edit concentration"
                              type="number"
                              min="0"
                              step="any"
                              value={edit.concentrationMgPerMl}
                              onChange={(e) => handleEditFieldChange(m.id, 'concentrationMgPerMl', e.target.value)}
                              style={{ width: '5rem' }}
                            />
                          )
                        ) : (
                          m.concentrationMgPerMl ?? '—'
                        )}
                      </td>
                      <td>
                        {edit.active ? (
                          <input
                            aria-label="Edit price"
                            type="number"
                            min="0"
                            value={edit.price}
                            onChange={(e) => handleEditFieldChange(m.id, 'price', e.target.value)}
                            style={{ width: '6rem' }}
                          />
                        ) : (
                          m.price
                        )}
                      </td>
                      <td>
                        {edit.active ? (
                          <select
                            aria-label="Edit pharma company"
                            value={edit.pharmaCompanyId}
                            onChange={(e) => handleEditFieldChange(m.id, 'pharmaCompanyId', e.target.value)}>
                            <option value="">-- Select Pharma --</option>
                            {companies.map((c) => (
                              <option key={c.id} value={String(c.id)}>{c.name}</option>
                            ))}
                          </select>
                        ) : (
                          m.pharmaCompany?.name ?? '—'
                        )}
                      </td>
                      <td className="actions-cell">
                        {edit.active ? (
                          <div>
                            {edit.error && (
                              <p role="alert" className="form-error">{edit.error}</p>
                            )}
                            <div className="btn-group">
                              <button
                                type="button"
                                className="btn btn-primary btn-sm"
                                disabled={edit.saving || !isEditValid(edit)}
                                onClick={() => saveEditMedicine(m.id)}>
                                {edit.saving ? 'Saving…' : 'Save'}
                              </button>
                              <button
                                type="button"
                                className="btn btn-secondary btn-sm"
                                disabled={edit.saving}
                                onClick={() => cancelEditMedicine(m.id)}>
                                Cancel
                              </button>
                            </div>
                          </div>
                        ) : (
                          <button
                            type="button"
                            className="btn btn-secondary btn-sm"
                            onClick={() => startEditMedicine(m)}>
                            Edit
                          </button>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  );
}
