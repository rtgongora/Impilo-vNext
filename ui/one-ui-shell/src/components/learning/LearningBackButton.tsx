"use client";

import { ArrowLeft } from "lucide-react";
import { usePathname, useRouter } from "next/navigation";

function parentPath(pathname: string): string {
  if (pathname === "/learning" || !pathname.startsWith("/learning/")) return "/learning";

  const segments = pathname.split("/").filter(Boolean);

  if (segments[1] === "courses" && segments.length >= 3) return "/learning/catalog";
  if (segments[1] === "attempts") return "/learning/my-learning";
  if (segments[1] === "certificates" && segments.length >= 3) return "/learning/certificates";
  if (segments[1] === "pathways" && segments.length >= 3) return "/learning/pathways";
  if (segments[1] === "library" && segments.length >= 3) return "/learning/library";
  if (segments[1] === "reports" && segments.length >= 3) return "/learning/reports";
  if (segments[1] === "surveys" && segments.length >= 4 && segments[3] === "respond") {
    return `/learning/surveys/${segments[2]}`;
  }
  if (segments[1] === "feedback") return "/learning/my-learning";

  if (segments[1] === "assessments" && segments.length >= 4 && segments[3] === "attempt") {
    return `/learning/assessments/${segments[2]}`;
  }

  if (segments[1] === "enrolments" && segments.length >= 5 && segments[3] === "lessons") {
    return `/learning/enrolments/${segments[2]}`;
  }

  if (segments[1] === "studio") {
    if (segments.length === 2) return "/learning";
    if (segments.length >= 5 && segments[2] === "courses" && segments[4] === "builder") {
      return `/learning/studio/courses/${segments[3]}`;
    }
    return `/${segments.slice(0, -1).join("/")}`;
  }

  if (segments[1] === "admin") {
    if (segments.length === 2) return "/learning";
    return `/${segments.slice(0, -1).join("/")}`;
  }

  return segments.length > 2 ? `/${segments.slice(0, -1).join("/")}` : "/learning";
}

export function LearningBackButton() {
  const pathname = usePathname();
  const router = useRouter();

  if (pathname === "/learning") return null;

  const fallback = parentPath(pathname);

  function goBack() {
    router.push(fallback);
  }

  return (
    <div className="mb-2">
      <button
        type="button"
        onClick={goBack}
        className="inline-flex items-center gap-1.5 rounded-lg border border-border bg-card px-2.5 py-1.5 text-xs font-medium text-muted-foreground transition hover:bg-neutral-100 hover:text-foreground"
      >
        <ArrowLeft className="h-3.5 w-3.5" />
        Back
      </button>
    </div>
  );
}
