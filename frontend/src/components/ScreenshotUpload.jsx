import React from 'react';
import { SCREENSHOT_CONSTRAINTS } from '../constants';

/**
 * Reusable payment screenshot upload widget.
 *
 * Extracted from SubmitTransaction to uphold SRP:
 * this component owns the upload UI; the page owns form orchestration.
 *
 * @param {Object}   props
 * @param {React.Ref} props.fileInputRef     - ref forwarded to the <input>
 * @param {string}   props.screenshotPreview - data URI for live preview (null = no file)
 * @param {string}   props.screenshotError   - validation error message (empty = no error)
 * @param {File|null} props.screenshotFile   - current File object (used for filename display)
 * @param {function} props.onFileChange      - change handler for <input type="file">
 * @param {function} props.onRemove          - called when the Remove button is clicked
 */
export default function ScreenshotUpload({
  fileInputRef,
  screenshotPreview,
  screenshotError,
  screenshotFile,
  onFileChange,
  onRemove,
}) {
  return (
    <div className="form-group screenshot-upload">
      <label htmlFor="screenshot-input">
        Payment Screenshot{' '}
        <span className="optional">(optional)</span>
      </label>

      <p className="field-hint">
        Attach a screenshot of your payment confirmation.
        Accepted: PNG, JPEG, WebP, GIF — max {SCREENSHOT_CONSTRAINTS.MAX_LABEL}.
      </p>

      <input
        id="screenshot-input"
        ref={fileInputRef}
        type="file"
        accept={SCREENSHOT_CONSTRAINTS.ACCEPT_ATTR}
        onChange={onFileChange}
        aria-label="Upload payment screenshot"
        aria-describedby={screenshotError ? 'screenshot-error' : undefined}
      />

      {screenshotError && (
        <p id="screenshot-error" role="alert" className="field-error">
          {screenshotError}
        </p>
      )}

      {screenshotPreview && (
        <div className="screenshot-preview">
          <img
            src={screenshotPreview}
            alt="Payment screenshot preview"
            className="screenshot-thumb"
          />
          <button
            type="button"
            className="btn btn-sm btn-remove"
            onClick={onRemove}
            aria-label="Remove screenshot"
          >
            ✕ Remove
          </button>
          {screenshotFile && (
            <p className="screenshot-filename">{screenshotFile.name}</p>
          )}
        </div>
      )}
    </div>
  );
}
