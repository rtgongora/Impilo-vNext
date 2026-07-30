/**
 * Impilo vNext — Mobile Work Mode Contract
 *
 * The WorkMode vocabulary is re-exported from contracts/trust/types/work-mode.ts
 * (itself the TS twin of libs/tshepo-contracts/.../enums/WorkMode.java), so
 * mobile and the web shell now read the same file rather than two copies of it.
 *
 * This used to be a hand-mirror, because Metro watches only its own workspace
 * and could not reach a file above apps/mobile. Adding the repo-root contracts/
 * directory to `watchFolders` in both apps' metro.config.js removes that limit;
 * the import is proven by an `expo export` in CI, not only by type-check, since
 * TypeScript resolving a path says nothing about whether Metro can bundle it.
 *
 * test/workModeParity.test.ts still runs. It compares this module against the
 * canonical file, which is now trivially true — and that is the point: it is
 * what fails if anyone reintroduces a local copy here.
 */

export type {
  WorkMode,
  WorkModeAnchorKind,
  WorkModeClinicalDataAccess,
  WorkModeDefinition,
} from "../../../../../contracts/trust/types/work-mode";

export {
  WORK_MODES,
  WORK_MODE_DEFINITIONS,
  grantsIdentifiedClinicalRead,
} from "../../../../../contracts/trust/types/work-mode";

import type { WorkMode } from "../../../../../contracts/trust/types/work-mode";

/**
 * A single resolved work context as returned by
 * `GET /internal/v1/work-context/resolved` (mirrors experience-bff's
 * WorkContextController#toContextView / ResolvedWorkContext.java field-for-field).
 */
export interface ResolvedWorkContextView {
  contextId: string;
  contextKind: "facility" | "organisation" | "jurisdiction" | "programme" | "regulator" | "virtual" | "support" | string;
  sourceSystem: "VASHANDI" | "WGV" | "ORG_REGISTRY" | "TUSO" | "SUPPORT" | string;
  facilityId?: string | null;
  organisationId?: string | null;
  jurisdictionCode?: string | null;
  programmeId?: string | null;
  departmentId?: string | null;
  roleTemplateId?: string | null;
  availableModes: WorkMode[];
  defaultMode?: WorkMode | null;
  modeSource?: "CATALOG" | "DERIVED_REMOTE" | "DERIVED_LOCAL" | string;
  restrictions: string[];
  label: string;
  groupHint: "today" | "regular" | "virtual" | "oversight" | "other" | "personal" | string;
  rankScore?: number;
}

export interface WorkContextSourceStatus {
  system: string;
  state: "LIVE" | "EMPTY" | "DEGRADED" | string;
  message?: string;
}

export interface ResolvedWorkContextsAttributes {
  contexts: ResolvedWorkContextView[];
  sourceStatuses: WorkContextSourceStatus[];
  recommendedContextId?: string | null;
  requiresContextChooser: boolean;
  friendlyResolutionState?: string;
}

/** Attributes of the `work-mode-session` resource returned by session/mode mint. */
export interface WorkModeSessionAttributes {
  token?: string;
  jti?: string;
  expiresAt?: string | null;
  contextId?: string;
  workMode?: string;
}
