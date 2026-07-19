"use client";

import Link from "next/link";
import { useMemo, useState } from "react";
import { PlusCircle } from "lucide-react";
import { FundoStudioWorkspace } from "@/components/learning/FundoStudioWorkspace";
import { useFundoCatalog } from "@/hooks/queries/useFundoCatalog";

export default function FundoStudioCoursesPage() {
  const { data, isLoading } = useFundoCatalog({ status: "ALL", limit: 100 });
  const [query, setQuery] = useState("");
  const [statusFilter, setStatusFilter] = useState<"ALL" | "DRAFT" | "PUBLISHED" | "ARCHIVED">("ALL");
  const items = data?.data?.items ?? [];
  const filtered = useMemo(
    () =>
      items.filter((course) => {
        const q = query.trim().toLowerCase();
        const matchesQuery = !q || `${course.title} ${course.description ?? ""}`.toLowerCase().includes(q);
        const matchesStatus = statusFilter === "ALL" || course.status === statusFilter;
        return matchesQuery && matchesStatus;
      }),
    [items, query, statusFilter],
  );

  return (
    <FundoStudioWorkspace title="Studio Courses" subtitle="Create course outlines, structure modules/lessons, and open the block builder.">
      <div className="mb-3 flex flex-wrap items-center justify-between gap-3 rounded-lg border border-amber-300 bg-gradient-to-r from-amber-50 to-white px-3 py-2">
        <div>
          <p className="text-sm font-semibold text-foreground">Course authoring</p>
          <p className="text-xs text-muted-foreground">Create a draft course, then add modules, lessons, assessments, surveys, and media.</p>
        </div>
        <Link
          href="/learning/studio/courses/new"
          className="inline-flex items-center gap-2 rounded-lg border border-amber-500 bg-gradient-to-br from-amber-400 to-amber-500 px-3 py-2 text-sm font-semibold text-[color:var(--on-yellow)] shadow-sm transition hover:from-amber-500 hover:to-amber-600"
        >
          <PlusCircle className="h-4 w-4" />
          New course
        </Link>
      </div>
      <div className="mb-3 grid gap-2 rounded-lg border border-border bg-card p-3 md:grid-cols-2">
        <input
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Search courses, topics, descriptions..."
          className="rounded border border-border px-3 py-2 text-sm"
        />
        <select
          value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value as "ALL" | "DRAFT" | "PUBLISHED" | "ARCHIVED")}
          className="rounded border border-border px-3 py-2 text-sm"
        >
          <option value="ALL">All statuses</option>
          <option value="DRAFT">Draft</option>
          <option value="PUBLISHED">Published</option>
          <option value="ARCHIVED">Archived</option>
        </select>
      </div>
      {isLoading ? <p className="text-sm text-muted-foreground">Loading courses...</p> : null}
      <ul className="space-y-3">
        {filtered.map((course) => (
          <li key={course.id} className="rounded-lg border border-border bg-card p-4">
            <p className="font-semibold text-foreground">{course.title}</p>
            <p className="text-xs text-muted-foreground">{course.status} · {course.category ?? "GENERAL"} · {course.level ?? "FOUNDATION"}</p>
            <div className="mt-2 flex flex-wrap gap-2 text-xs">
              <Link href={`/learning/studio/courses/${course.id}`} className="rounded border border-border px-2 py-1 text-foreground">Details</Link>
              <Link href={`/learning/studio/courses/${course.id}/builder`} className="rounded border border-border px-2 py-1 text-foreground">Builder</Link>
            </div>
          </li>
        ))}
      </ul>
    </FundoStudioWorkspace>
  );
}
