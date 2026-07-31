"use client";

import Link from "next/link";
import { AlertTriangle, ClipboardList, Stethoscope } from "lucide-react";
import { useConditions } from "@/hooks/queries/useConditions";
import { CDS_TOPICS } from "@/features/medicine/cds/cds-topics";
import { findSpecialty, matchesSpecialty, type SpecialtyDefinition } from "./specialty-config";

/**
 * One adult-medicine specialty workspace (brief.md §8).
 *
 * A view onto shared machinery — the problem list, the §7 examination, the governed CDS packs, the
 * chronic registers — filtered to what this specialty cares about. It is deliberately not a screen
 * that owns anything: the "folder of specialist forms" the brief forbids is what you get when each
 * specialty builds its own copy of the record.
 *
 * The section this surface exists to be honest about is the last one. §8's tooling lists are largely
 * unbuilt, and a workspace that renders them as tiles is the defect an earlier wave had to strip out
 * of this estate — buttons that look available and do nothing. What is missing is listed, as
 * missing, beside what is there.
 */

const REGION_LABEL = (region: string) =>
  region.charAt(0) + region.slice(1).toLowerCase().replace(/_/g, " ");

const REGISTER_LABEL: Record<string, string> = {
  HYPERTENSION: "Hypertension",
  DIABETES: "Diabetes",
  CHRONIC_KIDNEY_DISEASE: "Chronic kidney disease",
  CHRONIC_RESPIRATORY: "Asthma and COPD",
};

