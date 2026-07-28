/**
 * Professional-standing alerts (Phase G5) — consumes the SAME BFF surface web's
 * `/professional` page uses (`GET /internal/v1/professional/alerts`, Phase F4),
 * itself backed by Phase E5's ProfessionalAlertsComposer. Licence expiry/
 * suspension (VARAPI), outstanding CPD, and assignments awaiting this
 * provider's own acceptance (WGV).
 *
 * Deliberately NOT the mobile-only `/internal/v1/mobile/provider/notices`
 * surface this screen already reads — notices are facility/administrative
 * messages, alerts are regulated professional-standing facts. Both are real and
 * both belong; they are different data, not duplicates.
 */
import { apiClient } from "@impilo/mobile-api-client";

/** Mirrors the `professional-alerts` section status vocabulary (WorkHomeSection). */
export type ProfessionalAlertsStatus = "OK" | "EMPTY" | "DEGRADED";

export interface ProfessionalAlertItem {
  id: string;
  kind: "LICENCE_ALERT" | "CPD_ALERT" | "ASSIGNMENT_ALERT" | string;
  source: string;
  title: string;
  description?: string;
  status?: string;
  priority?: string;
  due_at?: string | null;
  href?: string;
}

export interface ProfessionalAlerts {
  status: ProfessionalAlertsStatus;
  items: ProfessionalAlertItem[];
  summary?: Record<string, unknown>;
  /** Human-readable reason for EMPTY/DEGRADED; null when OK. */
  note?: string | null;
  generatedAt?: string;
}

interface JsonApiEnvelope<TAttributes> {
  data: { id: string; type: string; attributes: TAttributes };
}

export async function getProfessionalAlerts(): Promise<ProfessionalAlerts> {
  const res = await apiClient.get<JsonApiEnvelope<ProfessionalAlerts>>("/internal/v1/professional/alerts");
  return res.data.data.attributes;
}
