import React, { useState } from 'react';

export default function PaymentScreenshotViewer({ screenshots, transactionId }) {
  const [open, setOpen]         = useState(false);
  const [activeIdx, setActiveIdx] = useState(0);
  const [zoomed, setZoomed]     = useState(false);

  if (!screenshots || screenshots.length === 0) {
    return <span className="no-screenshot">No screenshot</span>;
  }

  const active  = screenshots[activeIdx] || screenshots[0];
  const dataUri = `data:${active.mimeType || 'image/png'};base64,${active.data}`;
  const total   = screenshots.length;

  function handleOpen(idx) {
    setActiveIdx(idx);
    setZoomed(false);
    setOpen(true);
  }

  return (
    <>
      <div className="screenshot-thumbs">
        {screenshots.map((ss, idx) => {
          const uri = `data:${ss.mimeType || 'image/png'};base64,${ss.data}`;
          return (
            <button
              key={idx}
              type="button"
              className="screenshot-thumb-btn"
              onClick={() => handleOpen(idx)}
              aria-label={`View payment screenshot ${idx + 1} of ${total} for transaction #${transactionId}`}
              title="Click to view full screenshot"
            >
              <img
                src={uri}
                alt={`Payment screenshot ${idx + 1} for transaction #${transactionId}`}
                className="screenshot-thumb-img"
              />
              <span className="screenshot-badge">🔍 View</span>
            </button>
          );
        })}
      </div>

      {open && (
        <div
          className="screenshot-lightbox-overlay"
          role="dialog"
          aria-modal="true"
          aria-label={`Payment screenshots for transaction #${transactionId}`}
          onClick={() => setOpen(false)}
        >
          <div
            className="screenshot-lightbox-content"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="lightbox-header">
              <h3>
                Payment Screenshot{total > 1 ? ` ${activeIdx + 1} / ${total} — ` : ' — '}
                Transaction #{transactionId}
              </h3>
              <button
                type="button"
                className="lightbox-close"
                onClick={() => setOpen(false)}
                aria-label="Close screenshot viewer"
              >
                ✕
              </button>
            </div>

            <div className={`lightbox-img-wrap${zoomed ? ' zoomed' : ''}`}>
              <img
                src={dataUri}
                alt={`Full payment screenshot ${activeIdx + 1} for transaction #${transactionId}`}
                className={`screenshot-full-img${zoomed ? ' screenshot-full-img--original' : ''}`}
              />
            </div>

            <div className="lightbox-footer">
              {total > 1 && (
                <div className="lightbox-nav">
                  <button
                    type="button"
                    className="btn btn-sm btn-secondary"
                    disabled={activeIdx === 0}
                    onClick={() => { setActiveIdx((i) => i - 1); setZoomed(false); }}
                    aria-label="Previous screenshot"
                  >
                    ← Prev
                  </button>
                  <span className="lightbox-counter">{activeIdx + 1} / {total}</span>
                  <button
                    type="button"
                    className="btn btn-sm btn-secondary"
                    disabled={activeIdx === total - 1}
                    onClick={() => { setActiveIdx((i) => i + 1); setZoomed(false); }}
                    aria-label="Next screenshot"
                  >
                    Next →
                  </button>
                </div>
              )}
              <button
                type="button"
                className="btn btn-sm btn-secondary"
                onClick={() => setZoomed((z) => !z)}
                aria-label={zoomed ? 'Fit to screen' : 'View at original size'}
                title={zoomed ? 'Fit to screen' : 'View at original size'}
              >
                {zoomed ? '↔ Fit Screen' : '🔍 Original Size'}
              </button>
              <a
                href={dataUri}
                download={`payment-screenshot-tx-${transactionId}-${activeIdx + 1}.png`}
                className="btn btn-sm btn-download"
              >
                ⬇ Download
              </a>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
