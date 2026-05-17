/**
 * Friendly mobile copy for Integration Service statuses.
 *
 * Every status maps to:
 *   - a user-friendly label
 *   - a citizen-friendly message
 *   - a severity used by the UI badge
 *   - a flag indicating whether the status should be retried automatically
 */

import type { IntegrationSeverity, IntegrationStatus } from "./types";

interface StatusCopy {
  label: string;
  message: string;
  severity: IntegrationSeverity;
  isTerminal: boolean;
  shouldRetry: boolean;
}

const COPY: Record<IntegrationStatus, StatusCopy> = {
  CONNECTED: {
    label: "Connected",
    message: "Connection healthy.",
    severity: "success",
    isTerminal: false,
    shouldRetry: false,
  },
  PENDING: {
    label: "Pending",
    message: "Waiting for the next step.",
    severity: "info",
    isTerminal: false,
    shouldRetry: false,
  },
  PROCESSING: {
    label: "Processing",
    message: "Working on it. You'll see an update soon.",
    severity: "info",
    isTerminal: false,
    shouldRetry: false,
  },
  AWAITING_EXTERNAL: {
    label: "Awaiting external system",
    message: "Waiting for the partner system to respond.",
    severity: "info",
    isTerminal: false,
    shouldRetry: false,
  },
  AVAILABLE: {
    label: "Ready",
    message: "Ready for you to open.",
    severity: "success",
    isTerminal: false,
    shouldRetry: false,
  },
  FAILED: {
    label: "Failed",
    message: "Something went wrong. We've saved the reference so support can help.",
    severity: "danger",
    isTerminal: true,
    shouldRetry: false,
  },
  RETRY_SCHEDULED: {
    label: "Retrying soon",
    message: "We'll try again automatically.",
    severity: "warning",
    isTerminal: false,
    shouldRetry: true,
  },
  ACTION_REQUIRED: {
    label: "Action needed",
    message: "Tap to see what's needed.",
    severity: "warning",
    isTerminal: false,
    shouldRetry: false,
  },
  TEMPORARILY_UNAVAILABLE: {
    label: "Temporarily unavailable",
    message: "This service is unavailable for a moment. Try again shortly.",
    severity: "warning",
    isTerminal: false,
    shouldRetry: true,
  },
  NOT_CONFIGURED: {
    label: "Not set up",
    message: "Not configured for this facility or service.",
    severity: "muted",
    isTerminal: true,
    shouldRetry: false,
  },
  DISABLED: {
    label: "Off",
    message: "This integration is turned off in this environment.",
    severity: "muted",
    isTerminal: true,
    shouldRetry: false,
  },
  UNKNOWN: {
    label: "Status unknown",
    message: "We couldn't determine the status. Pull to refresh.",
    severity: "muted",
    isTerminal: false,
    shouldRetry: false,
  },
};

export function integrationStatusCopy(status: IntegrationStatus): StatusCopy {
  return COPY[status] ?? COPY.UNKNOWN;
}

/** Render a one-line message safe for citizen UIs, honouring user message when present. */
export function integrationLine(status: IntegrationStatus, message?: string): string {
  if (message && message.trim().length > 0) return message.trim();
  return integrationStatusCopy(status).message;
}
