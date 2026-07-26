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

export { GlassSurface } from "./components/GlassSurface";
export type { GlassSurfaceProps } from "./components/GlassSurface";

export { CinematicStage } from "./components/CinematicStage";
export type { CinematicStageProps } from "./components/CinematicStage";

export { LuminousStage } from "./components/LuminousStage";
export type { LuminousStageProps } from "./components/LuminousStage";

export { TierProvider, useTier, resolveTier } from "./hooks/useTier";
export type { Tier } from "./hooks/useTier";

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

// Adaptive workspace / responsive layout primitives
export { FormGrid, FormField, FormSection } from "./components/FormGrid";
export type { FormGridProps, FormFieldProps, FormSectionProps } from "./components/FormGrid";

export { StickyActionBar } from "./components/StickyActionBar";
export type { StickyActionBarProps } from "./components/StickyActionBar";

export { SegmentedControl } from "./components/SegmentedControl";
export type { SegmentedControlProps, SegmentedControlOption } from "./components/SegmentedControl";
export { Stepper } from "./components/Stepper";
export type { StepperProps, StepperStep } from "./components/Stepper";

export { MoreBelow } from "./components/MoreBelow";
export type { MoreBelowProps } from "./components/MoreBelow";

export { SplitView } from "./components/SplitView";
export type { SplitViewProps } from "./components/SplitView";

export { FullHeightWorkspace, WorkspaceScrollPane } from "./components/FullHeightWorkspace";
export type { FullHeightWorkspaceProps, WorkspaceScrollPaneProps } from "./components/FullHeightWorkspace";

export { AdaptiveGrid } from "./components/AdaptiveGrid";
export type { AdaptiveGridProps } from "./components/AdaptiveGrid";

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
