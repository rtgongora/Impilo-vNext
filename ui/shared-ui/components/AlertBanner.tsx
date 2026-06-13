import React from "react";

export type AlertBannerVariant = "info" | "success" | "warning" | "pending" | "danger" | "critical";

export interface AlertBannerProps {
  title?: string;
  children: React.ReactNode;
  variant?: AlertBannerVariant;
  icon?: React.ReactNode;
  className?: string;
  onDismiss?: () => void;
}

const variantStyles: Record<AlertBannerVariant, { container: string; icon: string; bar: string }> = {
  info: {
    container: "bg-info-soft border-info/20 text-primary-hover",
    icon: "bg-primary/10 text-primary",
    bar: "bg-primary",
  },
  success: {
    container: "bg-success-soft border-success/20 text-primary-hover",
    icon: "bg-primary/10 text-primary",
    bar: "bg-primary",
  },
  warning: {
    container: "bg-warning-soft border-warning/30 text-warning-foreground",
    icon: "bg-warning/20 text-warning-foreground",
    bar: "bg-warning",
  },
  pending: {
    container: "bg-warning-soft border-warning/30 text-warning-foreground",
    icon: "bg-warning/20 text-warning-foreground",
    bar: "bg-warning",
  },
  danger: {
    container: "bg-danger-soft border-danger/25 text-danger",
    icon: "bg-danger/10 text-danger",
    bar: "bg-danger",
  },
  critical: {
    container: "bg-danger-soft border-danger/25 text-danger",
    icon: "bg-danger/10 text-danger",
    bar: "bg-danger",
  },
};

export function AlertBanner({
  title,
  children,
  variant = "info",
  icon,
  className = "",
  onDismiss,
}: AlertBannerProps) {
  const styles = variantStyles[variant];

  return (
    <div
      role="alert"
      className={`relative flex gap-3 overflow-hidden rounded-2xl border p-4 ${styles.container} ${className}`}
    >
      <div className={`absolute left-0 top-0 h-full w-1 ${styles.bar}`} aria-hidden />
      {icon ? (
        <div className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-xl ${styles.icon}`}>
          {icon}
        </div>
      ) : null}
      <div className="min-w-0 flex-1 pl-2">
        {title ? <p className="text-sm font-semibold">{title}</p> : null}
        <div className={`text-sm ${title ? "mt-1" : ""}`}>{children}</div>
      </div>
      {onDismiss ? (
        <button
          type="button"
          onClick={onDismiss}
          className="shrink-0 rounded-lg px-2 py-1 text-xs font-medium opacity-70 hover:opacity-100"
          aria-label="Dismiss alert"
        >
          Dismiss
        </button>
      ) : null}
    </div>
  );
}
