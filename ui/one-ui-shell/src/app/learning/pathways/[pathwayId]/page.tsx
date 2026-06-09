"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useFundoPathway } from "@/hooks/queries/useFundoLms";

export default function PathwayDetailPage() {
  const params = useParams<{ pathwayId: string }>();
  const pathwayId = params?.pathwayId;
  const { data } = useFundoPathway(pathwayId);
  const pathway = ((data?.data as Record<string, unknown>)?.pathway ?? {}) as Record<string, unknown>;
  const items = (pathway.items as Array<Record<string, unknown>> | undefined) ?? [];
  const itemTitle = (item: Record<string, unknown>) => String(item.courseTitle ?? item.courseCode ?? "Course");

  return (
    <AppLayout>
      <PageShell title={String(pathway.title ?? "Pathway")} subtitle="Ordered courses and prerequisites.">
        <ul className="space-y-2">
          {items.map((it) => (
            <li key={String(it.id)} className="rounded border border-gray-200 bg-white p-3 text-sm">
              <p className="font-medium text-gray-900">
                {String(it.sequence ?? "-")}. {itemTitle(it)}
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
    </AppLayout>
  );
}
