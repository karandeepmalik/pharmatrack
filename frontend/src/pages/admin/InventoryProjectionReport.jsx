import React, { useState, useEffect, useMemo, useCallback } from 'react';
import * as api from '../../api/api';
import { trackEvent } from '../../telemetry';

const WEEKS_AHEAD_OPTIONS = [
    { value: 8, label: '8 Weeks' },
    { value: 12, label: '12 Weeks' },
];

const UNIT_OPTIONS = [
    { value: 'native', label: 'Vials / Strips' },
    { value: 'mg', label: 'mg' },
];

const TABLET_REF_OPTIONS = [20, 30, 50];
const DEFAULT_TABLET_REF_MG = 30;
const TABLETS_PER_STRIP = 10;

const VIAL_REF_OPTIONS = [8, 10];
const DEFAULT_VIAL_REF_ML = 8;
const VIAL_CONCENTRATION_MG_PER_ML = 20;

const LINE_COLORS = [
    '#3b82f6', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6',
    '#06b6d4', '#f97316', '#ec4899', '#84cc16', '#6366f1',
];

const PAGE = '/admin/reports';

// SVG chart constants
const W = 800, H = 380;
const PAD = { top: 26, right: 20, bottom: 46, left: 75 };
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

// mg content of "1" equivalent unit under the admin's chosen reference spec. Every line's real
// quantity is always mg (see InventoryProjectionResponse) — this is a pure display/stock-input
// conversion factor, not a second regression.
function mgPerUnit(dosageForm, tabletRefMg, vialRefMl) {
    if (dosageForm === 'TABLET') return tabletRefMg * TABLETS_PER_STRIP;
    if (dosageForm === 'VIAL') return vialRefMl * VIAL_CONCENTRATION_MG_PER_ML;
    return 1;
}

function equivalentUnitLabel(dosageForm, tabletRefMg, vialRefMl) {
    if (dosageForm === 'TABLET') return `strips (${tabletRefMg}mg)`;
    if (dosageForm === 'VIAL') return `vials (${vialRefMl}ml)`;
    return 'mg';
}

// Which series (and label) a line displays for the currently selected unit mode.
function seriesFor(line, unit, tabletRefMg, vialRefMl) {
    const hasEquivalentUnit = line.dosageForm === 'TABLET' || line.dosageForm === 'VIAL';
    if (unit === 'mg' || !hasEquivalentUnit) {
        return { historical: line.historicalQuantityMg, forecast: line.forecastQuantityMg, label: 'mg' };
    }
    const perUnit = mgPerUnit(line.dosageForm, tabletRefMg, vialRefMl);
    return {
        historical: line.historicalQuantityMg.map(v => Number(v) / perUnit),
        forecast: line.forecastQuantityMg.map(v => Number(v) / perUnit),
        label: equivalentUnitLabel(line.dosageForm, tabletRefMg, vialRefMl),
    };
}

const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

function formatDate(date) {
    return `${date.getDate()} ${MONTHS[date.getMonth()]} ${date.getFullYear()}`;
}

// How far ahead to simulate before giving up and reporting "not projected to run out".
const STOCKOUT_CAP_DAYS = 3650; // 10 years

/**
 * Walks the fitted mg regression forward day by day (the weekly rate at continuous week-index
 * `historyWeeks + daysElapsed/7`, floored at 0 same as the server-side forecast) until
 * cumulative consumption reaches `currentStockMg`, returning that calendar date — or null if
 * the model doesn't deplete it within STOCKOUT_CAP_DAYS (e.g. a declining trend that floors at
 * zero demand before the stock does, so it would never run out under this projection).
 */
function computeStockOutDate(forecastStartDate, slopeMg, interceptMg, historyWeeks, currentStockMg) {
    if (!(currentStockMg > 0)) return null;
    const anchor = new Date(forecastStartDate + 'T00:00:00');
    let cumulative = 0;
    for (let d = 0; d < STOCKOUT_CAP_DAYS; d++) {
        const weekIndex = historyWeeks + d / 7;
        const weeklyRate = Math.max(interceptMg + slopeMg * weekIndex, 0);
        cumulative += weeklyRate / 7;
        if (cumulative >= currentStockMg) {
            const date = new Date(anchor);
            date.setDate(date.getDate() + d);
            return date;
        }
    }
    return null;
}

