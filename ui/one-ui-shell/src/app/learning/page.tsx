"use client";

import Link from "next/link";
import { GraduationCap } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useFundoMyLearning } from "@/hooks/queries/useFundoLms";
import { useLearningSubject } from "@/components/learning/LearningSubjectPicker";

export default function LearningHubPage() {
  const subject = useLearningSubject();
  const { data, isLoading } = useFundoMyLearning(subject);
  const payload = (data?.data ?? {}) as Record<string, unknown>;
  const inProgress = Array.isArray(payload.inProgress) ? payload.inProgress.length : 0;
  const required = Array.isArray(payload.overdue) ? payload.overdue.length : 0;
  const recommended = Array.isArray(payload.recommended) ? payload.recommended.length : 0;
  const certs = Array.isArray(payload.certificates) ? payload.certificates.length : 0;
  const cpd = Array.isArray(payload.cpdEligibleCompletions) ? payload.cpdEligibleCompletions.length : 0;

  return (
    <AppLayout>
      <PageShell
        title="Impilo Fundo"
        subtitle="Native learning management, certification, in-service training, pre-service training and CPD support."
        icon={<GraduationCap className="h-6 w-6" />}
      >
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {[
            { label: "Continue learning", value: inProgress, href: "/learning/my-learning" },
            { label: "Required training", value: required, href: "/learning/my-learning" },
            { label: "Recommended courses", value: recommended, href: "/learning/catalog" },
            { label: "Certificates", value: certs, href: "/learning/certificates" },
            { label: "CPD eligible", value: cpd, href: "/learning/cpd" },
            { label: "Pathways", value: "View", href: "/learning/pathways" },
          ].map((card) => (
            <Link
              key={card.label}
              href={card.href}
              className="rounded-lg border border-gray-200 bg-white p-4 transition hover:border-teal-300"
            >
              <p className="text-sm text-gray-500">{card.label}</p>
              <p className="mt-1 text-xl font-semibold text-gray-900">{isLoading ? "…" : card.value}</p>
            </Link>
          ))}
        </div>
        <div className="mt-6 flex flex-wrap gap-2">
          {[
            ["/learning/catalog", "Catalogue"],
            ["/learning/my-learning", "My learning"],
            ["/learning/record", "Transcript"],
            ["/learning/reports", "Reports"],
            ["/learning/admin", "Authoring"],
          ].map(([href, label]) => (
            <Link key={href} href={href} className="rounded border border-gray-300 px-3 py-1.5 text-sm text-gray-700">
              {label}
            </Link>
          ))}
        </div>
      </PageShell>
    </AppLayout>
  );
}
