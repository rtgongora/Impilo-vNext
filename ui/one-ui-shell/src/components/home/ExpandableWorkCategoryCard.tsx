"use client";

import { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import type { LucideIcon } from "lucide-react";
import { Lock } from "lucide-react";
import { cn } from "@/lib/accessibility";

export interface WorkCategoryModule {
  id: string;
  label: string;
  description: string;
  href: string;
  icon: LucideIcon;
  color: string;
  /** When true, show a small lock hint (restricted module). */
  restricted?: boolean;
}

interface ExpandableWorkCategoryCardProps {
  id: string;
  title: string;
  description: string;
  modules: WorkCategoryModule[];
  icon: LucideIcon;
  color: string;
  categoryRestricted?: boolean;
}

export function ExpandableWorkCategoryCard({
  title,
  description,
  modules,
  icon: Icon,
  color,
  categoryRestricted,
}: ExpandableWorkCategoryCardProps) {
  const router = useRouter();
  const [open, setOpen] = useState(false);

  const onClose = useCallback(() => setOpen(false), []);

  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [open, onClose]);

  return (
    <>
      <button
        type="button"
        onClick={() => setOpen(true)}
        className="h-full w-full cursor-pointer rounded-lg border border-gray-200 bg-white p-4 text-left shadow-sm transition-all hover:border-impilo-200 hover:shadow-md"
      >
        <div className="flex h-full items-center gap-4">
          <div
            className={cn(
              "flex h-12 w-12 shrink-0 items-center justify-center rounded-xl text-white transition-transform group-hover:scale-105",
              color,
            )}
          >
            <Icon className="h-6 w-6 text-white" />
          </div>
          <div className="min-w-0 flex-1">
            <p className="truncate text-lg font-semibold leading-tight text-gray-900">{title}</p>
            <span className="text-base text-gray-500">{modules.length} modules</span>
          </div>
        </div>
      </button>

      {open && (
        <div className="fixed inset-0 z-[100] flex items-center justify-center p-4">
          <button
            type="button"
            className="absolute inset-0 bg-black/40"
            aria-label="Close dialog"
            onClick={onClose}
          />
          <div
            role="dialog"
            aria-modal="true"
            aria-labelledby={`work-cat-${title.replace(/\s+/g, "-")}`}
            className="relative z-[101] max-h-[80vh] w-full max-w-2xl overflow-y-auto rounded-xl border border-gray-200 bg-white p-6 shadow-xl"
          >
            <div className="flex items-start gap-3 border-b border-gray-100 pb-4">
              <div className={cn("flex h-10 w-10 shrink-0 items-center justify-center rounded-lg text-white", color)}>
                <Icon className="h-5 w-5 text-white" />
              </div>
              <div>
                <h2 id={`work-cat-${title.replace(/\s+/g, "-")}`} className="text-lg font-semibold text-gray-900">
                  {title}
                </h2>
                <p className="text-sm text-gray-500">{description}</p>
              </div>
            </div>

            <div className="mt-4 grid grid-cols-2 gap-2 sm:grid-cols-3">
              {modules.map((module) => {
                const ModIcon = module.icon;
                return (
                  <button
                    key={module.id}
                    type="button"
                    onClick={() => {
                      router.push(module.href);
                      onClose();
                    }}
                    className="cursor-pointer rounded-lg border border-gray-200 bg-gray-50 p-3 text-left transition-all hover:border-impilo-200 hover:bg-white hover:shadow-sm"
                  >
                    <div className="flex items-center gap-2">
                      <div
                        className={cn(
                          "flex h-8 w-8 shrink-0 items-center justify-center rounded-lg text-white",
                          module.color,
                        )}
                      >
                        <ModIcon className="h-4 w-4 text-white" />
                      </div>
                      <div className="min-w-0 flex-1">
                        <p className="truncate text-sm font-medium text-gray-900">{module.label}</p>
                        <p className="truncate text-xs text-gray-500">{module.description}</p>
                      </div>
                      {(module.restricted || categoryRestricted) && (
                        <Lock className="h-3 w-3 shrink-0 text-gray-400" aria-hidden />
                      )}
                    </div>
                  </button>
                );
              })}
            </div>

            <div className="mt-6 flex justify-end">
              <button
                type="button"
                onClick={onClose}
                className="rounded-lg border border-gray-200 px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50"
              >
                Close
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
