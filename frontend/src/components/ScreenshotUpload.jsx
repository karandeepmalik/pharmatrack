import React from 'react';
import { SCREENSHOT_CONSTRAINTS } from '../constants';

/**
 * Multi-file payment screenshot upload widget.
 *
 * @param {Object}    props
 * @param {Array}     props.screenshots    - array of {file, preview, error}
 * @param {boolean}   props.canAddMore     - whether the user can add more screenshots
 * @param {React.Ref} props.fileInputRef   - ref forwarded to the hidden <input>
 * @param {function}  props.onAdd          - change handler for <input type="file">
 * @param {function}  props.onRemove       - called with (index) when Remove is clicked
 * @param {boolean}   props.required       - whether at least one screenshot is required
 */
export default function ScreenshotUpload({
  screenshots,
  canAddMore,
  fileInputRef,
  onAdd,
  onRemove,
  required = false,
}) {
  const validCount = screenshots.filter((s) => s.file != null).length;

  return (
    <div className="form-group screenshot-upload">
      <label>
        Payment Screenshots{' '}
        {required
          ? <span className="required">*</span>
          : <span className="optional">(optional)</span>}
      </label>

      <p className="field-hint">
        Attach one or more screenshots of your payment confirmation (max 5).
        Accepted: PNG, JPEG, WebP, GIF — max {SCREENSHOT_CONSTRAINTS.MAX_LABEL} each.
      </p>

      {/* Uploaded screenshots list */}
      {screenshots.length > 0 && (
        <div className="screenshot-list">
          {screenshots.map((s, idx) => (
            <div key={idx} className="screenshot-item">
              {s.error ? (
                <p role="alert" className="field-error">{s.error}</p>
              ) : (
                <>
                  {s.preview && (
                    <img
                      src={s.preview}
                      alt={`Payment screenshot ${idx + 1} preview`}
                      className="screenshot-thumb"
                    />
                  )}
                  {s.file && <span className="screenshot-filename">{s.file.name}</span>}
                </>
              )}
              <button
                type="button"
                className="btn btn-sm btn-remove"
                onClick={() => onRemove(idx)}
                aria-label={`Remove screenshot ${idx + 1}`}
              >
                ✕ Remove
              </button>
            </div>
          ))}
        </div>
      )}

      {/* Hidden file input */}
      <input
        id="screenshot-input"
        ref={fileInputRef}
        type="file"
        accept={SCREENSHOT_CONSTRAINTS.ACCEPT_ATTR}
        onChange={onAdd}
        aria-label="Upload payment screenshot"
        style={{ display: 'none' }}
        multiple
      />

      {/* Add button — only shown when more can be added */}
      {canAddMore && (
        <button
          type="button"
          className="btn btn-secondary btn-sm"
          onClick={() => fileInputRef.current?.click()}
          aria-label={validCount === 0 ? 'Add payment screenshot' : 'Add another payment screenshot'}
        >
          {validCount === 0 ? '+ Add Screenshot' : '+ Add Another Screenshot'}
        </button>
      )}

      {validCount > 0 && (
        <p className="screenshot-count">{validCount} screenshot{validCount !== 1 ? 's' : ''} attached</p>
      )}
    </div>
  );
}
