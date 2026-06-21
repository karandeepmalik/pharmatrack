import React, { useState, useEffect } from 'react';
import * as api from '../../api/api';

const PERIODS = [
    { value: 'daily', label: 'Daily', daysBack: 29 },
    { value: 'weekly', label: 'Weekly', daysBack: 83 },
    { value: 'monthly', label: 'Monthly', daysBack: 364 },
];

const METRICS = [
    { value: 'quantity', label: 'Quantity' },
    { value: 'value', label: 'Value (Rs)' },
];

const todayStr = () => new Date().toISOString().slice(0, 10);

function daysAgo(n) {
    const d = new Date();
    d.setDate(d.getDate() - n);
    return d.toISOString().slice(0, 10);
}

// SVG chart constants
const W = 800, H = 380;
const PAD = { top: 20, right: 20, bottom: 70, left: 75 };
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

function BarChart({ dataPoints, metric }) {
    const [tooltip, setTooltip] = useState(null);

    const values = dataPoints.map(d => metric === 'quantity' ? d.quantity : Number(d.value));
    const maxVal = Math.max(...values, 0);
    const yMax = niceMax(maxVal);

    const N = dataPoints.length;
    const slotW = N > 0 ? CW / N : CW;
    const barW = Math.max(slotW * 0.62, 2);
    const barGap = (slotW - barW) / 2;

    const TICKS = 5;
    const yTicks = Array.from({ length: TICKS + 1 }, (_, i) => Math.round((yMax * i) / TICKS));

    const rotateLabels = N > 14;

    return (
        <svg
            viewBox={`0 0 ${W} ${H}`}
            width="100%"
            style={{ display: 'block', overflow: 'visible' }}
            aria-label="Sales bar chart"
        >
            <defs>
                <filter id="sg-shadow" x="-10%" y="-10%" width="130%" height="130%">
                    <feDropShadow dx="0" dy="1" stdDeviation="2" floodOpacity="0.12" />
                </filter>
            </defs>

            {/* Y-axis gridlines + labels */}
            {yTicks.map((tick) => {
                const y = PAD.top + CH - (yMax > 0 ? (tick / yMax) * CH : 0);
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

            {/* Y-axis label */}
            <text
                x={14}
                y={PAD.top + CH / 2}
                textAnchor="middle"
                fontSize={11}
                fill="#6b7280"
                transform={`rotate(-90, 14, ${PAD.top + CH / 2})`}
            >
                {metric === 'quantity' ? 'Units Sold' : 'Value (Rs)'}
            </text>

            {/* Bars + X labels */}
            {dataPoints.map((d, i) => {
                const val = metric === 'quantity' ? d.quantity : Number(d.value);
                const barH = yMax > 0 ? (val / yMax) * CH : 0;
                const x = PAD.left + i * slotW + barGap;
                const y = PAD.top + CH - barH;
                const lx = PAD.left + (i + 0.5) * slotW;
                const ly = PAD.top + CH + 13;
                const isHovered = tooltip?.i === i;

                return (
                    <g
                        key={i}
                        onMouseEnter={() => setTooltip({ i, lx, y, d })}
                        onMouseLeave={() => setTooltip(null)}
                        style={{ cursor: 'default' }}
                    >
                        <rect
                            x={x} y={y}
                            width={barW} height={Math.max(barH, 0)}
                            fill={isHovered ? '#1d4ed8' : '#3b82f6'}
                            rx={barW > 6 ? 3 : 1}
                        />
                        {rotateLabels ? (
                            <text x={lx} y={ly + 4} textAnchor="end" fontSize={10} fill="#6b7280"
                                transform={`rotate(-45, ${lx}, ${ly})`}>
                                {d.label}
                            </text>
                        ) : (
                            <text x={lx} y={ly} textAnchor="middle" fontSize={10} fill="#6b7280">
                                {d.label}
                            </text>
                        )}
                    </g>
                );
            })}

            {/* Tooltip */}
            {tooltip && (() => {
                const d = tooltip.d;
                const tx = Math.min(Math.max(tooltip.lx - 65, PAD.left), W - 145);
                const ty = Math.max(tooltip.y - 68, PAD.top);
                return (
                    <g pointerEvents="none">
                        <rect x={tx} y={ty} width={140} height={56}
                            fill="white" stroke="#d1d5db" rx={5}
                            filter="url(#sg-shadow)" />
                        <text x={tx + 10} y={ty + 17} fontSize={12} fontWeight="600" fill="#111827">
                            {d.label}
                        </text>
                        <text x={tx + 10} y={ty + 33} fontSize={11} fill="#374151">
                            Qty: {d.quantity.toLocaleString()}
                        </text>
                        <text x={tx + 10} y={ty + 49} fontSize={11} fill="#374151">
                            Rs {Number(d.value).toLocaleString()}
                        </text>
                    </g>
                );
            })()}
        </svg>
    );
}

export default function SalesGraphReport() {
    const [period, setPeriod] = useState('daily');
    const [metric, setMetric] = useState('quantity');
    const [from, setFrom] = useState(daysAgo(29));
    const [to, setTo] = useState(todayStr());
    const [data, setData] = useState(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');

    useEffect(() => {
        const p = PERIODS.find(p => p.value === period);
        setFrom(daysAgo(p.daysBack));
        setTo(todayStr());
    }, [period]);

    useEffect(() => {
        if (!from || !to || from > to) return;
        setLoading(true);
        setError('');
        setData(null);
        api.getReportSalesGraph(period, from, to)
            .then(res => setData(res.data))
            .catch(() => setError('Failed to load sales data. Please try again.'))
            .finally(() => setLoading(false));
    }, [period, from, to]);

    const totalQty = data?.dataPoints?.reduce((s, d) => s + d.quantity, 0) ?? 0;
    const totalVal = data?.dataPoints?.reduce((s, d) => s + Number(d.value), 0) ?? 0;
    const dateError = from && to && from > to;

    return (
        <div>
            {/* Period + metric toggles */}
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px', alignItems: 'center', marginBottom: '16px' }}>
                <span style={{ fontSize: '13px', color: '#6b7280', fontWeight: 500 }}>Period:</span>
                <div className="btn-group">
                    {PERIODS.map(p => (
                        <button
                            key={p.value}
                            type="button"
                            className={`btn btn-sm ${period === p.value ? 'btn-primary' : 'btn-secondary'}`}
                            onClick={() => setPeriod(p.value)}
                        >
                            {p.label}
                        </button>
                    ))}
                </div>
                <span style={{ fontSize: '13px', color: '#6b7280', fontWeight: 500, marginLeft: '12px' }}>Show:</span>
                <div className="btn-group">
                    {METRICS.map(m => (
                        <button
                            key={m.value}
                            type="button"
                            className={`btn btn-sm ${metric === m.value ? 'btn-primary' : 'btn-secondary'}`}
                            onClick={() => setMetric(m.value)}
                        >
                            {m.label}
                        </button>
                    ))}
                </div>
            </div>

            {/* Date range */}
            <div className="form-row" style={{ marginBottom: '12px' }}>
                <div className="form-group">
                    <label htmlFor="sg-from">From</label>
                    <input
                        id="sg-from"
                        type="date"
                        value={from}
                        onChange={e => setFrom(e.target.value)}
                    />
                </div>
                <div className="form-group">
                    <label htmlFor="sg-to">To</label>
                    <input
                        id="sg-to"
                        type="date"
                        value={to}
                        onChange={e => setTo(e.target.value)}
                    />
                </div>
            </div>
            {dateError && (
                <p className="form-error" role="alert" style={{ marginBottom: '12px' }}>
                    "From" date must be before or equal to "To" date.
                </p>
            )}

            {/* Summary cards */}
            {data && !loading && (
                <div style={{ display: 'flex', gap: '16px', flexWrap: 'wrap', marginBottom: '16px' }}>
                    <div style={{ background: '#eff6ff', borderRadius: '8px', padding: '12px 20px', minWidth: '140px' }}>
                        <div style={{ fontSize: '11px', fontWeight: 600, color: '#3b82f6', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                            Total Units
                        </div>
                        <div style={{ fontSize: '24px', fontWeight: 700, color: '#1e40af', marginTop: '2px' }}>
                            {totalQty.toLocaleString()}
                        </div>
                    </div>
                    <div style={{ background: '#f0fdf4', borderRadius: '8px', padding: '12px 20px', minWidth: '140px' }}>
                        <div style={{ fontSize: '11px', fontWeight: 600, color: '#16a34a', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                            Total Value
                        </div>
                        <div style={{ fontSize: '24px', fontWeight: 700, color: '#15803d', marginTop: '2px' }}>
                            Rs {totalVal.toLocaleString()}
                        </div>
                    </div>
                </div>
            )}

            {/* Chart area */}
            {loading && <p style={{ color: '#6b7280', padding: '24px 0' }}>Loading chart…</p>}
            {error && <div className="alert alert-error" role="alert">{error}</div>}
            {data && !loading && data.dataPoints.length === 0 && (
                <p style={{ color: '#6b7280', textAlign: 'center', padding: '48px 0', fontSize: '15px' }}>
                    No approved sales found in this period.
                </p>
            )}
            {data && !loading && data.dataPoints.length > 0 && (
                <div style={{
                    background: '#fff',
                    border: '1px solid #e5e7eb',
                    borderRadius: '10px',
                    padding: '16px 8px 8px',
                }}>
                    <BarChart dataPoints={data.dataPoints} metric={metric} />
                </div>
            )}
        </div>
    );
}
