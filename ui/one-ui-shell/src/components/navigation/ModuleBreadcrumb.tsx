"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { ChevronRight } from "lucide-react";
import { buildBreadcrumbTrail } from "@/lib/routes";

export function ModuleBreadcrumb() {
  const pathname = usePathname();
  const trail = buildBreadcrumbTrail(pathname ?? "/home");
  if (trail.length <= 1) return null;

  return (
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
  );
}
