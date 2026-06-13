import React from "react";

export interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: "primary" | "secondary" | "danger" | "ghost";
  size?: "sm" | "md" | "lg";
}

const variantStyles: Record<string, string> = {
  primary:
    "bg-primary text-primary-foreground hover:bg-primary-hover focus:ring-primary/30 shadow-impilo-card",
  secondary:
    "bg-card text-muted-foreground border border-border hover:bg-primary-soft hover:text-foreground hover:border-primary focus:ring-primary/20",
  danger:
    "bg-danger text-danger-foreground hover:opacity-90 focus:ring-danger/30 shadow-impilo-card",
  ghost:
    "bg-transparent text-foreground hover:bg-primary-soft",
};

const sizeStyles: Record<string, string> = {
  sm: "px-3 py-1.5 text-xs",
  md: "px-4 py-2 text-sm",
  lg: "px-6 py-3 text-base",
};

export function Button({
  variant = "primary",
  size = "md",
  className = "",
  children,
  ...props
}: ButtonProps) {
  return (
    <button
      className={`inline-flex items-center justify-center rounded-full font-semibold transition-colors
        focus:outline-none focus:ring-2 focus:ring-offset-2 disabled:opacity-50 disabled:cursor-not-allowed
        ${variantStyles[variant]} ${sizeStyles[size]} ${className}`}
      {...props}
    >
      {children}
    </button>
  );
}
