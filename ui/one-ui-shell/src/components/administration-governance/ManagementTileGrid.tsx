"use client";

import Link from "next/link";
import { ArrowUpRight } from "lucide-react";
import type { AdministrationTile } from "@/lib/administration-governance";
import { tileBlockedReason, tileEnabled } from "@/lib/administration-governance";
import type { SessionExperienceContract } from "@/lib/trust";
import { resolveFriendlyStateMessage } from "@/lib/administration-governance";

interface ManagementTileGridProps {
  contract: SessionExperienceContract;
  tiles: AdministrationTile[];
}

export function ManagementTileGrid({ contract, tiles }: ManagementTileGridProps) {
  return (
    <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
      {tiles.map((tile) => {
        const enabled = tileEnabled(contract, tile.requiredWorkspaces);
        const blocked = tileBlockedReason(contract, tile.blockedWorkspaceHint);
        const Icon = tile.icon;

        if (!enabled && !blocked) return null;

        const blockedMsg = blocked ? resolveFriendlyStateMessage(blocked) : null;

        if (!enabled && blocked) {
          return (
            <div
              key={tile.id}
              className="flex flex-col rounded-2xl border border-dashed border-slate-300 bg-slate-50 p-5 opacity-90"
              aria-disabled
            >
              <div className="flex items-start gap-3">
                <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-slate-200 text-slate-600">
                  <Icon className="h-5 w-5" />
                </div>
                <div>
                  <h4 className="font-medium text-slate-700">{tile.title}</h4>
                  <p className="mt-1 text-sm text-slate-600">{blockedMsg?.description ?? tile.description}</p>
                  <p className="mt-2 text-xs font-medium text-amber-800">{blockedMsg?.title ?? "Not available in your scope"}</p>
                </div>
              </div>
            </div>
          );
        }

        return (
          <Link
            key={tile.id}
            href={tile.href}
            className="group flex flex-col rounded-2xl border border-slate-200 bg-white p-5 shadow-sm transition hover:border-indigo-300 hover:shadow-md"
          >
            <div className="flex items-start justify-between gap-3">
              <div className="flex items-start gap-3">
                <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-indigo-100 text-indigo-800">
                  <Icon className="h-5 w-5" />
                </div>
                <div>
                  <h4 className="font-medium text-slate-900 group-hover:text-indigo-900">{tile.title}</h4>
                  <p className="mt-1 text-sm text-slate-600">{tile.description}</p>
                </div>
              </div>
              <ArrowUpRight className="h-4 w-4 shrink-0 text-slate-400 group-hover:text-indigo-700" />
            </div>
          </Link>
        );
      })}
    </div>
  );
}
