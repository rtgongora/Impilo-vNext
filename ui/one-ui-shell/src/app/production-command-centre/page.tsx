"use client";

import Link from "next/link";
import { useMemo, useState } from "react";
import { LayoutGrid, Search } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { FeatureMaturityBadge } from "@/components/FeatureMaturityBadge";
import { TrustContextBanner } from "@/components/experience/TrustContextBanner";
import { NompiloContextPanel } from "@/components/intelligent/NompiloContextPanel";
import { ServiceLogo } from "@/components/branding/ServiceLogo";
import { RelatedServicesPanel } from "@/components/workspace/RelatedServicesPanel";
import { WorkspaceHero } from "shared-ui";
import { COMMAND_CENTRE_TILE_SLUGS } from "@/config/serviceBranding";

const COMMAND_CENTRE_RELATED_LINKS = [
  { label: "Data & intelligence", href: "/data-intelligence", description: "Quality, pipelines, integration, audit" },
  { label: "Core transactions", href: "/core-transaction", description: "Transaction state and audit feed" },
  { label: "Clinical hub", href: "/clinical", description: "Clinical and inpatient modules" },
  { label: "Rx transaction journey", href: "/pharmacy/transaction-journey", description: "Golden-path Rx demonstration" },
  { label: "Butano SHR ops", href: "/operations/butano", description: "Shared health record operations", serviceSlug: "butano" },
  { label: "Indawo sites", href: "/public-health/site-registry", description: "Public health premises registry", serviceSlug: "indawo" },
  { label: "Tshepo trust", href: "/registry/trust", description: "Trust and federation governance", serviceSlug: "tshepo" },
];
import {
  filterCommandCentreSections,
  type CommandCentrePriority,
} from "@/features/production-command-centre/tile-registry";
import { useCommandCentreHealth } from "@/features/production-command-centre/useCommandCentreHealth";
import { CommandCentreHealthChip } from "@/features/production-command-centre/CommandCentreHealthChip";
import { serviceHintForTile, uiMaturityForService } from "@/lib/maturity";

export default function ProductionCommandCentrePage() {
  const [query, setQuery] = useState("");
  const [priority, setPriority] = useState<CommandCentrePriority | "ALL">("ALL");

  const sections = useMemo(
    () => filterCommandCentreSections(query, priority === "ALL" ? undefined : priority),
    [query, priority],
  );
  const health = useCommandCentreHealth();

  return (
    <AppLayout>
      <PageShell
        title="Production Command Centre"
        subtitle="Discover, navigate, and demonstrate the Impilo Health OS — service status, demo paths, and known gaps"
        icon={<LayoutGrid className="h-6 w-6" />}
      >
        <div className="space-y-6">
          <WorkspaceHero
            title="Production Command Centre"
            subtitle="Discover, navigate, and demonstrate the Impilo Health OS — service status, demo paths, and known gaps"
            watermark
          />

          <TrustContextBanner purposeOfUse="HEALTH_OS_OPERATIONS" />

          <div className="flex flex-wrap gap-3 text-xs text-[var(--text-secondary)]">
            <span className="impilo-chip bg-card">
              Integration hub:{" "}
              {health.isLoading
                ? "checking…"
                : health.integrationHubAvailable
                  ? `live (${health.routeCount} routes)`
                  : "unavailable"}
            </span>
            <span className="impilo-chip bg-card">
              Web routes: 417 registered (route parity CI)
            </span>
          </div>

          <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
            <div className="relative flex-1">
              <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-[var(--text-muted)]" />
              <input
                type="search"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                placeholder="Search services, planes, workflows…"
                className="impilo-pill-input w-full py-2 pl-10 pr-3"
              />
            </div>
            <select
              value={priority}
              onChange={(e) => setPriority(e.target.value as CommandCentrePriority | "ALL")}
              className="impilo-pill-input py-2"
            >
              <option value="ALL">All priorities</option>
              <option value="P0">P0 only</option>
              <option value="P1">P1 only</option>
              <option value="P2">P2 only</option>
            </select>
          </div>

          <div className="grid gap-6 lg:grid-cols-[1fr_280px]">
            <div className="space-y-8">
              {sections.map((section) => (
                <section key={section.id}>
                  <div className="mb-3">
                    <h2 className="text-lg font-semibold text-[var(--text-primary)]">{section.title}</h2>
                    <p className="text-sm text-[var(--text-secondary)]">{section.description}</p>
                  </div>
                  <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
                    {section.tiles.map((tile) => {
                      const serviceHint = serviceHintForTile(tile.id, tile.integrationKeywords);
                      const registryMaturity = serviceHint ? uiMaturityForService(serviceHint) : undefined;
                      const serviceSlug = COMMAND_CENTRE_TILE_SLUGS[tile.id];
                      return (
                      <Link
                        key={tile.id}
                        href={tile.href}
                        className="impilo-surface-card group flex flex-col border-t-4 border-t-primary p-4 transition hover:border-primary/30 hover:shadow-impilo-card"
                      >
                        {serviceSlug ? (
                          <div className="mb-3">
                            <ServiceLogo slug={serviceSlug} size="card" />
                          </div>
                        ) : null}
                        <div className="mb-2 flex flex-wrap items-center gap-2">
                          <span className="text-[10px] font-semibold uppercase tracking-wide text-[var(--text-muted)]">
                            {tile.plane}
                          </span>
                          <FeatureMaturityBadge status={tile.maturity} detail={`Data: ${tile.dataMode}`} />
                          {registryMaturity ? (
                            <FeatureMaturityBadge
                              status={registryMaturity}
                              detail={serviceHint ? `Registry: ${serviceHint}` : "Registry maturity"}
                            />
                          ) : null}
                          <CommandCentreHealthChip state={health.getTileHealth(tile.integrationKeywords)} />
                          <span className="impilo-chip bg-[var(--surface-soft)] text-[var(--text-secondary)]">
                            {tile.priority}
                          </span>
                        </div>
                        <h3 className="text-sm font-semibold text-[var(--text-primary)] group-hover:text-primary-hover">
                          {tile.title}
                        </h3>
                        <p className="mt-1 flex-1 text-xs text-[var(--text-secondary)]">{tile.description}</p>
                        {tile.demoAction ? (
                          <p className="mt-2 text-xs font-medium text-primary">{tile.demoAction}</p>
                        ) : null}
                        {tile.knownGaps ? (
                          <p className="mt-1 text-[11px] text-warning-foreground">Gap: {tile.knownGaps}</p>
                        ) : null}
                      </Link>
                    );
                    })}
                  </div>
                </section>
              ))}
            </div>
            <RelatedServicesPanel links={COMMAND_CENTRE_RELATED_LINKS} />
            <NompiloContextPanel
              plane="Production Command Centre"
              hint="Ask Nompilo to find a service, explain a gap, or guide a production-readiness demo journey."
            />
          </div>
        </div>
      </PageShell>
    </AppLayout>
  );
}
