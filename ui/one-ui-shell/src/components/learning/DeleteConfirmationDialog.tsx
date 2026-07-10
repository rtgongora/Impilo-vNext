"use client";

import { AlertCircle, X } from "lucide-react";
import { ReactNode } from "react";

export function DeleteConfirmationDialog({
  title,
  message,
  onConfirm,
  onCancel,
  confirmText = "Delete",
  cancelText = "Cancel",
}: {
  title: string;
  message: string | ReactNode;
  onConfirm: () => void;
  onCancel: () => void;
  confirmText?: string;
  cancelText?: string;
}) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm">
      <div className="relative w-full max-w-md rounded-lg bg-white shadow-lg animate-in fade-in zoom-in-95 duration-200">
        {/* Close button */}
        <button
          onClick={onCancel}
          className="absolute right-3 top-3 inline-flex h-7 w-7 items-center justify-center rounded-md hover:bg-slate-100 transition text-slate-400"
          title="Close dialog"
        >
          <X className="h-4 w-4" />
        </button>

        {/* Content */}
        <div className="p-6">
          {/* Header with icon */}
          <div className="flex items-start gap-3 mb-4">
            <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-red-50">
              <AlertCircle className="h-5 w-5 text-red-600" />
            </div>
            <div className="flex-1">
              <h2 className="text-lg font-semibold text-slate-950">{title}</h2>
            </div>
          </div>

          {/* Message */}
          <p className="mb-6 text-sm text-slate-600 leading-6">{message}</p>

          {/* Actions */}
          <div className="flex gap-2 justify-end">
            <button
              onClick={onCancel}
              className="inline-flex h-9 items-center justify-center rounded-md border border-slate-200 bg-white px-4 text-sm font-semibold text-slate-700 hover:bg-slate-50 transition"
            >
              {cancelText}
            </button>
            <button
              onClick={onConfirm}
              className="inline-flex h-9 items-center justify-center rounded-md border border-red-300 bg-red-600 px-4 text-sm font-semibold text-white hover:bg-red-700 transition"
            >
              {confirmText}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