function ProjectionLineChart({ historicalLabels, forecastLabels, lines, lineColorMap, unit, tabletRefMg, vialRefMl }) {
    const [tooltip, setTooltip] = useState(null);

    const labels = [...historicalLabels, ...forecastLabels];
    const n = labels.length;
    const historyLen = historicalLabels.length;

    const seriesByLine = lines.map(l => ({ line: l, ...seriesFor(l, unit, tabletRefMg, vialRefMl) }));

    const allValues = seriesByLine.flatMap(s => [
        ...s.historical.map(Number),
        ...s.forecast.map(Number),
    ]);
    const maxVal = Math.max(...allValues, 0);
    const yMax = niceMax(maxVal);

    const xStep = n > 1 ? CW / (n - 1) : 0;
    const xAt = (i) => PAD.left + i * xStep;
    const yAt = (v) => PAD.top + CH - (yMax > 0 ? (v / yMax) * CH : 0);
    const boundaryX = xAt(historyLen - 0.5);

    const TICKS = 5;
    const yTicks = Array.from({ length: TICKS + 1 }, (_, i) => Math.round((yMax * i) / TICKS));
    const labelStep = Math.max(1, Math.ceil(n / 10));

    return (
        <svg
            viewBox={`0 0 ${W} ${H}`}
            width="100%"
            style={{ display: 'block', overflow: 'visible' }}
            aria-label="Inventory projection line chart"
        >
            <defs>
                <filter id="ip-shadow" x="-10%" y="-10%" width="130%" height="130%">
                    <feDropShadow dx="0" dy="1" stdDeviation="2" floodOpacity="0.12" />
                </filter>
            </defs>

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

            <text
                x={14}
                y={PAD.top + CH / 2}
                textAnchor="middle"
                fontSize={11}
                fill="#6b7280"
                transform={`rotate(-90, 14, ${PAD.top + CH / 2})`}
            >
                {unit === 'mg' ? 'mg / Week' : 'Units / Week'}
            </text>

            {historyLen > 0 && historyLen < n && (
                <>
                    <line x1={boundaryX} y1={PAD.top} x2={boundaryX} y2={PAD.top + CH}
                        stroke="#9ca3af" strokeDasharray="3,3" strokeWidth={1} />
                    <text x={boundaryX} y={PAD.top - 10} textAnchor="middle" fontSize={10} fill="#6b7280">
                        Forecast →
                    </text>
                </>
            )}

            {labels.map((label, i) => {
                if (i % labelStep !== 0 && i !== n - 1) return null;
                return (
                    <text key={i} x={xAt(i)} y={PAD.top + CH + 18} textAnchor="middle" fontSize={10} fill="#6b7280">
                        {label}
                    </text>
                );
            })}

            {seriesByLine.map(({ line, historical, forecast, label }) => {
                const color = lineColorMap[line.name] ?? '#9ca3af';
                const histPts = historical.map((v, i) => [xAt(i), yAt(Number(v))]);
                const futPts = forecast.map((v, i) => [xAt(historyLen + i), yAt(Number(v))]);
                const bridge = histPts.length > 0 ? [histPts[histPts.length - 1], ...futPts] : futPts;
                const toPoints = (pts) => pts.map(p => p.join(',')).join(' ');

                return (
                    <g key={line.name}>
                        {histPts.length > 1 && (
                            <polyline points={toPoints(histPts)} fill="none" stroke={color} strokeWidth={2} />
                        )}
                        {bridge.length > 1 && (
                            <polyline points={toPoints(bridge)} fill="none" stroke={color} strokeWidth={2}
                                strokeDasharray="5,4" />
                        )}
                        {histPts.map(([x, y], i) => (
                            <circle
                                key={`h${i}`} cx={x} cy={y} r={3} fill={color}
                                onMouseEnter={() => setTooltip({
                                    x, y, label: labels[i], value: historical[i], unitLabel: label,
                                    name: line.name, isForecast: false,
                                })}
                                onMouseLeave={() => setTooltip(null)}
                                style={{ cursor: 'pointer' }}
                            />
                        ))}
                        {futPts.map(([x, y], i) => (
                            <circle
                                key={`f${i}`} cx={x} cy={y} r={3} fill="white" stroke={color} strokeWidth={2}
                                onMouseEnter={() => setTooltip({
                                    x, y, label: labels[historyLen + i], value: forecast[i], unitLabel: label,
                                    name: line.name, isForecast: true,
                                })}
                                onMouseLeave={() => setTooltip(null)}
                                style={{ cursor: 'pointer' }}
                            />
                        ))}
                    </g>
                );
            })}

            {tooltip && (() => {
                const tx = Math.min(Math.max(tooltip.x - 75, PAD.left), W - 160);
                const ty = Math.max(tooltip.y - 54, PAD.top);
                return (
                    <g pointerEvents="none">
                        <rect x={tx} y={ty} width={155} height={46} fill="white" stroke="#d1d5db" rx={5}
                            filter="url(#ip-shadow)" />
                        <text x={tx + 9} y={ty + 17} fontSize={11} fontWeight="600" fill="#111827">
                            {tooltip.label}{tooltip.isForecast ? ' (forecast)' : ''}
                        </text>
                        <text x={tx + 9} y={ty + 34} fontSize={10} fill="#374151">
                            {tooltip.name}: {Number(tooltip.value).toFixed(1)} {tooltip.unitLabel}
                        </text>
                    </g>
                );
            })()}
        </svg>
    );
}

