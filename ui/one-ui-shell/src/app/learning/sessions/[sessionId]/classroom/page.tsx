"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { GraduationCap } from "lucide-react";
import { PageShell } from "@/components/PageShell";
import { ClassroomShell } from "@/components/learning/live/ClassroomShell";

/** LEARNING_LIVE classroom — joins the session's linked Impilo Live event. */
export default function LearningSessionClassroomPage() {
  const params = useParams<{ sessionId: string }>();
  const sessionId = typeof params?.sessionId === "string" ? params.sessionId : "";

  return (
    <PageShell
      title="Classroom"
      subtitle="Governed live classroom over the Impilo Live media room"
      icon={<GraduationCap className="h-6 w-6" />}
    >
      <div className="mb-3">
        <Link href={`/learning/sessions/${sessionId}`} className="text-sm text-violet-700 hover:underline">
          ← Session details
        </Link>
      </div>
      {sessionId ? <ClassroomShell sessionId={sessionId} /> : null}
    </PageShell>
  );
}
