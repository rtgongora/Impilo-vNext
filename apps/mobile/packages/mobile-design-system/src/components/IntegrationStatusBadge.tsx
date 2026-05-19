/**
 * IntegrationStatusBadge — Mobile-safe presentation primitive for
 * Integration Service-mediated statuses (PACS, LIMS, eLMIS, telemedicine,
 * MusheX, Comms Hub, Nhume, Ndila, registries…).
 *
 * This is a pure presentation component. It accepts a canonical status string
 * (see `@impilo/mobile-integration` for the canonical type) and renders the
 * matching Badge variant. The component does not import the integration SDK
 * so the design system stays dependency-free.
 *
 * Callers should pass `friendlyLabel` to override the default label when
 * displaying citizen-facing text.
 */

import React from "react";
import { Badge, type BadgeVariant } from "./Badge";

export type IntegrationBadgeStatus =
  | "CONNECTED"
  | "PENDING"
  | "PROCESSING"
  | "AWAITING_EXTERNAL"
  | "AVAILABLE"
  | "FAILED"
  | "RETRY_SCHEDULED"
  | "ACTION_REQUIRED"
  | "TEMPORARILY_UNAVAILABLE"
  | "NOT_CONFIGURED"
  | "DISABLED"
  | "UNKNOWN";

const STATUS_LABELS: Record<IntegrationBadgeStatus, string> = {
  CONNECTED: "Connected",
  PENDING: "Pending",
  PROCESSING: "Processing",
  AWAITING_EXTERNAL: "Awaiting partner",
  AVAILABLE: "Ready",
  FAILED: "Failed",
  RETRY_SCHEDULED: "Retrying",
  ACTION_REQUIRED: "Action needed",
  TEMPORARILY_UNAVAILABLE: "Unavailable",
  NOT_CONFIGURED: "Not set up",
  DISABLED: "Off",
  UNKNOWN: "Status unknown",
};

const STATUS_VARIANTS: Record<IntegrationBadgeStatus, BadgeVariant> = {
  CONNECTED: "success",
  PENDING: "info",
  PROCESSING: "info",
  AWAITING_EXTERNAL: "info",
  AVAILABLE: "success",
  FAILED: "destructive",
  RETRY_SCHEDULED: "warning",
  ACTION_REQUIRED: "warning",
  TEMPORARILY_UNAVAILABLE: "warning",
  NOT_CONFIGURED: "outline",
  DISABLED: "outline",
  UNKNOWN: "outline",
};

export interface IntegrationStatusBadgeProps {
  status: IntegrationBadgeStatus;
  /** Override the default user-friendly label. */
  friendlyLabel?: string;
  testID?: string;
}

export function IntegrationStatusBadge({
  status,
  friendlyLabel,
  testID,
}: IntegrationStatusBadgeProps) {
  const label = friendlyLabel ?? STATUS_LABELS[status] ?? STATUS_LABELS.UNKNOWN;
  const variant = STATUS_VARIANTS[status] ?? "outline";
  return <Badge variant={variant} testID={testID}>{label}</Badge>;
}
