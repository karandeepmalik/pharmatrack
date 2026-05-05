import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import * as api from '../../api/api';

const REPORTS = [
    { value: '', label: '-- Select a Report --' },
    { value: 'inventory-by-user', label: 'Current Inventory Level By User' },
    { value: 'inventory-valuation', label: 'Current Inventory Valuation' },
    { value: 'today-sales', label: "Today's Sales" },
    { value: 'daily', label: 'Daily Report' },
];

// Default date = today in YYYY-MM-DD
const todayStr = () => new Date().toISOString().slice(0, 10);

export default function ViewReports() {
    const [selected, setSelected] = useState('');
    const [dailyDate, setDailyDate] = useState(todayStr());
    const [salesDays, setSalesDays] = useState(1);
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
            else if (selected === 'inventory-valuation') res = await api.getReportInventoryValuation();
            else if (selected === 'today-sales') res = await api.getReportTodaySales(salesDays);
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

    return (
        <div className="page">
            <div className="page-header">
                <h1>View Reports</h1>
                <Link to="/admin/dashboard" className="btn btn-secondary">← Back</Link>
            </div>

            {error && <div role="alert" className="alert alert-error">{error}</div>}

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

                {selected === 'today-sales' && (
                    <div className="form-group">
                        <label htmlFor="sales-days-input">Time Period (days)</label>
                        <input
                            id="sales-days-input"
                            type="number"
                            min="1"
                            max="365"
                            value={salesDays}
                            onChange={e => { setSalesDays(Math.max(1, Number(e.target.value))); setContent(''); }}
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
                    disabled={!selected || loading}
                    onClick={handleGenerate}>
                    {loading ? 'Generating…' : 'Generate Report'}
                </button>
            </div>

            {content && (
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
