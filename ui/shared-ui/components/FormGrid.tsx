import React from "react";

/**
 * Responsive form grid — treats horizontal space as an operational resource.
 * Single column on phones, `columns` at ≥sm/≥lg. Short fields sit side by side;
 * long narrative fields span the full width via <FormField span="full">.
 */
export interface FormGridProps {
  children: React.ReactNode;
  className?: string;
  /** Maximum columns on wide viewports. Phones always collapse to one column. */
  columns?: 2 | 3 | 4;
  /** `compact` tightens gaps for dense operational forms. */
  gap?: "default" | "compact";
}

const columnStyles: Record<number, string> = {
  2: "sm:grid-cols-2",
  3: "sm:grid-cols-2 lg:grid-cols-3",
  4: "sm:grid-cols-2 lg:grid-cols-4",
};

const gapStyles: Record<string, string> = {
  default: "gap-x-4 gap-y-3",
  compact: "gap-x-3 gap-y-2",
};

export function FormGrid({ children, className = "", columns = 2, gap = "default" }: FormGridProps) {
  return (
    <div className={`grid grid-cols-1 ${columnStyles[columns]} ${gapStyles[gap]} ${className}`}>
      {children}
    </div>
  );
}

export interface FormFieldProps {
  children: React.ReactNode;
  className?: string;
  /** Columns this field spans on wide viewports; `full` for narrative fields. */
  span?: 1 | 2 | 3 | "full";
  /** Optional consistent label rendering (pairs with `htmlFor`). */
  label?: React.ReactNode;
  htmlFor?: string;
  required?: boolean;
  /** Short helper text under the control. */
  hint?: React.ReactNode;
  /** Validation error — announced to screen readers via role=alert. */
  error?: React.ReactNode;
}

const spanStyles: Record<string, string> = {
  1: "",
  2: "sm:col-span-2",
  3: "sm:col-span-2 lg:col-span-3",
  full: "col-span-full",
};

export function FormField({
  children,
  className = "",
  span = 1,
  label,
  htmlFor,
  required = false,
  hint,
  error,
}: FormFieldProps) {
  return (
    <div className={`min-w-0 ${spanStyles[span]} ${className}`}>
      {label !== undefined && label !== null ? (
        <label
          htmlFor={htmlFor}
          className="mb-1 block text-xs font-medium text-[color:var(--text-secondary)]"
        >
          {label}
          {required ? (
            <span aria-hidden="true" className="ml-0.5 text-[color:var(--danger)]">
              *
            </span>
          ) : null}
        </label>
      ) : null}
      {children}
      {hint && !error ? (
        <p className="mt-1 text-[11px] leading-4 text-[color:var(--text-muted)]">{hint}</p>
      ) : null}
      {error ? (
        <p role="alert" className="mt-1 text-[11px] font-medium leading-4 text-[color:var(--danger)]">
          {error}
        </p>
      ) : null}
    </div>
  );
}

export interface FormSectionProps {
  children: React.ReactNode;
  className?: string;
  title: React.ReactNode;
  description?: React.ReactNode;
  /** Actions rendered inline with the section title (e.g. "Add another"). */
  actions?: React.ReactNode;
}

/**
 * Groups related fields with a subtle boundary instead of a giant card.
 */
export function FormSection({ children, className = "", title, description, actions }: FormSectionProps) {
  return (
    <section className={`border-t border-[color:var(--border-soft)] pt-3 first:border-t-0 first:pt-0 ${className}`}>
      <div className="mb-2 flex flex-wrap items-baseline justify-between gap-2">
        <div className="min-w-0">
          <h3 className="text-sm font-semibold text-[color:var(--text-primary)]">{title}</h3>
          {description ? (
            <p className="mt-0.5 text-xs leading-5 text-[color:var(--text-muted)]">{description}</p>
          ) : null}
        </div>
        {actions ? <div className="shrink-0">{actions}</div> : null}
      </div>
      {children}
    </section>
  );
}
