import React, { useState, useEffect, useCallback } from 'react';
import * as api from '../../api/api';
import { trackEvent } from '../../telemetry';

const PERIODS = [
    { value: 'daily', label: 'Daily', daysBack: 29 },
    { value: 'weekly', label: 'Weekly', daysBack: 83 },
    { value: 'monthly', label: 'Monthly', daysBack: 364 },
];

const METRICS = [
    { value: 'quantity', label: 'Quantity' },
    { value: 'value', label: 'Value (Rs)' },
];

// Colour palette — index matches the spec's position in the ordered spec list
const SPEC_COLORS = [
    '#3b82f6', // blue       — Vial 10 ml
    '#10b981', // emerald    — Vial 5 ml
    '#f59e0b', // amber      — Tablet 50 mg
    '#ef4444', // red        — Tablet 25 mg
    '#8b5cf6', // purple     — Tablet 12 mg
    '#06b6d4', // cyan
    '#f97316', // orange
    '#ec4899', // pink
];

const PAGE = '/admin/reports';

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

function BarChart({ dataPoints, metric, specColorMap }) {
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

            {/* Stacked bars + X labels */}
            {dataPoints.map((d, i) => {
                const x = PAD.left + i * slotW + barGap;
                const lx = PAD.left + (i + 0.5) * slotW;
                const ly = PAD.top + CH + 13;
                const isHovered = tooltip?.i === i;

                // Build stacked segments bottom → top
                let cumH = 0;
                const segments = (d.specs || []).map((spec) => {
                    const val = metric === 'quantity' ? spec.quantity : Number(spec.value);
                    if (val <= 0) return null;
                    const segH = yMax > 0 ? (val / yMax) * CH : 0;
                    const segY = PAD.top + CH - cumH - segH;
                    cumH += segH;
                    const color = specColorMap[spec.specName] ?? '#9ca3af';
                    return (
                        <rect
                            key={spec.specName}
                            x={x} y={segY}
                            width={barW}
                            height={Math.max(segH, 0)}
                            fill={isHovered ? shadeColor(color, -20) : color}
                            rx={barW > 8 ? 2 : 0}
                        />
                    );
                }).filter(Boolean);

                // Fallback: single grey bar if no spec data
                if (segments.length === 0) {
                    const val = metric === 'quantity' ? d.quantity : Number(d.value);
                    const barH = yMax > 0 ? (val / yMax) * CH : 0;
                    segments.push(
                        <rect key="_total" x={x} y={PAD.top + CH - barH}
                            width={barW} height={Math.max(barH, 0)}
                            fill={isHovered ? '#1d4ed8' : '#3b82f6'}
                            rx={barW > 8 ? 2 : 0} />
                    );
                }

                return (
                    <g
                        key={i}
                        onMouseEnter={() => setTooltip({ i, lx, barTopY: PAD.top + CH - cumH, d })}
                        onMouseLeave={() => setTooltip(null)}
                        style={{ cursor: 'default' }}
                    >
                        {segments}
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
                const activeSpecs = (d.specs || []).filter(s =>
                    (metric === 'quantity' ? s.quantity : s.value) > 0);
                const tipH = 28 + activeSpecs.length * 16 + 12;
                const tx = Math.min(Math.max(tooltip.lx - 75, PAD.left), W - 160);
                const ty = Math.max(tooltip.barTopY - tipH - 8, PAD.top);
                return (
                    <g pointerEvents="none">
                        <rect x={tx} y={ty} width={155} height={tipH}
                            fill="white" stroke="#d1d5db" rx={5}
                            filter="url(#sg-shadow)" />
                        <text x={tx + 10} y={ty + 17} fontSize={12} fontWeight="600" fill="#111827">
                            {d.label}
                        </text>
                        {activeSpecs.map((spec, si) => {
                            const val = metric === 'quantity' ? spec.quantity : Number(spec.value);
                            const color = specColorMap[spec.specName] ?? '#9ca3af';
                            return (
                                <g key={spec.specName}>
                                    <rect x={tx + 10} y={ty + 26 + si * 16} width={8} height={8}
                                        fill={color} rx={1} />
                                    <text x={tx + 22} y={ty + 34 + si * 16} fontSize={10} fill="#374151">
                                        {spec.specName.replace('Tablet ', 'Tab ').replace(' (10 Tablets)', '')}: {
                                            metric === 'quantity'
                                                ? val.toLocaleString()
                                                : 'Rs ' + val.toLocaleString()
                                        }
                                    </text>
                                </g>
                            );
                        })}
                    </g>
                );
            })()}
        </svg>
    );
}

function shadeColor(hex, pct) {
    const num = parseInt(hex.slice(1), 16);
    const r = Math.min(255, Math.max(0, (num >> 16) + pct));
    const g = Math.min(255, Math.max(0, ((num >> 8) & 0xff) + pct));
    const b = Math.min(255, Math.max(0, (num & 0xff) + pct));
    return '#' + [r, g, b].map(v => v.toString(16).padStart(2, '0')).join('');
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
            .then(res => {
                setData(res.data);
                trackEvent('sales_graph_loaded', PAGE, {
                    period,
                    dataPoints: res.data.dataPoints?.length ?? 0,
                });
            })
            .catch(() => {
                setError('Failed to load sales data. Please try again.');
                trackEvent('sales_graph_error', PAGE, { period });
            })
            .finally(() => setLoading(false));
    }, [period, from, to]);

    const handlePeriodChange = useCallback((newPeriod) => {
        setPeriod(newPeriod);
        trackEvent('period_changed', PAGE, { from: period, to: newPeriod });
    }, [period]);

    const handleMetricChange = useCallback((newMetric) => {
        setMetric(newMetric);
        trackEvent('metric_toggled', PAGE, { metric: newMetric });
    }, []);

    // Build consistent colour map from the first data point's ordered spec list
    const allSpecNames = data?.dataPoints?.[0]?.specs?.map(s => s.specName) ?? [];
    const specColorMap = Object.fromEntries(
        allSpecNames.map((name, i) => [name, SPEC_COLORS[i % SPEC_COLORS.length]])
    );

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
                            onClick={() => handlePeriodChange(p.value)}
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
                            onClick={() => handleMetricChange(m.value)}
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
                    <BarChart
                        dataPoints={data.dataPoints}
                        metric={metric}
                        specColorMap={specColorMap}
                    />

                    {/* Spec colour legend */}
                    {allSpecNames.length > 0 && (
                        <div style={{
                            display: 'flex',
                            flexWrap: 'wrap',
                            gap: '12px 20px',
                            justifyContent: 'center',
                            padding: '12px 16px 4px',
                            borderTop: '1px solid #f3f4f6',
                            marginTop: '8px',
                        }}
                            aria-label="Chart legend"
                        >
                            {allSpecNames.map((name, i) => (
                                <div key={name} style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                                    <div style={{
                                        width: 12, height: 12,
                                        borderRadius: 2,
                                        background: SPEC_COLORS[i % SPEC_COLORS.length],
                                        flexShrink: 0,
                                    }} />
                                    <span style={{ fontSize: '12px', color: '#374151' }}>{name}</span>
                                </div>
                            ))}
                        </div>
                    )}
                </div>
            )}
        </div>
    );
}
