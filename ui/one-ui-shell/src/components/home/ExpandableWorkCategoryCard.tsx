"use client";

import { useCallback, useEffect, useId, useState, type ComponentType } from "react";
import Link from "next/link";
import { Lock } from "lucide-react";
import { ModuleCardIcon } from "@/components/branding/ModuleCardIcon";
import { cn } from "@/lib/accessibility";

export interface WorkCategoryModule {
  id: string;
  label: string;
  description: string;
  href: string;
  icon: ComponentType<{ className?: string }>;
  color: string;
  serviceSlug?: string;
  restricted?: boolean;
}

export interface WorkCategoryCardProps {
  id: string;
  title: string;
  description?: string;
  modules: WorkCategoryModule[];
  icon: ComponentType<{ className?: string }>;
  color: string;
  categoryRestricted?: boolean;
}

export function ExpandableWorkCategoryCard({
  id,
  title,
  description,
  modules,
  icon: Icon,
  color,
  categoryRestricted,
}: WorkCategoryCardProps) {
  const [open, setOpen] = useState(false);
  const titleId = useId();
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
        className="h-full w-full cursor-pointer rounded-lg border border-border bg-card p-4 text-left shadow-sm transition-all hover:border-primary/25 hover:shadow-md"
      >
        <div className="flex h-full items-center gap-3">
          <div
            className={cn(
              "flex h-9 w-9 shrink-0 items-center justify-center rounded-lg text-white",
              color,
            )}
          >
            <Icon className="h-4.5 w-4.5 text-white" />
          </div>
          <div className="min-w-0 flex-1">
            <p className="truncate text-sm font-medium text-foreground">{title}</p>
            <span className="text-xs text-muted-foreground">{modules.length} modules</span>
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
            aria-labelledby={titleId}
            className="relative z-[101] max-h-[80vh] w-full max-w-2xl overflow-y-auto rounded-xl border border-border bg-card p-6 shadow-xl"
          >
            <div className="flex items-start gap-3 border-b border-border pb-4">
              <div className={cn("flex h-10 w-10 shrink-0 items-center justify-center rounded-lg text-white", color)}>
                <Icon className="h-5 w-5 text-white" />
              </div>
              <div>
                <h2 id={titleId} className="text-lg font-semibold text-foreground">
                  {title}
                </h2>
                {description ? <p className="text-sm text-muted-foreground">{description}</p> : null}
              </div>
            </div>

            <div className="mt-4 grid grid-cols-1 gap-2 sm:grid-cols-2">
              {modules.map((module) => (
                <Link
                  key={`${id}-${module.id}-${module.href}`}
                  href={module.href}
                  onClick={onClose}
                  className="flex items-center gap-2.5 rounded-lg border border-border bg-background p-3 transition-all hover:border-primary/25 hover:bg-card hover:shadow-sm"
                >
                  <ModuleCardIcon
                    serviceSlug={module.serviceSlug}
                    icon={module.icon}
                    color={module.color}
                    size="sm"
                  />
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-medium text-foreground">{module.label}</p>
                    <p className="truncate text-xs text-muted-foreground">{module.description}</p>
                  </div>
                  {(module.restricted || categoryRestricted) && (
                    <Lock className="h-3 w-3 shrink-0 text-muted-foreground" aria-hidden />
                  )}
                </Link>
              ))}
            </div>

            <div className="mt-6 flex justify-end">
              <button
                type="button"
                onClick={onClose}
                className="rounded-lg border border-border px-4 py-2 text-sm font-medium text-foreground hover:bg-background"
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
