/**
 * Per-queue presentation + decision config for the IATG Trust Console.
 * Field mappings follow the owning system-of-record payloads:
 * varapi ProviderAccessRequestView, tuso AppointmentView,
 * BFF AccessRequestRecord, identity-assurance AssuranceUpgradeRequestEntity.
 */

import type { TrustConsoleQueueName } from "@/hooks/queries/useTrustConsole";

export type TrustConsoleDecisionValue = "APPROVED" | "REJECTED" | "NEEDS_MORE_INFORMATION";

export interface QueueItemView {
  id: string;
  title: string;
  subtitle: string;
  status: string;
  createdAt?: string;
  detail: { label: string; value: string }[];
}

export interface QueueConfig {
  name: TrustConsoleQueueName;
  label: string;
  description: string;
  decisions: TrustConsoleDecisionValue[];
  decisionNote?: string;
  toView: (item: Record<string, unknown>) => QueueItemView;
}

function s(value: unknown): string {
  return typeof value === "string" && value.length > 0 ? value : "—";
}

function opt(label: string, value: unknown): { label: string; value: string }[] {
  return typeof value === "string" && value.length > 0 ? [{ label, value }] : [];
}

export const QUEUE_CONFIGS: QueueConfig[] = [
  {
    name: "provider-access-requests",
    label: "Provider access",
    description: "Self-service provider access requests awaiting review (varapi).",
    decisions: ["APPROVED", "REJECTED", "NEEDS_MORE_INFORMATION"],
    toView: (item) => ({
      id: s(item.publicId),
      title: `${s(item.requestType)} · ${s(item.publicId)}`,
      subtitle: s(item.profession),
      status: s(item.status),
      createdAt: typeof item.createdAt === "string" ? item.createdAt : undefined,
      detail: [
        { label: "Next actor", value: s(item.nextActor) },
        ...opt("Council", item.councilCode),
        ...opt("Organisation", item.organizationRef),
        ...opt("Reason", item.reason),
      ],
    }),
  },
  {
    name: "facility-admin-appointments",
    label: "Facility admin claims",
    description: "Pending facility administrator appointments across facilities (tuso).",
    decisions: ["APPROVED"],
    decisionNote:
      "Facility admin appointments support approval only — rejection has no downstream transition yet.",
    toView: (item) => ({
      id: String(item.id ?? "—"),
      title: `Appointment #${String(item.id ?? "—")}`,
      subtitle: s(item.role),
      status: s(item.approvalState),
      createdAt: typeof item.createdAt === "string" ? item.createdAt : undefined,
      detail: [
        { label: "Facility", value: s(item.facilityUuid) },
        { label: "Person", value: s(item.personHealthId) },
        ...opt("Evidence", item.evidenceRef),
      ],
    }),
  },
  {
    name: "org-access-requests",
    label: "Org onboarding",
    description: "Organisation onboarding and access requests (admin governance).",
    decisions: ["APPROVED", "REJECTED", "NEEDS_MORE_INFORMATION"],
    toView: (item) => ({
      id: s(item.id),
      title: `${s(item.requestType)} · ${s(item.id)}`,
      subtitle: s(item.requestedRole),
      status: s(item.status),
      createdAt: typeof item.createdAt === "string" ? item.createdAt : undefined,
      detail: [
        { label: "Requester", value: s(item.requesterId) },
        ...opt("Organisation", item.organisationName ?? item.organisationId),
        ...opt("Risk", item.riskLevel),
      ],
    }),
  },
  {
    name: "assurance-upgrades",
    label: "Assurance upgrades",
    description: "Identity assurance upgrade requests awaiting a decision (identity-assurance).",
    decisions: ["APPROVED", "REJECTED"],
    toView: (item) => ({
      id: String(item.id ?? "—"),
      title: `Upgrade request #${String(item.id ?? "—")}`,
      subtitle: `${s(item.currentLevel)} → ${s(item.targetLevel)}`,
      status: s(item.status),
      createdAt: typeof item.createdAt === "string" ? item.createdAt : undefined,
      detail: [
        { label: "Subject", value: s(item.actorId ?? item.subjectId) },
        ...opt("Method", item.method),
      ],
    }),
  },
];

export function queueConfig(name: TrustConsoleQueueName): QueueConfig {
  const config = QUEUE_CONFIGS.find((q) => q.name === name);
  if (!config) throw new Error(`Unknown trust-console queue: ${name}`);
  return config;
}
