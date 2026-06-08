"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { GraduationCap } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { summarizeFundoMyLearning, useFundoMyLearning } from "@/hooks/queries/useFundoLms";
import { FundoLearningOrchestrationRail } from "@/components/learning/FundoLearningOrchestrationRail";
import { useLearningSubject } from "@/components/learning/LearningSubjectPicker";

export default function LearningHubPage() {
  const searchParams = useSearchParams();
  const focus = searchParams.get("focus");
  const subject = useLearningSubject();
  const { data, isLoading } = useFundoMyLearning(subject);
  const payload = (data?.data ?? {}) as Record<string, unknown>;
  const fundoKpis = summarizeFundoMyLearning(payload);
  const recommended = Array.isArray(payload.recommended) ? payload.recommended.length : 0;
  const certs = Array.isArray(payload.certificates) ? payload.certificates.length : 0;

  return (
    <AppLayout>
      <PageShell
        title="Impilo Fundo"
        subtitle="Native learning management, certification, in-service training, pre-service training and CPD support."
        icon={<GraduationCap className="h-6 w-6" />}
      >
        <FundoLearningOrchestrationRail />
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {[
            { label: "Continue learning", value: fundoKpis.inProgress, href: "/learning?focus=in-progress", focusKey: "in-progress" },
            { label: "Required training", value: fundoKpis.required, href: "/learning?focus=required", focusKey: "required" },
            { label: "Overdue modules", value: fundoKpis.overdue, href: "/learning?focus=overdue", focusKey: "overdue" },
            { label: "Recommended courses", value: recommended, href: "/learning/catalog", focusKey: null },
            { label: "Certificates", value: certs, href: "/learning/certificates", focusKey: null },
            { label: "CPD eligible", value: fundoKpis.cpdEligible, href: "/learning?focus=cpd", focusKey: "cpd" },
            { label: "Pathways", value: "View", href: "/learning/pathways", focusKey: null },
          ].map((card) => (
            <Link
              key={card.label}
              href={card.href}
              className={`rounded-lg border bg-white p-4 transition hover:border-teal-300 ${
                card.focusKey && focus === card.focusKey ? "border-teal-400 ring-2 ring-teal-100" : "border-gray-200"
              }`}
            >
              <p className="text-sm text-gray-500">{card.label}</p>
              <p className="mt-1 text-xl font-semibold text-gray-900">{isLoading ? "…" : card.value}</p>
            </Link>
          ))}
        </div>
        {focus ? (
          <div className="mt-3 rounded-lg border border-teal-200 bg-teal-50 px-3 py-2 text-xs text-teal-800">
            Focus mode: <span className="font-semibold">{focus}</span>. Use this snapshot to prioritize the selected Fundo queue.
          </div>
        ) : null}
        <div className="mt-6 flex flex-wrap gap-2">
          {[
            ["/learning/catalog", "Catalogue"],
            ["/learning/my-learning", "My learning"],
            ["/learning/record", "Transcript"],
            ["/learning/reports", "Reports"],
            ["/learning/admin", "Authoring"],
            ["/learning/studio", "Fundo Studio"],
            ["/learning/library", "Library"],
            ["/learning/notifications", "Notifications"],
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
