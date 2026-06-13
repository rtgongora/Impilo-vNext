/**
 * Impilo vNext — Shared UI Component Library
 *
 * Re-exports all shared components, hooks, and utilities.
 * Consumed by workspace apps via: import { ... } from "shared-ui";
 */

// Design tokens are imported via CSS: @import "shared-ui/tokens.css";

// Trust layer contracts (shared across all apps)
export { TRUST_HEADERS } from "./lib/contracts";
export type {
  PurposeOfUse,
  ActorType,
  ApiEnvelope,
  PagedResponse,
} from "./lib/contracts";

// Components
export { Button } from "./components/Button";
export type { ButtonProps } from "./components/Button";

export { Card, CardHeader, CardTitle } from "./components/Card";
export type { CardProps } from "./components/Card";

export { Badge } from "./components/Badge";
export type { BadgeProps } from "./components/Badge";

export { StatusBadge } from "./components/StatusBadge";
export type { StatusBadgeProps, StatusBadgeVariant } from "./components/StatusBadge";

export { AlertBanner } from "./components/AlertBanner";
export type { AlertBannerProps, AlertBannerVariant } from "./components/AlertBanner";

export { ServiceCard } from "./components/ServiceCard";
export type { ServiceCardProps } from "./components/ServiceCard";

export { WorkspaceHero } from "./components/WorkspaceHero";
export type { WorkspaceHeroProps } from "./components/WorkspaceHero";

export { BrandedPageShell } from "./components/BrandedPageShell";
export type { BrandedPageShellProps } from "./components/BrandedPageShell";

export { NompiloTipCard } from "./components/NompiloTipCard";
export type { NompiloTipCardProps } from "./components/NompiloTipCard";

export { StatusIndicator } from "./components/StatusIndicator";
export type { StatusIndicatorProps } from "./components/StatusIndicator";

export { DataTable } from "./components/DataTable";
export type { DataTableProps, Column } from "./components/DataTable";

// Voice dictation — shared contracts (implementations live in shells / apps)
export type {
  DictationAuditMetadata,
  DictationConfig,
  DictationError,
  DictationErrorCode,
  DictationLanguage,
  DictationProvider,
  DictationSession,
  TranscriptionAlternative,
  TranscriptionConfidence,
  TranscriptionResult,
} from "./dictation";
export { createNoopDictationProvider } from "./dictation";

// Finance — COSTA tariff library grouping (Experience + One UI Shell)
export {
  REFERENCE_TARIFF_WARNING,
  ZIMBABWE_POC_DISCLAIMER,
  parseTariffMetadata,
  classifyTariffList,
  groupTariffLists,
  sectionCatalog,
} from "./lib/finance/tariff-library-groups";
export type { CostaTariffListRow, TariffListSectionKey } from "./lib/finance/tariff-library-groups";

// Tailwind preset (for workspace tailwind.config.ts)
export { default as impiloPreset } from "./tailwind-preset";
