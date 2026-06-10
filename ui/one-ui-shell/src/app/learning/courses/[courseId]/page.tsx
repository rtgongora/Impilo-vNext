"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { ArrowLeft, BadgeCheck, BookOpenCheck, CheckCircle2, Clock, GraduationCap } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useFundoCourseStructure } from "@/hooks/queries/useFundoCatalog";
import { useCreateFundoEnrolment } from "@/hooks/queries/useFundoLms";
import { useLearningSubject } from "@/components/learning/LearningSubjectPicker";
import { FundoLiveWebinarEmbed } from "@/components/live/FundoLiveWebinarEmbed";

/**
 * Phase 6B — native Impilo Fundo course detail page. Renders the Phase 5B
 * `/v11/courses/{courseId}/structure` view (course + ordered modules +
 * lessons) via the Phase 6B BFF passthrough.
 */
export default function LearningCourseDetailPage() {
  const params = useParams<{ courseId: string }>();
  const router = useRouter();
  const courseId = typeof params?.courseId === "string" ? params.courseId : undefined;
  const { data, isLoading, isError } = useFundoCourseStructure(courseId);
  const createEnrolment = useCreateFundoEnrolment();
  const subject = useLearningSubject();

  const structure = data?.data?.structure;
  const titleText = structure?.title ?? "Impilo Fundo Course";

  return (
    <AppLayout>
      <PageShell
        title={titleText}
        subtitle={
          structure?.code
            ? `Native course: ${structure.code}`
            : "Native Impilo Fundo course"
        }
        icon={<GraduationCap className="h-6 w-6" />}
      >
        <div className="mb-4">
          <Link
            href="/learning/catalog"
            className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700"
          >
            <ArrowLeft className="h-4 w-4" /> Back to catalogue
          </Link>
        </div>

        {isLoading ? (
          <div className="rounded-lg border border-gray-200 bg-white p-6 text-sm text-gray-600">
            Loading course…
          </div>
        ) : null}

        {!isLoading && (isError || !structure) ? (
          <div className="rounded-lg border border-amber-200 bg-amber-50 p-6 text-sm text-amber-900">
            Course is currently unavailable. Please retry shortly.
          </div>
        ) : null}

        {!isLoading && !isError && structure ? (
          <>
            <div className="mb-6 rounded-lg border border-gray-200 bg-white p-5">
              <div className="flex flex-wrap items-center gap-2 text-xs">
                {structure.category ? (
                  <span className="rounded bg-gray-100 px-1.5 py-0.5 text-gray-700">{structure.category}</span>
                ) : null}
                {structure.level ? (
                  <span className="rounded bg-gray-100 px-1.5 py-0.5 text-gray-700">{structure.level}</span>
                ) : null}
                {structure.cpdEligible ? (
                  <span className="inline-flex items-center gap-1 rounded bg-emerald-50 px-1.5 py-0.5 text-emerald-800">
                    <BadgeCheck className="h-3 w-3" /> CPD
                  </span>
                ) : null}
                {structure.mandatory ? (
                  <span className="inline-flex items-center gap-1 rounded bg-blue-50 px-1.5 py-0.5 text-blue-800">
                    <CheckCircle2 className="h-3 w-3" /> Mandatory
                  </span>
                ) : null}
                {structure.estimatedDurationMinutes ? (
                  <span className="inline-flex items-center gap-1 text-gray-500">
                    <Clock className="h-3 w-3" /> {structure.estimatedDurationMinutes} min total
                  </span>
                ) : null}
              </div>
              {structure.description ? (
                <p className="mt-3 text-sm text-gray-700">{structure.description}</p>
              ) : null}
              <div className="mt-3 flex gap-2">
                <button
                  onClick={async () => {
                    const res = (await createEnrolment.mutateAsync({
                      courseId: structure.id,
                      subjectType: subject.subjectType,
                      subjectId: subject.subjectId,
                      enrolmentType: "SELF",
                    })) as { data?: { enrolment?: { id?: string } } };
                    const enrolmentId = String(res?.data?.enrolment?.id ?? "");
                    if (enrolmentId) {
                      router.push(`/learning/enrolments/${enrolmentId}`);
                    }
                  }}
                  className="rounded bg-teal-700 px-3 py-1.5 text-sm text-white"
                  data-testid="fundo-enrol-button"
                >
                  Enrol / Continue
                </button>
                <Link href="/learning/my-learning" className="rounded border border-gray-300 px-3 py-1.5 text-sm text-gray-700">
                  View my learning
                </Link>
              </div>
            </div>

            <div className="mb-6">
              <FundoLiveWebinarEmbed
                courseId={structure.id}
                courseTitle={structure.title}
                liveEventId={
                  typeof (structure as { impiloLiveEventId?: string }).impiloLiveEventId === "string"
                    ? (structure as { impiloLiveEventId?: string }).impiloLiveEventId
                    : undefined
                }
              />
            </div>

            <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-gray-500">
              Modules ({structure.modules.length})
            </h2>
            {structure.modules.length === 0 ? (
              <div className="rounded-lg border border-gray-200 bg-white p-6 text-sm text-gray-600">
                This course has no published modules yet.
              </div>
            ) : (
              <ol className="space-y-3">
                {structure.modules.map((m) => (
                  <li key={m.id} className="rounded-lg border border-gray-200 bg-white p-4">
                    <p className="font-semibold text-gray-900">
                      {m.sequence}. {m.title}
                    </p>
                    {m.description ? <p className="mt-1 text-xs text-gray-600">{m.description}</p> : null}
                    {m.lessons.length > 0 ? (
                      <ul className="mt-3 space-y-1.5 pl-3">
                        {m.lessons.map((l) => (
                          <li key={l.id} className="flex items-start gap-2 text-sm text-gray-700">
                            <BookOpenCheck className="mt-0.5 h-4 w-4 flex-shrink-0 text-teal-700" />
                            <span>
                              <span className="font-medium">{l.title}</span>
                              <span className="ml-2 font-mono text-xs uppercase text-gray-500">
                                {l.contentType}
                              </span>
                              {l.estimatedDurationMinutes ? (
                                <span className="ml-2 text-xs text-gray-500">
                                  {l.estimatedDurationMinutes} min
                                </span>
                              ) : null}
                              {!l.required ? (
                                <span className="ml-2 rounded bg-gray-100 px-1 text-[10px] uppercase text-gray-600">
                                  Optional
                                </span>
                              ) : null}
                            </span>
                          </li>
                        ))}
                      </ul>
                    ) : (
                      <p className="mt-2 text-xs italic text-gray-500">No lessons yet.</p>
                    )}
                  </li>
                ))}
              </ol>
            )}
          </>
        ) : null}
      </PageShell>
    </AppLayout>
  );
}
