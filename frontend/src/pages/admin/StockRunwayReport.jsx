import React, { useState, useMemo, useCallback } from 'react';
import * as api from '../../api/api';
import { trackEvent } from '../../telemetry';

const ORAL_UNIT_OPTIONS = [
    { value: 'STRIPS', label: 'Strips' },
    { value: 'MG', label: 'mg' },
];
const TABLET_REF_OPTIONS = [20, 30, 50];
const DEFAULT_TABLET_REF_MG = 30;

const VIAL_REF_OPTIONS = [8, 10];
const DEFAULT_VIAL_REF_ML = 8;

const PAGE = '/admin/reports';

const LINE_COLORS = { 'Total Oral': '#3b82f6', 'Total Injectable': '#f59e0b' };

// SVG chart constants
const W = 760, H = 260;
const PAD = { top: 20, right: 20, bottom: 40, left: 65 };
const CW = W - PAD.left - PAD.right;
const CH = H - PAD.top - PAD.bottom;

function niceMax(val) {
    if (val === 0) return 10;
    const mag = Math.pow(10, Math.floor(Math.log10(val)));
    const n = val / mag;
    const nice = n <= 1 ? 1 : n <= 2 ? 2 : n <= 5 ? 5 : 10;
    return nice * mag;
}

function fmtY(v) {
    if (v >= 10000000) return (v / 10000000).toFixed(1) + 'Cr';
    if (v >= 100000) return (v / 100000).toFixed(1) + 'L';
    if (v >= 1000) return (v / 1000).toFixed(0) + 'K';
    return String(v);
}

function RunwayChart({ dateLabels, remainingQuantity, color, unit }) {
    const [tooltip, setTooltip] = useState(null);

    const n = dateLabels.length;
    const values = remainingQuantity.map(Number);
    const yMax = niceMax(Math.max(...values, 0));

    const xStep = n > 1 ? CW / (n - 1) : 0;
    const xAt = (i) => PAD.left + i * xStep;
    const yAt = (v) => PAD.top + CH - (yMax > 0 ? (v / yMax) * CH : 0);

    const TICKS = 5;
    const yTicks = Array.from({ length: TICKS + 1 }, (_, i) => Math.round((yMax * i) / TICKS));
    const labelStep = Math.max(1, Math.ceil(n / 8));

    const points = values.map((v, i) => [xAt(i), yAt(v)]);
    const linePoints = points.map(p => p.join(',')).join(' ');
    const areaPoints = [...points, [xAt(n - 1), yAt(0)], [xAt(0), yAt(0)]].map(p => p.join(',')).join(' ');

    return (
        <svg
            viewBox={`0 0 ${W} ${H}`}
            width="100%"
            style={{ display: 'block', overflow: 'visible' }}
            aria-label="Receding stock chart"
        >
            {yTicks.map((tick) => {
                const y = yAt(tick);
                return (
                    <g key={tick}>
                        <line x1={PAD.left} y1={y} x2={PAD.left + CW} y2={y}
                            stroke={tick === 0 ? '#d1d5db' : '#e5e7eb'} strokeWidth={1} />
                        <text x={PAD.left - 7} y={y + 4} textAnchor="end" fontSize={11} fill="#6b7280">
                            {fmtY(tick)}
                        </text>
                    </g>
                );
            })}

            <polygon points={areaPoints} fill={color} fillOpacity={0.12} stroke="none" />
            <polyline points={linePoints} fill="none" stroke={color} strokeWidth={2} />

            {dateLabels.map((label, i) => {
                if (i % labelStep !== 0 && i !== n - 1) return null;
                return (
                    <text key={i} x={xAt(i)} y={PAD.top + CH + 18} textAnchor="middle" fontSize={10} fill="#6b7280">
                        {label}
                    </text>
                );
            })}

            {points.map(([x, y], i) => (
                <circle
                    key={i} cx={x} cy={y} r={3} fill={color}
                    onMouseEnter={() => setTooltip({ x, y, label: dateLabels[i], value: values[i] })}
                    onMouseLeave={() => setTooltip(null)}
                    style={{ cursor: 'pointer' }}
                />
            ))}

            {tooltip && (() => {
                const tx = Math.min(Math.max(tooltip.x - 70, PAD.left), W - 150);
                const ty = Math.max(tooltip.y - 46, PAD.top);
                return (
                    <g pointerEvents="none">
                        <rect x={tx} y={ty} width={145} height={38} fill="white" stroke="#d1d5db" rx={5} />
                        <text x={tx + 8} y={ty + 16} fontSize={11} fontWeight="600" fill="#111827">{tooltip.label}</text>
                        <text x={tx + 8} y={ty + 30} fontSize={10} fill="#374151">{tooltip.value.toFixed(1)} {unit}</text>
                    </g>
                );
            })()}
        </svg>
    );
}

