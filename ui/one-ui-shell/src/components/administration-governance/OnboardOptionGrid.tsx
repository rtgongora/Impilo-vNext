"use client";

import Link from "next/link";
import { ArrowUpRight, UserPlus } from "lucide-react";
import type { OnboardActorOption } from "@/lib/administration-governance";
import { tileEnabled } from "@/lib/administration-governance";
import type { SessionExperienceContract } from "@/lib/trust";

interface OnboardOptionGridProps {
  contract: SessionExperienceContract;
  options: OnboardActorOption[];
}

export function OnboardOptionGrid({ contract, options }: OnboardOptionGridProps) {
  return (
    <div className="grid gap-4 md:grid-cols-2">
      {options.map((option) => {
        const enabled = tileEnabled(contract, option.requiredWorkspaces);
        if (!enabled) return null;
        return (
          <Link
            key={option.id}
            href={option.href}
            className="group flex flex-col rounded-2xl border border-border bg-card p-5 shadow-sm transition hover:border-indigo-300 hover:shadow-md"
          >
            <div className="flex items-start justify-between gap-3">
              <div className="flex items-start gap-3">
                <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-indigo-100 text-primary-hover">
                  <UserPlus className="h-5 w-5" />
                </div>
                <div>
                  <h4 className="font-medium text-foreground group-hover:text-primary-hover">{option.title}</h4>
                  <p className="mt-1 text-sm text-muted-foreground">{option.description}</p>
                  <p className="mt-2 text-xs font-medium text-primary-hover">{option.outcome}</p>
                </div>
              </div>
              <ArrowUpRight className="h-4 w-4 shrink-0 text-muted-foreground group-hover:text-primary-hover" />
            </div>
          </Link>
        );
      })}
    </div>
  );
}
