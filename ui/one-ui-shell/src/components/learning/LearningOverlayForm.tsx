"use client";

import { X } from "lucide-react";

interface LearningOverlayFormProps {
  title: string;
  subtitle?: string;
  open: boolean;
  onClose?: () => void;
  children: React.ReactNode;
  footer?: React.ReactNode;
}

export function LearningOverlayForm({
  title,
  subtitle,
  open,
  onClose,
  children,
  footer,
}: LearningOverlayFormProps) {
  if (!open) return null;

  return (
    <div className="fixed inset-0 z-[10020] flex items-center justify-center p-4">
      {onClose ? (
        <button
          type="button"
          className="absolute inset-0 cursor-default bg-slate-950/25"
          aria-label="Close form overlay"
          onClick={onClose}
        />
      ) : (
        <div className="absolute inset-0 bg-slate-950/25" />
      )}
      <section
        role="dialog"
        aria-modal="true"
        aria-label={title}
        className="relative flex max-h-[min(90vh,760px)] w-full max-w-4xl flex-col rounded-lg border border-gray-200 bg-white shadow-2xl"
      >
        <div className="flex items-start justify-between gap-4 border-b border-gray-100 px-5 py-4">
          <div className="min-w-0">
            <h2 className="text-lg font-semibold text-gray-900">{title}</h2>
            {subtitle ? <p className="mt-1 text-sm text-gray-600">{subtitle}</p> : null}
          </div>
          {onClose ? (
            <button
              type="button"
              onClick={onClose}
              className="rounded-lg border border-gray-300 p-2 text-gray-600 hover:bg-gray-50"
              aria-label="Close form"
            >
              <X className="h-4 w-4" />
            </button>
          ) : null}
        </div>
        <div className="flex-1 overflow-y-visible px-5 py-4">{children}</div>
        {footer ? <div className="border-t border-gray-100 px-5 py-4">{footer}</div> : null}
      </section>
    </div>
  );
}
