import React, { useState, useRef, useEffect } from 'react';

/**
 * Checkbox-panel multi-select used for every FILTER-purpose dropdown across the app (User,
 * Medicine Spec, Stock Type, Payment Status, Status). Deliberately not a native
 * <select multiple> — that requires ctrl/cmd-click to pick more than one option and has poor
 * touch/mobile support, which conflicts with this app's "UI must be mobile-viewable" rule.
 *
 * `selected` is always an array of values; an empty array means "no filter" (every page's
 * existing "ALL" convention). `onChange` receives the full new array on every toggle.
 */
export default function MultiSelectDropdown({ id, label, options, selected, onChange, allLabel = 'All' }) {
  const [open, setOpen] = useState(false);
  const wrapperRef = useRef(null);

  useEffect(() => {
    if (!open) return;
    const handleOutside = (e) => {
      if (wrapperRef.current && !wrapperRef.current.contains(e.target)) setOpen(false);
    };
    const handleEscape = (e) => { if (e.key === 'Escape') setOpen(false); };
    document.addEventListener('mousedown', handleOutside);
    document.addEventListener('keydown', handleEscape);
    return () => {
      document.removeEventListener('mousedown', handleOutside);
      document.removeEventListener('keydown', handleEscape);
    };
  }, [open]);

  const toggleOption = (value) => {
    onChange(selected.includes(value) ? selected.filter((v) => v !== value) : [...selected, value]);
  };

  const selectAll = () => onChange([]);

  const summary = selected.length === 0
    ? allLabel
    : selected.length === 1
      ? (options.find((o) => String(o.value) === String(selected[0]))?.label ?? allLabel)
      : `${selected.length} selected`;

  const toggleId = `${id}-toggle`;
  const labelId = `${id}-label`;
  const summaryId = `${id}-summary`;

  return (
    <div className="form-group multiselect" ref={wrapperRef}>
      {/* No htmlFor: a label programmatically associated via "for" would make the accessible-
          name algorithm replace the button's own text (the current selection summary) with
          just this label text, which is less useful. An unassociated <label> is still fine
          for the existing .form-group label CSS and reads fine visually. */}
      <label id={labelId}>{label}</label>
      <div className="multiselect-wrapper">
        <button
          type="button"
          id={toggleId}
          data-testid={toggleId}
          className="multiselect-toggle"
          aria-haspopup="listbox"
          aria-expanded={open}
          onClick={() => setOpen((o) => !o)}>
          <span id={summaryId}>{summary}</span>
        </button>
        {open && (
          <div className="multiselect-panel" role="listbox" aria-label={label}>
            <label className="multiselect-option">
              <input type="checkbox" checked={selected.length === 0} onChange={selectAll} />
              {allLabel}
            </label>
            {options.map((o) => (
              <label key={o.value} className="multiselect-option">
                <input
                  type="checkbox"
                  checked={selected.includes(o.value)}
                  onChange={() => toggleOption(o.value)}
                />
                {o.label}
              </label>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
