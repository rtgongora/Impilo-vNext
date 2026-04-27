/**
 * @impilo/mobile-design-system — Shared Design System
 *
 * Design tokens, UI primitives, layout, forms, clinical components,
 * and feedback states for all Impilo mobile apps.
 */

// Design Tokens
export { colors } from "./tokens/colors";
export type { ColorToken } from "./tokens/colors";
export { spacing } from "./tokens/spacing";
export type { SpacingToken } from "./tokens/spacing";
export { typography, textStyles } from "./tokens/typography";
export type { TextStyle } from "./tokens/typography";
export { borderRadius, shadows, tokens } from "./tokens/index";

// Theme
export { ThemeProvider, useTheme } from "./theme/ThemeProvider";
export type { Theme, ThemeMode } from "./theme/ThemeProvider";

// Core Components
export { Button } from "./components/Button";
export type { ButtonProps, ButtonVariant, ButtonSize } from "./components/Button";
export { Card, CardHeader, CardBody, CardFooter } from "./components/Card";
export type { CardProps, CardHeaderProps, CardBodyProps, CardFooterProps } from "./components/Card";
export { Badge } from "./components/Badge";
export type { BadgeProps, BadgeVariant, BadgeSize } from "./components/Badge";
export { Progress } from "./components/Progress";
export type { ProgressProps } from "./components/Progress";
export { StatusIndicator } from "./components/StatusIndicator";
export type { StatusIndicatorProps, IndicatorStatus } from "./components/StatusIndicator";
export { Avatar } from "./components/Avatar";
export type { AvatarProps, AvatarSize } from "./components/Avatar";

// Form Components
export { TextField } from "./forms/TextField";
export type { TextFieldProps } from "./forms/TextField";
export { DatePicker } from "./forms/DatePicker";
export type { DatePickerProps } from "./forms/DatePicker";
export { Checkbox } from "./forms/Checkbox";
export type { CheckboxProps } from "./forms/Checkbox";
export { Select } from "./forms/Select";
export type { SelectProps, SelectOption } from "./forms/Select";
export { Switch } from "./forms/Switch";
export type { SwitchProps } from "./forms/Switch";
export { DictationAssistButton } from "./forms/DictationAssistButton";
export type { DictationAssistButtonProps } from "./forms/DictationAssistButton";

// Clinical Components
export { VitalCard } from "./clinical/VitalCard";
export type { VitalCardProps } from "./clinical/VitalCard";
export { DiagnosisBadge } from "./clinical/DiagnosisBadge";
export type { DiagnosisBadgeProps } from "./clinical/DiagnosisBadge";
export { RxCard } from "./clinical/RxCard";
export type { RxCardProps, RxStatus } from "./clinical/RxCard";
export { LabResultCard } from "./clinical/LabResultCard";
export type { LabResultCardProps, LabResultStatus } from "./clinical/LabResultCard";

// Feedback Components
export { LoadingSpinner } from "./feedback/LoadingSpinner";
export type { LoadingSpinnerProps } from "./feedback/LoadingSpinner";
export { EmptyState } from "./feedback/EmptyState";
export type { EmptyStateProps } from "./feedback/EmptyState";
export { ErrorState } from "./feedback/ErrorState";
export type { ErrorStateProps } from "./feedback/ErrorState";
export { SkeletonLoader } from "./feedback/SkeletonLoader";
export type { SkeletonLoaderProps } from "./feedback/SkeletonLoader";

// Layout Components
export { Screen } from "./layout/Screen";
export type { ScreenProps } from "./layout/Screen";
export { Header } from "./layout/Header";
export type { HeaderProps } from "./layout/Header";
export { BottomSheet } from "./layout/BottomSheet";
export type { BottomSheetProps } from "./layout/BottomSheet";
export { TabBar } from "./layout/TabBar";
export type { TabBarProps, TabItem } from "./layout/TabBar";
