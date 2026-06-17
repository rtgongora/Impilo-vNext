"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { PageShell } from "@/components/PageShell";
import { useFundoPathway } from "@/hooks/queries/useFundoLms";

export default function PathwayDetailPage() {
  const params = useParams<{ pathwayId: string }>();
  const pathwayId = params?.pathwayId;
  const { data } = useFundoPathway(pathwayId);
  const pathway = ((data?.data as Record<string, unknown>)?.pathway ?? {}) as Record<string, unknown>;
  const items = (pathway.items as Array<Record<string, unknown>> | undefined) ?? [];

  return (
      <PageShell title={String(pathway.title ?? "Pathway")} subtitle="Ordered courses and prerequisites.">
        <ul className="space-y-2">
          {items.map((it) => (
            <li key={String(it.id)} className="rounded border border-border bg-card p-3 text-sm">
              <p className="font-medium text-foreground">
                {String(it.sequence ?? "-")}. {String(it.courseTitle ?? it.courseId)}
              </p>
              {it.courseId ? (
                <Link className="text-teal-700 hover:underline" href={`/learning/courses/${String(it.courseId)}`}>
                  Open course
                </Link>
              ) : null}
            </li>
          ))}
        </ul>
      </PageShell>
  );
}
