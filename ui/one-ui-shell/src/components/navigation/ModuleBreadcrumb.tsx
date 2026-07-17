"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { ChevronLeft, ChevronRight } from "lucide-react";
import { buildBreadcrumbTrail } from "@/lib/routes";

export function ModuleBreadcrumb() {
  const pathname = usePathname();
  const trail = buildBreadcrumbTrail(pathname ?? "/home");
  if (trail.length <= 1) return null;

  const current = trail[trail.length - 1];
  const parent = trail.length >= 2 ? trail[trail.length - 2] : null;

  return (
    <>
      {/* Desktop / tablet: full trail. */}
      <nav aria-label="Breadcrumb" className="hidden min-w-0 flex-1 items-center gap-1 text-xs text-muted-foreground md:flex">
        {trail.map((crumb, i) => {
          const last = i === trail.length - 1;
          return (
            <span key={crumb.href} className="flex min-w-0 items-center gap-1">
              {i > 0 ? <ChevronRight className="h-3.5 w-3.5 shrink-0 text-muted-foreground" aria-hidden /> : null}
              {last ? (
                <span className="truncate font-medium text-foreground" aria-current="page">
                  {crumb.label}
                </span>
              ) : (
                <Link href={crumb.href} className="truncate hover:text-primary">
                  {crumb.label}
                </Link>
              )}
            </span>
          );
        })}
      </nav>

      {/* Mobile: compact current-location with a tap-back to the parent crumb. */}
      <nav aria-label="Breadcrumb" className="flex min-w-0 flex-1 items-center gap-1 text-xs text-muted-foreground md:hidden">
        {parent ? (
          <Link
            href={parent.href}
            className="flex shrink-0 items-center gap-0.5 hover:text-primary"
            title={`Back to ${parent.label}`}
          >
            <ChevronLeft className="h-3.5 w-3.5 shrink-0" aria-hidden />
            <span className="max-w-[6rem] truncate">{parent.label}</span>
          </Link>
        ) : null}
        {parent ? <ChevronRight className="h-3.5 w-3.5 shrink-0 text-muted-foreground" aria-hidden /> : null}
        <span className="truncate font-medium text-foreground" aria-current="page">
          {current.label}
        </span>
      </nav>
    </>
  );
}
