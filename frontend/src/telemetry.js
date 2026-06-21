import { postTelemetryEvent } from './api/api';

/**
 * Fire-and-forget telemetry event. Failures are silently swallowed so they
 * never affect the user experience.
 *
 * @param {string} eventName  e.g. "report_generated", "period_changed"
 * @param {string} page       e.g. "/admin/reports"
 * @param {Object} properties arbitrary key/value pairs visible in Cloud Logging
 */
export function trackEvent(eventName, page, properties = {}) {
    postTelemetryEvent(eventName, page, properties).catch(() => {});
}
