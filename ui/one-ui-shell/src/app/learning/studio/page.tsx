"use client";

import Link from "next/link";
import { FundoStudioWorkspace } from "@/components/learning/FundoStudioWorkspace";
import { useFundoStudioDashboard } from "@/hooks/queries/useFundoStudio";

export default function FundoStudioPage() {
  const { data, isLoading } = useFundoStudioDashboard();
  const payload = (data?.data ?? {}) as Record<string, number>;
  const cards: Array<{ label: string; value: number; href: string }> = [
    { label: "Draft courses", value: payload.draftCourses ?? 0, href: "/learning/studio/courses" },
    { label: "Published courses", value: payload.publishedCourses ?? 0, href: "/learning/studio/publish" },
    { label: "AI awaiting review", value: payload.aiPendingReview ?? 0, href: "/learning/studio/ai" },
    { label: "Library resources", value: payload.libraryResources ?? 0, href: "/learning/studio/library" },
    { label: "Media drafts", value: payload.mediaDrafts ?? 0, href: "/learning/studio/media" },
  ];

  return (
    <FundoStudioWorkspace
      title="Fundo Studio"
      subtitle="Modern creator/admin workspace for authoring, AI-assisted generation, media, assessments, surveys, publishing and analytics."
    >
      <div className="grid gap-3 md:grid-cols-3">
        {cards.map((card) => (
          <Link key={card.label} href={card.href} className="rounded-lg border border-border bg-card p-4 hover:border-teal-300">
            <p className="text-sm text-muted-foreground">{card.label}</p>
            <p className="mt-1 text-2xl font-semibold text-foreground">{isLoading ? "..." : card.value}</p>
          </Link>
        ))}
      </div>
    </FundoStudioWorkspace>
  );
}
