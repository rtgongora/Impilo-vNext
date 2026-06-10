/**
 * Health OS — Experience shell domain model.
 *
 * Conceptual layer above individual modules: apps, open work, recents, commands, resources.
 */

export type ShellAppCategory =
  | "clinical"
  | "operations"
  | "registry"
  | "finance"
  | "citizen"
  | "intelligence"
  | "system";

/** Launchable module / utility surfaced by the shell. */
export interface AppDefinition {
  id: string;
  appCode: string;
  name: string;
  description: string;
  /** Lucide icon name for registry lookup */
  icon: string;
  category: ShellAppCategory;
  /** Primary navigation target */
  href: string;
  activeFlag: boolean;
  /** Role group key aligned with AuthGuardProvider ROLE_GROUPS, or concrete Keycloak role */
  requiredRole?: string;
  systemAppFlag: boolean;
  /** Optional sort weight within category */
  weight?: number;
  /** Canonical parity sweep maturity (see docs/frontend/MATURITY_TAXONOMY.md) */
  maturity?: "live" | "partial" | "fixture" | "not_wired" | "blocked";
  /** Primary journey for launcher labelling */
  journey?: "person" | "provider" | "platform" | "cross_cutting";
  /** Seven-plane label for launcher */
  plane?: string;
  /** Disabled reason when activeFlag is false or maturity is blocked/not_wired */
  disabledReason?: string;
  /** Sovereign service slug for branded logo rendering (see config/serviceBranding.ts) */
  serviceSlug?: string;
  metadata?: Record<string, unknown>;
}

export type RunningTaskStatus = "open" | "minimized";

export type RunningTaskType =
  | "module"
  | "patient_chart"
  | "facility"
  | "workspace"
  | "registry_record"
  | "document"
  | "dicom_viewer"
  | "shell_utility"
  | "other";

/** Open unit of work (tab / workspace abstraction). */
export interface RunningTask {
  id: string;
  appId: string;
  taskType: RunningTaskType;
  title: string;
  route: string;
  contextRef?: string;
  openedAt: string;
  lastActiveAt: string;
  status: RunningTaskStatus;
  pinnedFlag?: boolean;
}

export interface RecentItem {
  id: string;
  kind: "app" | "route" | "resource" | "search";
  title: string;
  subtitle?: string;
  href: string;
  /** For dedupe / analytics */
  refKey: string;
  recordedAt: string;
  /** Optional scope hint for ABAC-style filtering in UI */
  sensitivity?: "normal" | "clinical" | "registry";
}

export type ShellCommandAction =
  | { type: "navigate"; href: string }
  | { type: "open-search" }
  | { type: "open-start" }
  | { type: "open-task-manager" }
  | { type: "open-file-manager" }
  | { type: "open-nav-drawer" }
  | { type: "open-sos" };

/** Palette / quick-action command */
export interface ShellCommand {
  id: string;
  label: string;
  description?: string;
  keywords: string[];
  action: ShellCommandAction;
  requiredRole?: string;
}

export type FileResourceType =
  | "personal_document"
  | "clinical_document"
  | "indexed_entity"
  | "link"
  | "generated_output"
  | "registry_certificate"
  | "other";

/** Unified document/resource row for the vNext file explorer */
export interface FileResource {
  id: string;
  resourceType: FileResourceType;
  title: string;
  associatedApp?: string;
  associatedEntityType?: string;
  associatedEntityId?: string;
  createdAt?: string;
  updatedAt?: string;
  mimeType?: string;
  category?: string;
  href?: string;
  downloadAction?: "prepared_url" | "navigate" | "none";
}
