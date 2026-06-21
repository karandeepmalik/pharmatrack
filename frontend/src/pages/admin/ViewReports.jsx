import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import * as api from '../../api/api';
import SalesGraphReport from './SalesGraphReport';

const REPORTS = [
    { value: '', label: '-- Select a Report --' },
    { value: 'inventory-by-user', label: 'Current Medicine Stock Per User' },
    { value: 'inventory-valuation', label: 'Medicine Stock Valuation' },
    { value: 'today-sales', label: 'Sales Report' },
    { value: 'daily', label: 'Daily Report' },
    { value: 'sales-graph', label: 'Sales Trend Graph' },
];

// Default date = today in YYYY-MM-DD
const todayStr = () => new Date().toISOString().slice(0, 10);

export default function ViewReports() {
    const [selected, setSelected] = useState('');
    const [dailyDate, setDailyDate] = useState(todayStr());
    const [valuationDate, setValuationDate] = useState(todayStr());
    const [salesFrom, setSalesFrom] = useState(todayStr());
    const [salesTo, setSalesTo] = useState(todayStr());
    const [content, setContent] = useState('');
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');

    const handleGenerate = async () => {
        if (!selected) return;
        setLoading(true);
        setError('');
        setContent('');
        try {
            let res;
            if (selected === 'inventory-by-user') res = await api.getReportInventoryByUser();
            else if (selected === 'inventory-valuation') res = await api.getReportInventoryValuation(valuationDate || null);
            else if (selected === 'today-sales') res = await api.getReportTodaySales(salesFrom, salesTo);
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

    return (
        <div className="page">
            <div className="page-header">
                <h1>View Reports</h1>
                <Link to="/admin/dashboard" className="btn btn-secondary">← Back</Link>
            </div>

            {error && !isSalesGraph && <div role="alert" className="alert alert-error">{error}</div>}

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

                {/* Standard text-report controls */}
                {!isSalesGraph && (
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

                        {selected === 'today-sales' && salesFrom > salesTo && (
                            <p className="form-error" role="alert">
                                "From" date must be before or equal to "To" date.
                            </p>
                        )}

                        {selected === 'inventory-valuation' && (
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

            {content && !isSalesGraph && (
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
