"use client";

/**
 * One adult-medicine specialty workspace (brief.md §8).
 * Route: /ehr/[patientId]/medicine/specialty/[specialty] | pageTitle: "Specialty workspace"
 *
 * Distinct from /ehr/[patientId]/workspace/[specialty], which is a cross-discipline template owned
 * by another lane covering surgery, obstetrics, paediatrics and emergency. These are §8's thirteen
 * adult-medicine specialties, bound to the medicine pack's own machinery. Only "cardiology" appears
 * in both, and converging them is a follow-up rather than a silent fork.
 */

import { useParams } from "next/navigation";
import Link from "next/link";
import { EHRLayout } from "@/components/EHRLayout";
import { PageShell } from "@/components/PageShell";
import { SpecialtyWorkspaceShell } from "@/features/medicine/specialties/SpecialtyWorkspaceShell";
import { findSpecialty } from "@/features/medicine/specialties/specialty-config";

export default function MedicineSpecialtyPage() {
  const params = useParams();
  const patientId = typeof params?.patientId === "string" ? params.patientId : "";
  const specialtyKey = typeof params?.specialty === "string" ? params.specialty : "";
  const specialty = findSpecialty(specialtyKey);

  return (
    <EHRLayout>
      <PageShell
        title={specialty?.label ?? "Specialty workspace"}
        subtitle="A view onto the shared record — not a separate one."
      >
        <div className="space-y-4">
          {patientId ? (
            <SpecialtyWorkspaceShell patientId={patientId} specialtyKey={specialtyKey} />
          ) : (
            <p className="text-sm text-muted-foreground">No patient selected.</p>
          )}
          <Link
            href={`/ehr/${patientId}/medicine`}
            className="inline-block text-sm text-primary hover:underline"
          >
            ← Back to the medicine workspace
          </Link>
        </div>
      </PageShell>
    </EHRLayout>
  );
}
