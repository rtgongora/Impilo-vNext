import React from "react";

export interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: "primary" | "secondary" | "danger" | "ghost";
  size?: "sm" | "md" | "lg";
}

const variantStyles: Record<string, string> = {
  primary:
    "bg-brand-primary text-white hover:bg-brand-primary/90 focus:ring-brand-primary/30",
  secondary:
    "bg-neutral-100 text-neutral-900 hover:bg-neutral-100/80 border border-neutral-500/20",
  danger:
    "bg-danger text-white hover:bg-danger/90 focus:ring-danger/30",
  ghost:
    "bg-transparent text-neutral-900 hover:bg-neutral-100",
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
      className={`inline-flex items-center justify-center font-medium rounded-[12px] transition-colors
        focus:outline-none focus:ring-2 focus:ring-offset-2 disabled:opacity-50 disabled:cursor-not-allowed
        ${variantStyles[variant]} ${sizeStyles[size]} ${className}`}
      {...props}
    >
      {children}
    </button>
  );
}
