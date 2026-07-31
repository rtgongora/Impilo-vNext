"use client";

/**
 * Statutory professional registers for a regulatory organisation (NCZ task #97).
 *
 * Route: /work/regulatory/[orgId]/registers
 *
 * This is the human surface of the register materialiser. Configuration packs declare REGISTER
 * definitions; this page shows the live rows those definitions became — including provenance, so a
 * migration-seeded register cannot be mistaken for one that arrived as configuration. Reconcile is
 * idempotent and writes only professional_registers; it is not a configuration studio.
 */

import { useState } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";
import {
  ArrowLeft,
  BookMarked,
  Loader2,
  RefreshCw,
  TriangleAlert,
} from "lucide-react";
import { LuminousStage } from "shared-ui";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useRegulatoryOrganisations } from "@/hooks/queries/useRegulatoryOrganisations";
import {
  useProfessionalRegisters,
  useReconcileProfessionalRegisters,
  type ProfessionalRegisterRow,
  type RegisterReconcileReport,
} from "@/hooks/queries/useProfessionalRegisters";

const SOURCE_STYLE: Record<string, string> = {
  CONFIG_PACK: "bg-teal-100 text-teal-800",
  MIGRATION_SEED: "bg-amber-100 text-amber-900",
};

const STATUS_STYLE: Record<string, string> = {
  ACTIVE: "bg-green-100 text-green-800",
  RETIRED: "bg-neutral-100 text-muted-foreground",
};

function fmt(iso: string | null | undefined): string {
  if (!iso) return "—";
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? "—" : d.toLocaleString();
}

function sourceLabel(source: string): string {
  if (source === "CONFIG_PACK") return "From configuration";
  if (source === "MIGRATION_SEED") return "Migration seed";
  return source;
}

function RegisterCard({ register }: { register: ProfessionalRegisterRow }) {
  const sourceStyle = SOURCE_STYLE[register.source] ?? "bg-neutral-100 text-muted-foreground";
  const statusStyle = STATUS_STYLE[register.status] ?? "bg-neutral-100 text-muted-foreground";
  return (
    <article className="rounded-2xl border border-border bg-card p-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <BookMarked className="h-4 w-4 shrink-0 text-muted-foreground" aria-hidden />
            <h3 className="text-sm font-semibold text-foreground">{register.name}</h3>
            <span className="font-mono text-xs text-muted-foreground">{register.registerCode}</span>
          </div>
          {register.description ? (
            <p className="mt-1 text-xs text-muted-foreground">{register.description}</p>
          ) : null}
        </div>
        <div className="flex flex-wrap gap-1.5">
          <span className={`rounded-full px-2 py-0.5 text-[11px] font-medium ${statusStyle}`}>
            {register.status}
          </span>
          <span className={`rounded-full px-2 py-0.5 text-[11px] font-medium ${sourceStyle}`}>
            {sourceLabel(register.source)}
          </span>
          {register.registerAxis ? (
            <span className="rounded-full border border-border px-2 py-0.5 text-[11px] text-muted-foreground">
              {register.registerAxis}
            </span>
          ) : null}
        </div>
      </div>

      <dl className="mt-3 grid gap-2 text-xs sm:grid-cols-2">
        <div>
          <dt className="text-muted-foreground">Statutory title</dt>
          <dd className="text-foreground">
            {register.statutoryTitle ?? (
              <span className="text-muted-foreground">
                Not set
                {register.statutoryTitleDecisionRef
                  ? ` — awaiting ${register.statutoryTitleDecisionRef}`
                  : ""}
              </span>
            )}
          </dd>
        </div>
        <div>
          <dt className="text-muted-foreground">Configuration version</dt>
          <dd className="text-foreground">
            {register.configDefinitionKey ? (
              <>
                <span className="font-mono">{register.configDefinitionKey}</span>
                {register.configSemanticVersion
                  ? ` @ ${register.configSemanticVersion}`
                  : null}
              </>
            ) : (
              <span className="text-muted-foreground">—</span>
            )}
          </dd>
        </div>
        <div>
          <dt className="text-muted-foreground">Materialised</dt>
          <dd className="text-foreground">{fmt(register.materialisedAt)}</dd>
        </div>
        <div>
          <dt className="text-muted-foreground">Retired</dt>
          <dd className="text-foreground">{fmt(register.retiredAt)}</dd>
        </div>
      </dl>
    </article>
  );
}

function reportMessage(report: RegisterReconcileReport): string {
  if (report.outcome === "CONFIGURATION_UNAVAILABLE") {
    return (
      report.notes[0] ??
      "The Regulatory Configuration Registry could not be reached — nothing was changed."
    );
  }
  if (report.outcome === "NO_ORGANISATION_LINK" || report.outcome === "NO_COUNCIL_LINKED") {
    return report.note ?? report.notes[0] ?? "No council is linked to this organisation.";
  }
  return [
    `Reconciled ${report.councilCode ?? "council"}:`,
    `${report.declared} declared`,
    `${report.created} created`,
    `${report.updated} updated`,
    `${report.unchanged} unchanged`,
    `${report.retired} retired`,
    `${report.reinstated} reinstated`,
  ].join(" · ");
}