function daysUntil(dateStr) {
    if (!dateStr) return null;
    const ms = new Date(dateStr + 'T00:00:00') - new Date(new Date().toDateString());
    return Math.round(ms / 86400000);
}

export default function StockRunwayReport({ users }) {
    const [city, setCity] = useState('');
    const [oralStock, setOralStock] = useState('');
    const [oralUnit, setOralUnit] = useState('STRIPS');
    const [oralRefMg, setOralRefMg] = useState(DEFAULT_TABLET_REF_MG);
    const [vialStock, setVialStock] = useState('');
    const [vialRefMl, setVialRefMl] = useState(DEFAULT_VIAL_REF_ML);
    const [data, setData] = useState(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');

    const cityOptions = useMemo(() => {
        const set = new Set();
        (users || []).forEach(u => {
            if (u.location && u.location.trim()) set.add(u.location.trim());
        });
        return Array.from(set).sort();
    }, [users]);

    const oralNum = oralStock === '' ? null : Number(oralStock);
    const vialNum = vialStock === '' ? null : Number(vialStock);
    const hasValidInput = (oralNum > 0) || (vialNum > 0);

    const handleGenerate = useCallback(async () => {
        setLoading(true);
        setError('');
        setData(null);
        try {
            const res = await api.getReportStockRunway(
                city || undefined,
                oralNum > 0 ? oralNum : undefined,
                oralUnit,
                oralUnit === 'STRIPS' ? oralRefMg : undefined,
                vialNum > 0 ? vialNum : undefined,
                vialNum > 0 ? vialRefMl : undefined,
            );
            setData(res.data);
            trackEvent('stock_runway_generated', PAGE, {
                city: city || 'Overall',
                hasOral: oralNum > 0,
                hasVial: vialNum > 0,
                lines: res.data.lines?.length ?? 0,
            });
        } catch {
            setError('Failed to generate stock runway. Please try again.');
            trackEvent('stock_runway_error', PAGE, {});
        } finally {
            setLoading(false);
        }
    }, [city, oralNum, oralUnit, oralRefMg, vialNum, vialRefMl]);

    return (
        <div>
            <div className="form-group">
                <label htmlFor="runway-city-select">City</label>
                <select
                    id="runway-city-select"
                    value={city}
                    onChange={e => { setCity(e.target.value); setData(null); }}
                >
                    <option value="">Overall (every city)</option>
                    {cityOptions.map(c => (
                        <option key={c} value={c}>{c}</option>
                    ))}
                </select>
            </div>

            <div className="form-row">
                <div className="form-group">
                    <label htmlFor="runway-oral-stock">Oral Stock on Hand</label>
                    <input
                        id="runway-oral-stock"
                        type="number"
                        min="0"
                        step="0.1"
                        value={oralStock}
                        onChange={e => { setOralStock(e.target.value); setData(null); }}
                        placeholder="e.g. 500"
                    />
                </div>
                <div className="form-group">
                    <label htmlFor="runway-oral-unit">Oral Unit</label>
                    <select
                        id="runway-oral-unit"
                        value={oralUnit}
                        onChange={e => { setOralUnit(e.target.value); setData(null); }}
                    >
                        {ORAL_UNIT_OPTIONS.map(u => (
                            <option key={u.value} value={u.value}>{u.label}</option>
                        ))}
                    </select>
                </div>
                {oralUnit === 'STRIPS' && (
                    <div className="form-group">
                        <label htmlFor="runway-oral-ref">Strip Strength</label>
                        <select
                            id="runway-oral-ref"
                            value={oralRefMg}
                            onChange={e => { setOralRefMg(Number(e.target.value)); setData(null); }}
                        >
                            {TABLET_REF_OPTIONS.map(mg => (
                                <option key={mg} value={mg}>{mg}mg</option>
                            ))}
                        </select>
                    </div>
                )}
            </div>

            <div className="form-row">
                <div className="form-group">
                    <label htmlFor="runway-vial-stock">Injection Vial Stock on Hand (number)</label>
                    <input
                        id="runway-vial-stock"
                        type="number"
                        min="0"
                        step="0.1"
                        value={vialStock}
                        onChange={e => { setVialStock(e.target.value); setData(null); }}
                        placeholder="e.g. 20"
                    />
                </div>
                <div className="form-group">
                    <label htmlFor="runway-vial-ref">Vial Volume</label>
                    <select
                        id="runway-vial-ref"
                        value={vialRefMl}
                        onChange={e => { setVialRefMl(Number(e.target.value)); setData(null); }}
                    >
                        {VIAL_REF_OPTIONS.map(ml => (
                            <option key={ml} value={ml}>{ml}ml</option>
                        ))}
                    </select>
                </div>
            </div>

            <button
                type="button"
                className="btn btn-primary"
                disabled={!hasValidInput || loading}
                onClick={handleGenerate}
            >
                {loading ? 'Generating…' : 'Generate Stock Runway'}
            </button>

            {error && <div role="alert" className="alert alert-error" style={{ marginTop: '16px' }}>{error}</div>}

            {data && !loading && data.lines.length === 0 && (
                <p style={{ color: '#6b7280', textAlign: 'center', padding: '32px 0', fontSize: '15px' }}>
                    Enter a stock value above and press Generate.
                </p>
            )}

            {data && !loading && data.lines.length > 0 && (
                <div style={{ marginTop: '20px' }}>
                    <div className="table-wrapper" style={{ marginBottom: '20px' }}>
                        <table className="data-table">
                            <thead>
                                <tr>
                                    <th>Type</th>
                                    <th>Current Stock</th>
                                    <th>Projected Stock-Out Date</th>
                                    <th>Days Remaining</th>
                                </tr>
                            </thead>
                            <tbody>
                                {data.lines.map(line => {
                                    const days = daysUntil(line.stockOutDate);
                                    return (
                                        <tr key={line.name}>
                                            <td>{line.name}</td>
                                            <td>{Number(line.currentStock).toFixed(1)} {line.unit}</td>
                                            <td>
                                                {line.insufficientData
                                                    ? 'No sales history for this selection'
                                                    : line.stockOutDate
                                                        ? line.stockOutDate
                                                        : "Doesn't run out within 10 years at this trend"}
                                            </td>
                                            <td>{days != null ? days : '—'}</td>
                                        </tr>
                                    );
                                })}
                            </tbody>
                        </table>
                    </div>

                    {data.lines.map(line => (
                        <div key={line.name} style={{
                            background: '#fff', border: '1px solid #e5e7eb', borderRadius: '10px',
                            padding: '16px', marginBottom: '16px',
                        }}>
                            <h3 style={{ fontSize: '14px', margin: '0 0 8px', color: '#374151' }}>
                                {line.name} — Receding Stock ({line.unit})
                            </h3>
                            {line.insufficientData && (
                                <p style={{ fontSize: '12px', color: '#9a6700', marginBottom: '8px' }}>
                                    No sales history found for this selection — the chart below assumes
                                    zero consumption and can't be trusted to predict a real stock-out date.
                                </p>
                            )}
                            <RunwayChart
                                dateLabels={line.dateLabels}
                                remainingQuantity={line.remainingQuantity}
                                color={LINE_COLORS[line.name] ?? '#6366f1'}
                                unit={line.unit}
                            />
                        </div>
                    ))}
                </div>
            )}
        </div>
    );
}
