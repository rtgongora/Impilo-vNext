"use client";

/**
 * Governed adult-medicine decision support for one patient.
 * Route: /ehr/[patientId]/medicine/cds | pageTitle: "Decision support"
 *
 * Eight topics, each backed by a versioned DAK content pack in the clinical knowledge platform and
 * evaluated through the BFF. Thin by design: the panel and the topic definitions live in
 * features/medicine/cds so they can be tested without mounting the chart.
 */

import { useParams } from "next/navigation";
import Link from "next/link";
import { EHRLayout } from "@/components/EHRLayout";
import { PageShell } from "@/components/PageShell";
import { CdsTopicPanel } from "@/features/medicine/cds/CdsTopicPanel";
import { CDS_TOPICS } from "@/features/medicine/cds/cds-topics";

export default function MedicineCdsPage() {
  const params = useParams();
  const patientId = typeof params?.patientId === "string" ? params.patientId : "";

  return (
    <EHRLayout>
      <PageShell
        title="Decision support"
        subtitle="Governed guidance evaluated against the facts you supply. Advisory — it never overrides your judgement."
      >
        <div className="space-y-4" data-testid="medicine-cds">
          <div className="rounded-lg border border-border bg-muted/30 p-4 text-sm text-muted-foreground">
            Each topic is evaluated only when you ask for it, against the facts you enter. A blank
            field means <strong>not stated</strong>, never &quot;no&quot; — unstated facts are
            reported back as not assessed rather than assumed. All content is an engineering seed
            pending MoHCC ratification.
          </div>

          {patientId ? (
            <div className="grid gap-4 lg:grid-cols-2">
              {CDS_TOPICS.map((t) => (
                <CdsTopicPanel
                  key={t.topic}
                  topic={t.topic}
                  title={t.title}
                  description={t.description}
                  fields={t.fields}
                />
              ))}
            </div>
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