export default function RegulatoryRegistersPage() {
  const params = useParams<{ orgId: string }>();
  const orgId = decodeURIComponent(String(params?.orgId ?? ""));
  const { organisation } = useRegulatoryOrganisations();
  const org = organisation(orgId);
  const catalogue = useProfessionalRegisters(orgId);
  const reconcile = useReconcileProfessionalRegisters(orgId);
  const [message, setMessage] = useState<string | null>(null);
  const [reportNotes, setReportNotes] = useState<string[]>([]);

  const data = catalogue.data;
  const registers = data?.registers ?? [];
  const summary = data?.summary;

  return (
    <AppLayout>
      <PageShell
        title="Professional registers"
        subtitle={
          org
            ? `${org.code} — registers materialised from the activated configuration`
            : "Registers materialised from the activated configuration"
        }
        serviceSlug="varapi"
      >
        <LuminousStage className="space-y-5 p-5 sm:p-6">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <Link
              href={`/work/regulatory/${encodeURIComponent(orgId)}`}
              className="inline-flex items-center gap-1 text-xs font-semibold text-teal-700 hover:underline"
            >
              <ArrowLeft className="h-3.5 w-3.5" /> Workspace hub
            </Link>
            <Link
              href={`/work/regulatory/${encodeURIComponent(orgId)}/configuration`}
              className="text-xs text-muted-foreground hover:text-foreground hover:underline"
            >
              View configuration packs
            </Link>
          </div>

          <p className="text-xs text-muted-foreground">
            Registers are owned by the council&apos;s activated configuration pack and materialised
            into varapi. This view is not a register editor: use reconcile to bring the live rows in
            line with the ACTIVE release. A register dropped from a pack is retired, never deleted.
          </p>

          <div className="rounded-2xl border border-border bg-card p-4">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div>
                {catalogue.isLoading ? (
                  <Loader2 className="h-4 w-4 animate-spin text-muted-foreground" />
                ) : data && !data.linked ? (
                  <p className="text-sm text-amber-800">
                    No professional council is linked to this organisation yet.
                  </p>
                ) : summary ? (
                  <p className="text-sm text-foreground">
                    <span className="font-semibold">{data?.councilName ?? data?.councilCode}</span>
                    <span className="text-muted-foreground">
                      {" "}
                      · {summary.total} registers · {summary.fromConfiguration} from configuration ·{" "}
                      {summary.fromMigrationSeed} migration seed · {summary.retired} retired
                    </span>
                  </p>
                ) : (
                  <p className="text-sm text-muted-foreground">Register catalogue unavailable.</p>
                )}
                {data?.note ? (
                  <p className="mt-1 text-xs text-muted-foreground">{data.note}</p>
                ) : null}
              </div>
              <div className="flex items-center gap-2">
                <button
                  type="button"
                  className="inline-flex items-center gap-1 rounded border border-border px-3 py-1.5 text-sm hover:bg-background disabled:opacity-40"
                  disabled={reconcile.isPending || !data?.linked}
                  onClick={() => {
                    setMessage(null);
                    setReportNotes([]);
                    reconcile
                      .mutateAsync()
                      .then((report) => {
                        setMessage(reportMessage(report));
                        setReportNotes(report.notes ?? []);
                      })
                      .catch((error: unknown) => {
                        const msg =
                          error &&
                          typeof error === "object" &&
                          "message" in error &&
                          typeof (error as { message: unknown }).message === "string"
                            ? (error as { message: string }).message
                            : "Reconcile failed — the configuration registry may be unreachable.";
                        setMessage(msg);
                      });
                  }}
                >
                  <RefreshCw
                    className={`h-4 w-4 ${reconcile.isPending ? "animate-spin" : ""}`}
                    aria-hidden
                  />
                  {reconcile.isPending ? "Reconciling…" : "Reconcile from configuration"}
                </button>
              </div>
            </div>
            {message ? (
              <p className="mt-3 text-xs text-muted-foreground" role="status">
                {message}
              </p>
            ) : null}
            {reportNotes.length > 0 ? (
              <ul className="mt-2 list-disc space-y-1 pl-5 text-xs text-muted-foreground">
                {reportNotes.map((note) => (
                  <li key={note}>{note}</li>
                ))}
              </ul>
            ) : null}
          </div>

          {summary && summary.fromMigrationSeed > 0 ? (
            <div className="flex gap-2 rounded-lg border border-amber-500/40 bg-amber-500/10 p-3 text-xs text-amber-950">
              <TriangleAlert className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
              <p>
                {summary.fromMigrationSeed} register
                {summary.fromMigrationSeed === 1 ? "" : "s"} still carry migration-seed provenance.
                Those rows predate configuration materialisation and are not retired when a pack is
                silent about them — retiring them would answer an open council decision by side
                effect.
              </p>
            </div>
          ) : null}

          {catalogue.isLoading ? (
            <div className="flex justify-center py-16">
              <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
            </div>
          ) : catalogue.isError ? (
            <div className="rounded-2xl border border-border bg-card p-10 text-center text-sm text-muted-foreground">
              The register catalogue could not be loaded.
            </div>
          ) : registers.length === 0 ? (
            <div className="rounded-2xl border border-dashed border-border bg-muted/30 p-10 text-center">
              <BookMarked className="mx-auto mb-3 h-8 w-8 text-muted-foreground" aria-hidden />
              <p className="text-sm text-muted-foreground">
                No professional registers yet. Activate a configuration pack that declares REGISTER
                definitions, then reconcile.
              </p>
            </div>
          ) : (
            <div className="space-y-3">
              {registers.map((register) => (
                <RegisterCard key={register.registerCode} register={register} />
              ))}
            </div>
          )}
        </LuminousStage>
      </PageShell>
    </AppLayout>
  );
}