export default function InventoryProjectionReport({ users }) {
    const [weeksAhead, setWeeksAhead] = useState(8);
    const [unit, setUnit] = useState('native');
    const [tabletRefMg, setTabletRefMg] = useState(DEFAULT_TABLET_REF_MG);
    const [vialRefMl, setVialRefMl] = useState(DEFAULT_VIAL_REF_ML);
    const [selectedCities, setSelectedCities] = useState([]);
    const [stockInputs, setStockInputs] = useState({});
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

    useEffect(() => {
        setLoading(true);
        setError('');
        setData(null);
        api.getReportInventoryProjection(weeksAhead, selectedCities.length ? selectedCities : undefined)
            .then(res => {
                setData(res.data);
                trackEvent('inventory_projection_loaded', PAGE, {
                    weeksAhead,
                    cities: selectedCities,
                    lines: res.data.lines?.length ?? 0,
                });
            })
            .catch(() => {
                setError('Failed to load inventory projection. Please try again.');
                trackEvent('inventory_projection_error', PAGE, { weeksAhead, cities: selectedCities });
            })
            .finally(() => setLoading(false));
    }, [weeksAhead, selectedCities]);

    const handleWeeksAheadChange = useCallback((value) => {
        setWeeksAhead(value);
        trackEvent('projection_horizon_changed', PAGE, { weeksAhead: value });
    }, []);

    // A number typed under the old unit/reference spec doesn't mean anything under a new one
    // (e.g. "50" could mean 50 strips of 30mg or 50 strips of 50mg) — clear rather than
    // silently reinterpreting it.
    const handleUnitChange = useCallback((value) => {
        setUnit(value);
        setStockInputs({});
        trackEvent('projection_unit_changed', PAGE, { unit: value });
    }, []);

    const handleTabletRefChange = useCallback((value) => {
        setTabletRefMg(value);
        setStockInputs({});
        trackEvent('projection_tablet_ref_changed', PAGE, { tabletRefMg: value });
    }, []);

    const handleVialRefChange = useCallback((value) => {
        setVialRefMl(value);
        setStockInputs({});
        trackEvent('projection_vial_ref_changed', PAGE, { vialRefMl: value });
    }, []);

    const handleStockInputChange = useCallback((lineName, value) => {
        setStockInputs(prev => ({ ...prev, [lineName]: value }));
    }, []);

    const toggleCity = useCallback((city) => {
        setSelectedCities(prev => {
            const next = prev.includes(city) ? prev.filter(c => c !== city) : [...prev, city];
            trackEvent('projection_city_toggled', PAGE, { cities: next });
            return next;
        });
    }, []);

    const selectOverall = useCallback(() => {
        setSelectedCities([]);
        trackEvent('projection_city_toggled', PAGE, { cities: [] });
    }, []);

    const lineColorMap = Object.fromEntries(
        (data?.lines ?? []).map((l, i) => [l.name, LINE_COLORS[i % LINE_COLORS.length]])
    );

    const hasTabletLine = (data?.lines ?? []).some(l => l.dosageForm === 'TABLET');
    const hasVialLine = (data?.lines ?? []).some(l => l.dosageForm === 'VIAL');

    return (
        <div>
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px', alignItems: 'center', marginBottom: '12px' }}>
                <span style={{ fontSize: '13px', color: '#6b7280', fontWeight: 500 }}>Project:</span>
                <div className="btn-group" aria-label="Projection horizon">
                    {WEEKS_AHEAD_OPTIONS.map(w => (
                        <button
                            key={w.value}
                            type="button"
                            className={`btn btn-sm ${weeksAhead === w.value ? 'btn-primary' : 'btn-secondary'}`}
                            onClick={() => handleWeeksAheadChange(w.value)}
                        >
                            {w.label}
                        </button>
                    ))}
                </div>
                <span style={{ fontSize: '13px', color: '#6b7280', fontWeight: 500, marginLeft: '12px' }}>Unit:</span>
                <div className="btn-group" aria-label="Quantity unit">
                    {UNIT_OPTIONS.map(u => (
                        <button
                            key={u.value}
                            type="button"
                            className={`btn btn-sm ${unit === u.value ? 'btn-primary' : 'btn-secondary'}`}
                            onClick={() => handleUnitChange(u.value)}
                        >
                            {u.label}
                        </button>
                    ))}
                </div>
            </div>

            {unit === 'native' && (hasTabletLine || hasVialLine) && (
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px 16px', alignItems: 'center', marginBottom: '12px' }}>
                    {hasTabletLine && (
                        <>
                            <span style={{ fontSize: '13px', color: '#6b7280', fontWeight: 500 }}>Oral strip reference:</span>
                            <div className="btn-group" aria-label="Oral tablet reference strength">
                                {TABLET_REF_OPTIONS.map(mg => (
                                    <button
                                        key={mg}
                                        type="button"
                                        className={`btn btn-sm ${tabletRefMg === mg ? 'btn-primary' : 'btn-secondary'}`}
                                        onClick={() => handleTabletRefChange(mg)}
                                    >
                                        {mg}mg
                                    </button>
                                ))}
                            </div>
                        </>
                    )}
                    {hasVialLine && (
                        <>
                            <span style={{ fontSize: '13px', color: '#6b7280', fontWeight: 500 }}>Injection vial reference:</span>
                            <div className="btn-group" aria-label="Injection vial reference volume">
                                {VIAL_REF_OPTIONS.map(ml => (
                                    <button
                                        key={ml}
                                        type="button"
                                        className={`btn btn-sm ${vialRefMl === ml ? 'btn-primary' : 'btn-secondary'}`}
                                        onClick={() => handleVialRefChange(ml)}
                                    >
                                        {ml}ml
                                    </button>
                                ))}
                            </div>
                        </>
                    )}
                </div>
            )}

            <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px', alignItems: 'center', marginBottom: '16px' }}>
                <span style={{ fontSize: '13px', color: '#6b7280', fontWeight: 500 }}>City:</span>
                <div className="btn-group" style={{ flexWrap: 'wrap' }} aria-label="City filter">
                    <button
                        type="button"
                        className={`btn btn-sm ${selectedCities.length === 0 ? 'btn-primary' : 'btn-secondary'}`}
                        onClick={selectOverall}
                    >
                        Overall
                    </button>
                    {cityOptions.map(city => (
                        <button
                            key={city}
                            type="button"
                            className={`btn btn-sm ${selectedCities.includes(city) ? 'btn-primary' : 'btn-secondary'}`}
                            onClick={() => toggleCity(city)}
                        >
                            {city}
                        </button>
                    ))}
                </div>
            </div>

            {selectedCities.length > 0 && (
                <p style={{ fontSize: '12px', color: '#9a6700', background: '#fffbeb', border: '1px solid #fde68a', borderRadius: '6px', padding: '8px 12px', marginBottom: '16px' }}>
                    City attribution is based on the submitting user's assigned location and is approximate —
                    some dispatches cross cities in practice (e.g. Bengaluru covering Mumbai or Delhi demand),
                    which isn't captured by this filter.
                </p>
            )}

            {loading && <p style={{ color: '#6b7280', padding: '24px 0' }}>Loading projection…</p>}
            {error && <div className="alert alert-error" role="alert">{error}</div>}

            {data && !loading && data.lines.length === 0 && (
                <p style={{ color: '#6b7280', textAlign: 'center', padding: '48px 0', fontSize: '15px' }}>
                    Not enough sales history to build a projection for this selection.
                </p>
            )}

            {data && !loading && data.lines.length > 0 && (
                <>
                    <div style={{ display: 'flex', gap: '16px', flexWrap: 'wrap', marginBottom: '8px' }}>
                        <div style={{ background: '#f0fdf4', borderRadius: '8px', padding: '12px 20px', minWidth: '180px' }}>
                            <div style={{ fontSize: '11px', fontWeight: 600, color: '#16a34a', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                                Total Projected Revenue (next {weeksAhead}w)
                            </div>
                            <div style={{ fontSize: '24px', fontWeight: 700, color: '#15803d', marginTop: '2px' }}>
                                Rs {data.totalForecastRevenue.toLocaleString()}
                            </div>
                        </div>
                        {data.lines.map(line => {
                            const { forecast, label } = seriesFor(line, unit, tabletRefMg, vialRefMl);
                            const totalQty = forecast.reduce((s, v) => s + Number(v), 0);
                            const perUnit = mgPerUnit(line.dosageForm, tabletRefMg, vialRefMl);
                            const rawStock = stockInputs[line.name];
                            const stockNum = rawStock === undefined || rawStock === '' ? null : Number(rawStock);
                            const hasValidStock = stockNum !== null && !isNaN(stockNum) && stockNum > 0;
                            const stockMg = hasValidStock ? stockNum * (unit === 'mg' ? 1 : perUnit) : null;
                            const stockOutDate = hasValidStock
                                ? computeStockOutDate(data.forecastStartDate, line.regressionSlopeMg, line.regressionInterceptMg, data.historyWeeks, stockMg)
                                : null;
                            return (
                                <div
                                    key={line.name}
                                    style={{
                                        background: '#eff6ff', borderRadius: '8px', padding: '12px 20px', minWidth: '200px',
                                        borderLeft: `4px solid ${lineColorMap[line.name]}`,
                                    }}
                                >
                                    <div style={{ fontSize: '11px', fontWeight: 600, color: '#3b82f6', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                                        {line.name} — Next {weeksAhead}w
                                    </div>
                                    <div style={{ fontSize: '24px', fontWeight: 700, color: '#1e40af', marginTop: '2px' }}>
                                        {totalQty.toFixed(1)} {label}
                                    </div>
                                    <div style={{ marginTop: '10px', paddingTop: '10px', borderTop: '1px solid #dbeafe' }}>
                                        <label
                                            htmlFor={`stock-input-${line.name}`}
                                            style={{ fontSize: '11px', color: '#6b7280', display: 'block', marginBottom: '4px' }}
                                        >
                                            Current stock ({label})
                                        </label>
                                        <input
                                            id={`stock-input-${line.name}`}
                                            type="number"
                                            min="0"
                                            step="0.1"
                                            value={rawStock ?? ''}
                                            onChange={e => handleStockInputChange(line.name, e.target.value)}
                                            placeholder="e.g. 50"
                                            style={{ width: '110px', fontSize: '13px', padding: '4px 6px' }}
                                        />
                                        {hasValidStock && (
                                            <p style={{ fontSize: '12px', marginTop: '6px', color: stockOutDate ? '#b45309' : '#6b7280' }}>
                                                {stockOutDate
                                                    ? `Projected to run out around ${formatDate(stockOutDate)}`
                                                    : 'Not projected to run out within the next 10 years at this trend'}
                                            </p>
                                        )}
                                    </div>
                                </div>
                            );
                        })}
                    </div>
                    <p style={{ fontSize: '12px', color: '#6b7280', marginTop: '0', marginBottom: '16px' }}>
                        Revenue, not profit — this app doesn't track medicine cost/purchase price, so this is
                        projected sales value at each line's historical average price per mg.
                    </p>

                    <div style={{ background: '#fff', border: '1px solid #e5e7eb', borderRadius: '10px', padding: '16px 8px 8px' }}>
                        <ProjectionLineChart
                            historicalLabels={data.historicalLabels}
                            forecastLabels={data.forecastLabels}
                            lines={data.lines}
                            lineColorMap={lineColorMap}
                            unit={unit}
                            tabletRefMg={tabletRefMg}
                            vialRefMl={vialRefMl}
                        />
                        <div style={{
                            display: 'flex', flexWrap: 'wrap', gap: '12px 20px', justifyContent: 'center',
                            padding: '12px 16px 4px', borderTop: '1px solid #f3f4f6', marginTop: '8px',
                        }}
                            aria-label="Chart legend"
                        >
                            {data.lines.map(l => (
                                <div key={l.name} style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                                    <div style={{
                                        width: 12, height: 12, borderRadius: 2,
                                        background: lineColorMap[l.name], flexShrink: 0,
                                    }} />
                                    <span style={{ fontSize: '12px', color: '#374151' }}>{l.name}</span>
                                </div>
                            ))}
                        </div>
                    </div>
                </>
            )}
        </div>
    );
}
