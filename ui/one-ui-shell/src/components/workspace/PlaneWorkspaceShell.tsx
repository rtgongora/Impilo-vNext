"use client";

import type { ReactNode } from "react";
import Link from "next/link";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { FeatureMaturityBadge } from "@/components/FeatureMaturityBadge";
import { ContextRail } from "./ContextRail";
import { RelatedServicesPanel, type RelatedServiceLink } from "./RelatedServicesPanel";
import { NompiloContextPanel } from "@/components/intelligent/NompiloContextPanel";

export interface PlaneTab {
  label: string;
  href: string;
}

export function PlaneWorkspaceShell(props: {
  title: string;
  subtitle?: string;
  plane: string;
  serviceSlug?: string;
  maturity?: "live" | "partial" | "not_wired" | "blocked";
  maturityDetail?: string;
  tabs?: PlaneTab[];
  relatedLinks?: RelatedServiceLink[];
  contextPatient?: string;
  contextEncounter?: string;
  contextTransaction?: string;
  nompiloHint?: string;
  children: ReactNode;
  aside?: ReactNode;
}) {
  return (
    <AppLayout>
      <PageShell title={props.title} subtitle={props.subtitle} serviceSlug={props.serviceSlug}>
        <div className="mb-4 flex flex-wrap items-center gap-2">
          <span className="impilo-chip bg-[var(--primary-soft)] text-primary-hover">{props.plane}</span>
          {props.maturity ? (
            <FeatureMaturityBadge status={props.maturity} detail={props.maturityDetail} />
          ) : null}
        </div>

        {props.tabs && props.tabs.length > 0 ? (
          <nav className="mb-4 flex flex-wrap gap-1 border-b border-[var(--border-soft)] pb-2">
            {props.tabs.map((tab) => (
              <Link
                key={tab.href}
                href={tab.href}
                className="rounded-full px-3 py-1.5 text-sm font-medium text-[var(--text-secondary)] transition hover:bg-[var(--primary-soft)] hover:text-primary-hover"
              >
                {tab.label}
              </Link>
            ))}
          </nav>
        ) : null}

        <div className="grid gap-6 lg:grid-cols-[1fr_280px]">
          <div className="min-w-0 space-y-4">{props.children}</div>
          <div className="space-y-4">
            <ContextRail
              patientLabel={props.contextPatient}
              encounterLabel={props.contextEncounter}
              transactionLabel={props.contextTransaction}
            />
            {props.aside}
            {props.relatedLinks ? <RelatedServicesPanel links={props.relatedLinks} /> : null}
            <NompiloContextPanel hint={props.nompiloHint} plane={props.plane} />
          </div>
        </div>
      </PageShell>
    </AppLayout>
  );
}
