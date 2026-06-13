import React from "react";

export interface BadgeProps {
  children: React.ReactNode;
  variant?: "default" | "success" | "warning" | "danger" | "info";
  className?: string;
}

const variantStyles: Record<string, string> = {
  default: "bg-neutral-100 text-foreground border-border",
  success: "bg-success-soft text-primary-hover border-success/25",
  warning: "bg-warning-soft text-warning-foreground border-warning/35",
  danger: "bg-danger-soft text-danger border-danger/28",
  info: "bg-info-soft text-primary-hover border-info/25",
};

export function Badge({ children, variant = "default", className = "" }: BadgeProps) {
  return (
    <span
      className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium border
        ${variantStyles[variant]} ${className}`}
    >
      {children}
    </span>
  );
}
