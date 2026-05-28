"use client";

import Link from "next/link";
import { Shield } from "lucide-react";
import { useExperienceEntry } from "@/providers/ExperienceEntryProvider";

export function TrustContextBanner(props: { purposeOfUse?: string }) {
  const { facility, workspace } = useExperienceEntry();

  return (
    <div
      data-testid="trust-context-banner"
      className="impilo-surface-soft impilo-subtle-african-accent flex flex-wrap items-center gap-2 px-3 py-2 text-xs"
    >
      <Shield className="h-3.5 w-3.5 text-impilo-600" />
      <span className="font-medium text-[var(--text-primary)]">Trust context</span>
      {facility?.name ? (
        <span className="text-[var(--text-secondary)]">Facility: {facility.name}</span>
      ) : null}
      {workspace?.name ? (
        <span className="text-[var(--text-secondary)]">Workspace: {workspace.name}</span>
      ) : null}
      {props.purposeOfUse ? (
        <span className="text-[var(--text-secondary)]">Purpose: {props.purposeOfUse}</span>
      ) : null}
      <Link href="/registry/trust" className="ml-auto font-medium text-impilo-600 hover:underline">
        Trust admin
      </Link>
    </div>
  );
}
