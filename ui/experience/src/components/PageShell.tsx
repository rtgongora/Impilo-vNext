"use client";

/**
 * PageShell — Standard page wrapper with title and empty state.
 * Used by all route pages to display correct headings/labels from 02.
 */

interface PageShellProps {
  title: string;
  subtitle?: string;
  icon?: React.ReactNode;
  emptyStateLabel?: string;
  children?: React.ReactNode;
}

export function PageShell({ title, subtitle, icon, emptyStateLabel, children }: PageShellProps) {
  return (
    <div>
      <div className="mb-6">
        <div className="flex items-center gap-3">
          {icon && <div className="text-gray-500">{icon}</div>}
          <h1 className="text-2xl font-bold text-gray-900">{title}</h1>
        </div>
        {subtitle && <p className="text-sm text-gray-500 mt-1">{subtitle}</p>}
      </div>
      {children || (
        <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
          <p className="text-gray-400 text-sm">
            {emptyStateLabel || "No data available"}
          </p>
        </div>
      )}
    </div>
  );
}
