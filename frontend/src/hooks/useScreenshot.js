import { useState, useRef, useCallback } from 'react';
import { SCREENSHOT_CONSTRAINTS } from '../constants';

const MAX_SCREENSHOTS = 5;

/**
 * Custom hook encapsulating multiple-screenshot upload state and behaviour.
 *
 * @returns {{
 *   screenshots: Array<{file: File, preview: string, error: string}>,
 *   addScreenshot: function,
 *   removeScreenshot: function,
 *   clearAll: function,
 *   hasAnyError: boolean,
 *   hasAnyScreenshot: boolean,
 *   fileInputRef: React.RefObject,
 * }}
 */
export default function useScreenshot() {
  const [screenshots, setScreenshots] = useState([]);
  const fileInputRef = useRef(null);

  const addScreenshot = useCallback((e) => {
    const files = Array.from(e.target.files || []);
    if (!files.length) return;

    const toAdd = files.slice(0, MAX_SCREENSHOTS - screenshots.length);

    const newEntries = toAdd.map((file) => {
      if (!SCREENSHOT_CONSTRAINTS.ALLOWED_TYPES.includes(file.type)) {
        return { file: null, preview: null, error: 'Only PNG, JPEG, WebP, or GIF images are allowed.' };
      }
      if (file.size > SCREENSHOT_CONSTRAINTS.MAX_BYTES) {
        return { file: null, preview: null, error: `File must be smaller than ${SCREENSHOT_CONSTRAINTS.MAX_LABEL}.` };
      }
      return { file, preview: null, error: '' };
    });

    setScreenshots((prev) => [...prev, ...newEntries]);

    // Generate previews for valid entries
    newEntries.forEach((entry, idx) => {
      if (!entry.file) return;
      const globalIdx = screenshots.length + idx;
      const reader = new FileReader();
      reader.onloadend = () => {
        setScreenshots((prev) => {
          const next = [...prev];
          if (next[globalIdx]) {
            next[globalIdx] = { ...next[globalIdx], preview: reader.result };
          }
          return next;
        });
      };
      reader.readAsDataURL(entry.file);
    });

    // Reset input so the same file can be re-added after removing
    if (fileInputRef.current) fileInputRef.current.value = '';
  }, [screenshots]);

  const removeScreenshot = useCallback((index) => {
    setScreenshots((prev) => prev.filter((_, i) => i !== index));
  }, []);

  const clearAll = useCallback(() => {
    setScreenshots([]);
    if (fileInputRef.current) fileInputRef.current.value = '';
  }, []);

  return {
    screenshots,
    addScreenshot,
    removeScreenshot,
    clearAll,
    hasAnyError: screenshots.some((s) => s.error),
    hasAnyScreenshot: screenshots.some((s) => s.file != null),
    canAddMore: screenshots.length < MAX_SCREENSHOTS,
    fileInputRef,
  };
}
