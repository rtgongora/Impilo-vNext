"use client";

import Link from "next/link";
import { FileText } from "lucide-react";
import { useLiveResources } from "@/hooks/queries/useLive";

/**
 * Class resources — the shared resources of the linked live event (the same
 * surface the live room's Resources tab reads), plus a link back to the
 * course page for module/lesson material.
 */
export function ClassResourcesPanel({ eventId, courseId }: { eventId: string; courseId?: string }) {
  const { data: resources = [] } = useLiveResources(eventId);

  return (
    <div data-testid="class-resources" className="space-y-2">
      <p className="flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wide text-muted-foreground">
        <FileText className="h-3.5 w-3.5" /> Resources
      </p>
      {resources.length === 0 ? (
        <p className="text-xs text-muted-foreground" data-testid="class-resources-empty">
          No resources shared for this class yet.
        </p>
      ) : (
        <ul className="space-y-1">
          {resources.map((res) => (
            <li key={res.id} className="rounded border border-border px-2 py-1.5 text-xs">
              <p className="font-medium text-foreground">{res.title}</p>
              <p className="text-muted-foreground">{res.resourceType}</p>
              {res.fileId ? <p className="mt-0.5 text-violet-700">File: {res.fileId}</p> : null}
            </li>
          ))}
        </ul>
      )}
      {courseId ? (
        <Link
          href={`/learning/courses/${courseId}`}
          className="inline-block text-xs text-teal-700 underline"
        >
          Course modules &amp; lessons
        </Link>
      ) : null}
    </div>
  );
}
