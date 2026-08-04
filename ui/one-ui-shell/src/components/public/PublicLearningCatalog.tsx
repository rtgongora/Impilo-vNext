"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { Loader2, GraduationCap, Clock } from "lucide-react";
import { apiClient } from "@/lib/api-client";

/**
 * Public learning catalog (PF-W5). Browse the PUBLISHED course library WITHOUT sign-in.
 * Enrolment, assessed learning, certificates and CPD credits require sign-in.
 */

interface Course {
  code?: string;
  title?: string;
  description?: string;
  category?: string;
  level?: string;
  estimatedDurationMinutes?: number;
  cpdEligible?: boolean;
}

interface CatalogResponse {
  data?: { items?: Course[] };
}

export function PublicLearningCatalog() {
  const [courses, setCourses] = useState<Course[] | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    let cancelled = false;
    apiClient
      .get<CatalogResponse>("/internal/v1/public/gateway/learning/catalog?limit=50")
      .then((r) => {
        if (!cancelled) setCourses(r?.data?.items ?? []);
      })
      .catch(() => {
        if (!cancelled) setError("The course library is temporarily unavailable. Please try again shortly.");
      });
    return () => {
      cancelled = true;
    };
  }, []);

  if (error) return <p className="text-sm text-red-700">{error}</p>;
  if (courses === null) {
    return (
      <p className="flex items-center gap-2 text-sm text-slate-500">
        <Loader2 className="h-4 w-4 animate-spin" aria-hidden /> Loading courses…
      </p>
    );
  }

  return (
    <div className="space-y-6">
      {courses.length === 0 ? (
        <p className="text-sm text-slate-600" data-testid="learning-empty">
          No courses are published yet.
        </p>
      ) : (
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3" data-testid="learning-courses">
          {courses.map((c) => (
            <div key={c.code} className="flex flex-col rounded-xl border border-white/60 bg-white/70 backdrop-blur-sm [.low-blur_&]:bg-white p-4">
              <div className="flex items-start gap-2">
                <GraduationCap className="h-5 w-5 shrink-0 text-emerald-600" aria-hidden />
                <p className="font-semibold text-slate-900">{c.title}</p>
              </div>
              {c.description && <p className="mt-1 flex-1 text-[13px] leading-relaxed text-slate-600">{c.description}</p>}
              <div className="mt-2 flex flex-wrap items-center gap-2 text-xs text-slate-500">
                {c.category && <span className="rounded-full bg-slate-100 px-2 py-0.5">{c.category}</span>}
                {c.level && <span>{c.level}</span>}
                {c.estimatedDurationMinutes ? (
                  <span className="inline-flex items-center gap-1">
                    <Clock className="h-3 w-3" aria-hidden /> {c.estimatedDurationMinutes} min
                  </span>
                ) : null}
                {c.cpdEligible && <span className="text-emerald-700">CPD</span>}
              </div>
            </div>
          ))}
        </div>
      )}

      <div className="rounded-xl border border-emerald-200 bg-emerald-50 p-4">
        <p className="text-sm font-medium text-emerald-900">Earn certificates &amp; CPD?</p>
        <p className="mt-1 text-sm text-emerald-800">
          Read freely. Sign in to enrol, complete assessments, track progress and earn
          certificates or CPD credits.
        </p>
        <Link
          href="/auth/login?returnTo=%2Flearning"
          className="mt-2 inline-block rounded-lg bg-emerald-700 px-4 py-2 text-sm font-semibold text-white hover:bg-emerald-800"
        >
          Sign in to learn
        </Link>
      </div>
    </div>
  );
}