export function SpecialtyWorkspaceShell({
  patientId,
  specialtyKey,
}: {
  patientId: string;
  specialtyKey: string;
}) {
  const specialty = findSpecialty(specialtyKey);
  const conditions = useConditions(patientId);

  if (!specialty) {
    return (
      <p className="text-sm text-muted-foreground" data-testid="specialty-unknown">
        No adult-medicine specialty is registered under &quot;{specialtyKey}&quot;.
      </p>
    );
  }

  const rows = conditions.data?.data ?? [];
  const mine = rows.filter((c) =>
    matchesSpecialty(specialty, c.attributes.icdCode, c.attributes.conditionName),
  );

  return (
    <div className="space-y-6" data-testid={`specialty-${specialty.key}`}>
      <p className="text-sm text-muted-foreground">
        {specialty.summary} <span className="text-xs">({specialty.section})</span>
      </p>

      <section className="rounded-lg border border-border bg-card p-5 space-y-2" data-testid="specialty-problems">
        <div className="flex items-center gap-2">
          <Stethoscope className="w-4 h-4 text-primary" />
          <h3 className="font-semibold text-sm">This patient&apos;s problems in scope</h3>
        </div>
        {conditions.isError ? (
          <p className="text-sm text-danger" data-testid="specialty-problems-unavailable">
            The problem list could not be read — this is <strong>not</strong> a statement that the
            patient has no conditions in this specialty.
          </p>
        ) : mine.length === 0 ? (
          <p className="text-sm text-muted-foreground" data-testid="specialty-problems-empty">
            No problem on this patient&apos;s list falls in this specialty. The list was read.
          </p>
        ) : (
          <ul className="space-y-1 text-sm">
            {mine.map((c) => (
              <li key={c.id} className="flex items-center justify-between">
                <span>{c.attributes.conditionName}</span>
                <span className="text-xs text-muted-foreground">
                  {c.attributes.icdCode ?? "—"} · {c.attributes.clinicalStatus.toLowerCase()}
                </span>
              </li>
            ))}
          </ul>
        )}
        <Link
          href={`/ehr/${patientId}/conditions`}
          className="inline-block text-sm text-primary hover:underline"
          data-testid="specialty-link-problems"
        >
          Open the full problem list →
        </Link>
      </section>

      <div className="grid gap-4 lg:grid-cols-2">
        <section className="rounded-lg border border-border bg-card p-5 space-y-2" data-testid="specialty-cds">
          <h3 className="font-semibold text-sm">Governed decision support</h3>
          {specialty.cdsTopics.length === 0 ? (
            <p className="text-sm text-muted-foreground">
              No governed CDS pack covers this specialty yet.
            </p>
          ) : (
            <ul className="space-y-1 text-sm">
              {specialty.cdsTopics.map((topic) => {
                const def = CDS_TOPICS.find((t) => t.topic === topic);
                return (
                  <li key={topic} data-testid={`specialty-cds-${topic}`}>
                    <Link
                      href={`/ehr/${patientId}/medicine/cds`}
                      className="text-primary hover:underline"
                    >
                      {def?.title ?? topic}
                    </Link>
                  </li>
                );
              })}
            </ul>
          )}
        </section>

        <section className="rounded-lg border border-border bg-card p-5 space-y-2" data-testid="specialty-examination">
          <h3 className="font-semibold text-sm">Examination focus</h3>
          <p className="text-sm text-muted-foreground">
            {specialty.examinationRegions.map(REGION_LABEL).join(", ")}.
          </p>
          <Link
            href={`/ehr/${patientId}/examination`}
            className="inline-block text-sm text-primary hover:underline"
            data-testid="specialty-link-examination"
          >
            Record an examination →
          </Link>
        </section>
      </div>

      {specialty.registers.length > 0 && (
        <section className="rounded-lg border border-border bg-card p-5 space-y-2" data-testid="specialty-registers">
          <div className="flex items-center gap-2">
            <ClipboardList className="w-4 h-4 text-primary" />
            <h3 className="font-semibold text-sm">Registers this specialty runs</h3>
          </div>
          <p className="text-sm text-muted-foreground">
            {specialty.registers.map((r) => REGISTER_LABEL[r] ?? r).join(", ")}.
          </p>
          <Link
            href="/clinical/chronic-registers"
            className="inline-block text-sm text-primary hover:underline"
            data-testid="specialty-link-registers"
          >
            Open the registers →
          </Link>
        </section>
      )}

      {specialty.spineLinks.length > 0 && (
        <section
          className="rounded-lg border border-border bg-card p-5 space-y-2"
          data-testid="specialty-spine"
        >
          <h3 className="font-semibold text-sm">Shared spine this specialty uses</h3>
          <p className="text-sm text-muted-foreground">
            These are deep links into existing services — not a second copy of the record.
          </p>
          <ul className="space-y-2 text-sm">
            {specialty.spineLinks.map((link) => (
              <li key={link.label} data-testid={`specialty-spine-${link.label}`}>
                <Link
                  href={link.href(patientId)}
                  className="text-primary hover:underline"
                >
                  {link.label} →
                </Link>
                {link.note && (
                  <p className="text-xs text-muted-foreground mt-0.5">{link.note}</p>
                )}
              </li>
            ))}
          </ul>
        </section>
      )}

      <section className="rounded-lg border border-border bg-card p-5 space-y-2" data-testid="specialty-conditions">
        <h3 className="font-semibold text-sm">What {specialty.section} asks this specialty to support</h3>
        <ul className="grid gap-1 text-sm text-muted-foreground sm:grid-cols-2">
          {specialty.conditions.map((c) => (
            <li key={c}>{c}</li>
          ))}
        </ul>
      </section>

      {/* The section that keeps this surface honest. Shortening it without building the thing is
          how a workspace starts claiming capability it does not have. */}
      <section
        className="rounded-lg border border-warning/40 bg-amber-50 p-5 space-y-2"
        data-testid="specialty-not-built"
      >
        <div className="flex items-center gap-2">
          <AlertTriangle className="w-4 h-4 text-warning-foreground" />
          <h3 className="font-semibold text-sm">Not built here</h3>
        </div>
        <p className="text-sm text-muted-foreground">
          {specialty.section} asks for the following, and this pack has not built it. It is listed
          rather than shown as a button that does nothing.
        </p>
        <ul className="list-disc pl-5 text-sm text-muted-foreground">
          {specialty.notBuilt.map((n) => (
            <li key={n}>{n}</li>
          ))}
        </ul>
      </section>
    </div>
  );
}

export type { SpecialtyDefinition };
