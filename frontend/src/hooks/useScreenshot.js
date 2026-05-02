import { useState, useRef, useCallback } from 'react';
import { SCREENSHOT_CONSTRAINTS } from '../constants';

/**
 * Custom hook encapsulating all screenshot upload state and behaviour.
 *
 * Extracted from SubmitTransaction to uphold the Single Responsibility Principle:
 * the page component handles form orchestration; this hook owns the screenshot lifecycle.
 *
 * @returns {{
 *   screenshotFile: File|null,
 *   screenshotPreview: string|null,
 *   screenshotError: string,
 *   fileInputRef: React.RefObject,
 *   handleScreenshotChange: function,
 *   handleRemoveScreenshot: function,
 * }}
 */
export default function useScreenshot() {
  const [screenshotFile, setScreenshotFile]       = useState(null);
  const [screenshotPreview, setScreenshotPreview] = useState(null);
  const [screenshotError, setScreenshotError]     = useState('');
  const fileInputRef = useRef(null);

  const handleScreenshotChange = useCallback((e) => {
    const file = e.target.files?.[0];
    if (!file) return;

    if (!SCREENSHOT_CONSTRAINTS.ALLOWED_TYPES.includes(file.type)) {
      setScreenshotError('Only PNG, JPEG, WebP, or GIF images are allowed.');
      setScreenshotFile(null);
      setScreenshotPreview(null);
      return;
    }

    if (file.size > SCREENSHOT_CONSTRAINTS.MAX_BYTES) {
      setScreenshotError(`File must be smaller than ${SCREENSHOT_CONSTRAINTS.MAX_LABEL}.`);
      setScreenshotFile(null);
      setScreenshotPreview(null);
      return;
    }

    setScreenshotError('');
    setScreenshotFile(file);

    const reader = new FileReader();
    reader.onloadend = () => setScreenshotPreview(reader.result);
    reader.readAsDataURL(file);
  }, []);

  const handleRemoveScreenshot = useCallback(() => {
    setScreenshotFile(null);
    setScreenshotPreview(null);
    setScreenshotError('');
    if (fileInputRef.current) fileInputRef.current.value = '';
  }, []);

  return {
    screenshotFile,
    screenshotPreview,
    screenshotError,
    fileInputRef,
    handleScreenshotChange,
    handleRemoveScreenshot,
  };
}
