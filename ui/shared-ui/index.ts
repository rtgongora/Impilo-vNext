/**
 * Impilo vNext — Shared UI Component Library
 *
 * Re-exports all shared components, hooks, and utilities.
 * Consumed by workspace apps via: import { ... } from "shared-ui";
 */

// Design tokens are imported via CSS: @import "shared-ui/tokens.css";

// Components
export { Button } from "./components/Button";
export type { ButtonProps } from "./components/Button";

export { Card, CardHeader, CardTitle } from "./components/Card";
export type { CardProps } from "./components/Card";

export { Badge } from "./components/Badge";
export type { BadgeProps } from "./components/Badge";

export { StatusIndicator } from "./components/StatusIndicator";
export type { StatusIndicatorProps } from "./components/StatusIndicator";

export { DataTable } from "./components/DataTable";
export type { DataTableProps, Column } from "./components/DataTable";
