import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import * as api from '../../api/api';
import SalesGraphReport from './SalesGraphReport';
import InventoryProjectionReport from './InventoryProjectionReport';
import StockRunwayReport from './StockRunwayReport';
import MultiSelectDropdown from '../../components/MultiSelectDropdown';
import { medicineOptionLabel } from '../../constants';

const REPORTS = [
    { value: '', label: '-- Select a Report --' },
    { value: 'medicine-stock-by-user', label: 'Current Medicine Stock Per User' },
    { value: 'medicine-stock-valuation', label: 'Medicine Stock Valuation' },
    { value: 'today-sales', label: 'Sales Report' },
    { value: 'daily', label: 'Daily Report' },
    { value: 'sales-graph', label: 'Sales Trend Graph' },
    { value: 'inventory-projection', label: 'Future Inventory Projections' },
    { value: 'stock-runway', label: 'Stock Runway (Till When Will Current Stock Last)' },
];

// Default date = today in YYYY-MM-DD
const todayStr = () => new Date().toISOString().slice(0, 10);

export default function ViewReports() {
    const [selected, setSelected] = useState('');
    const [dailyDate, setDailyDate] = useState(todayStr());
    const [valuationDate, setValuationDate] = useState(todayStr());
    const [salesFrom, setSalesFrom] = useState(todayStr());
    const [salesTo, setSalesTo] = useState(todayStr());
    // Empty array means "no filter" (every page's existing "ALL" convention, applied to the
    // multi-select equivalent).
    const [salesUserFilter, setSalesUserFilter] = useState([]);
    const [salesMedicineFilter, setSalesMedicineFilter] = useState([]);
    const [users, setUsers] = useState([]);
    const [medicines, setMedicines] = useState([]);
    const [content, setContent] = useState('');
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');

    useEffect(() => {
        api.getUsers().then(r => setUsers(
            (r.data || []).filter(u => u.role !== 'ADMIN')
        )).catch(() => {});
        api.getMedicines().then(r => setMedicines(r.data || [])).catch(() => {});
    }, []);

    const handleGenerate = async () => {
        if (!selected) return;
        setLoading(true);
        setError('');
        setContent('');
        try {
            let res;
            if (selected === 'medicine-stock-by-user') res = await api.getReportMedicineStockByUser();
            else if (selected === 'medicine-stock-valuation') res = await api.getReportMedicineStockValuation(valuationDate || null);
            else if (selected === 'today-sales') res = await api.getReportTodaySales(
                salesFrom, salesTo,
                salesUserFilter.length ? salesUserFilter : undefined,
                salesMedicineFilter.length ? salesMedicineFilter : undefined
            );
            else if (selected === 'daily') res = await api.getReportDaily(dailyDate || null);
            setContent(res.data.content);
        } catch {
            setError('Failed to generate report. Please try again.');
        } finally {
            setLoading(false);
        }
    };

    const handleCopy = () => {
        if (!content) return;
        navigator.clipboard.writeText(content).catch(() => {
            const ta = document.createElement('textarea');
            ta.value = content;
            document.body.appendChild(ta);
            ta.select();
            document.execCommand('copy');
            document.body.removeChild(ta);
        });
    };

    const salesDateValid = salesFrom && salesTo && salesFrom <= salesTo;
    const isSalesGraph = selected === 'sales-graph';
    const isInventoryProjection = selected === 'inventory-projection';
    const isStockRunway = selected === 'stock-runway';
    const isSelfRenderedReport = isSalesGraph || isInventoryProjection || isStockRunway;

    return (
        <div className="page">
            <div className="page-header">
                <h1>View Reports</h1>
                <Link to="/admin/dashboard" className="btn btn-secondary">← Back</Link>
            </div>

            {error && !isSelfRenderedReport && <div role="alert" className="alert alert-error">{error}</div>}

            <div className="form-card">
                <div className="form-group">
                    <label htmlFor="report-select">Select Report</label>
                    <select
                        id="report-select"
                        value={selected}
                        onChange={e => { setSelected(e.target.value); setContent(''); setError(''); }}>
                        {REPORTS.map(r => (
                            <option key={r.value} value={r.value}>{r.label}</option>
                        ))}
                    </select>
                </div>

                {/* Sales Trend Graph — renders its own controls inline */}
                {isSalesGraph && <SalesGraphReport />}

                {/* Future Inventory Projections — renders its own controls inline */}
                {isInventoryProjection && <InventoryProjectionReport users={users} />}

                {/* Stock Runway — renders its own controls inline */}
                {isStockRunway && <StockRunwayReport users={users} />}

                {/* Standard text-report controls */}
                {!isSelfRenderedReport && (
                    <>
                        {selected === 'today-sales' && (
                            <div className="form-row">
                                <div className="form-group">
                                    <label htmlFor="sales-from-input">From Date</label>
                                    <input
                                        id="sales-from-input"
                                        type="date"
                                        value={salesFrom}
                                        onChange={e => { setSalesFrom(e.target.value); setContent(''); }}
                                    />
                                </div>
                                <div className="form-group">
                                    <label htmlFor="sales-to-input">To Date</label>
                                    <input
                                        id="sales-to-input"
                                        type="date"
                                        value={salesTo}
                                        onChange={e => { setSalesTo(e.target.value); setContent(''); }}
                                    />
                                </div>
                            </div>
                        )}

                        {selected === 'today-sales' && (
                            <div className="form-row">
                                <MultiSelectDropdown
                                    id="sales-user-filter"
                                    label="User"
                                    allLabel="All Users"
                                    options={users.map(u => ({ value: u.username, label: `${u.fullName} (${u.username})` }))}
                                    selected={salesUserFilter}
                                    onChange={v => { setSalesUserFilter(v); setContent(''); }}
                                />
                                <MultiSelectDropdown
                                    id="sales-medicine-filter"
                                    label="Medicine Spec"
                                    allLabel="All Medicines"
                                    options={medicines.map(m => ({ value: String(m.id), label: medicineOptionLabel(m) }))}
                                    selected={salesMedicineFilter}
                                    onChange={v => { setSalesMedicineFilter(v); setContent(''); }}
                                />
                            </div>
                        )}

                        {selected === 'today-sales' && salesFrom > salesTo && (
                            <p className="form-error" role="alert">
                                "From" date must be before or equal to "To" date.
                            </p>
                        )}

                        {selected === 'medicine-stock-valuation' && (
                            <div className="form-group">
                                <label htmlFor="valuation-date-input">As of Date</label>
                                <input
                                    id="valuation-date-input"
                                    type="date"
                                    value={valuationDate}
                                    onChange={e => { setValuationDate(e.target.value); setContent(''); }}
                                />
                            </div>
                        )}

                        {selected === 'daily' && (
                            <div className="form-group">
                                <label htmlFor="daily-date-input">Report Date</label>
                                <input
                                    id="daily-date-input"
                                    type="date"
                                    value={dailyDate}
                                    onChange={e => { setDailyDate(e.target.value); setContent(''); }}
                                />
                            </div>
                        )}

                        <button
                            type="button"
                            className="btn btn-primary"
                            disabled={!selected || loading || (selected === 'today-sales' && !salesDateValid)}
                            onClick={handleGenerate}>
                            {loading ? 'Generating…' : 'Generate Report'}
                        </button>
                    </>
                )}
            </div>

            {content && !isSelfRenderedReport && (
                <div className="report-section">
                    <div className="report-header">
                        <h2>{REPORTS.find(r => r.value === selected)?.label}</h2>
                        <button
                            type="button"
                            className="btn btn-secondary btn-sm"
                            onClick={handleCopy}>
                            Copy to Clipboard
                        </button>
                    </div>
                    <pre className="report-content">{content}</pre>
                </div>
            )}
        </div>
    );
}
