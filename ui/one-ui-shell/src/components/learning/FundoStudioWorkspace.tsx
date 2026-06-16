"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import type { ReactNode } from "react";
import { BarChart3, Bell, BookOpenCheck, Bot, ClipboardCheck, FolderOpen, Library, PlayCircle, Radio, Send, Settings2 } from "lucide-react";
import { ServiceLogo } from "@/components/branding/ServiceLogo";
import { PageShell } from "@/components/PageShell";

type Props = {
  title: string;
  subtitle: string;
  children?: ReactNode;
};

const STUDIO_LINKS = [
  { href: "/learning/studio", label: "Home", icon: Settings2 },
  { href: "/learning/studio/courses", label: "Courses", icon: BookOpenCheck },
  { href: "/learning/studio/library", label: "Library", icon: Library },
  { href: "/learning/studio/media", label: "Media", icon: Radio },
  { href: "/learning/studio/assessments", label: "Assessments", icon: ClipboardCheck },
  { href: "/learning/studio/surveys", label: "Surveys", icon: Bell },
  { href: "/learning/studio/ai", label: "AI", icon: Bot },
  { href: "/learning/studio/publish", label: "Publish", icon: Send },
  { href: "/learning/studio/analytics", label: "Analytics", icon: BarChart3 },
];

const LEARNER_LINKS = [
  { href: "/learning/library", label: "Learning Library" },
  { href: "/learning/library/resources", label: "Resources" },
  { href: "/learning/library/uploads", label: "Uploads" },
  { href: "/learning/notifications", label: "Notifications" },
];

export function FundoStudioWorkspace({ title, subtitle, children }: Props) {
  const pathname = usePathname();

  return (
      <PageShell title={title} hideHeader>
        <div className="mb-3 rounded-lg border border-border bg-card px-3 py-2">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div className="flex min-w-0 items-center gap-2">
              <ServiceLogo slug="fundo" size="compact" />
              <div className="min-w-0">
                <h1 className="truncate text-lg font-semibold text-foreground">{title}</h1>
                <p className="truncate text-xs text-muted-foreground">{subtitle}</p>
              </div>
            </div>
            <div className="rounded-full border border-teal-200 bg-teal-50 px-2.5 py-1 text-xs font-medium text-teal-800">
              Studio workspace
            </div>
          </div>
        </div>

        <div className="sticky top-3 z-10 mb-3 rounded-lg border border-teal-200 bg-teal-50/95 px-3 py-2 shadow-sm backdrop-blur">
          <div className="flex flex-wrap items-center gap-1.5">
            {STUDIO_LINKS.map((item) => {
              const Icon = item.icon;
              const active =
                item.href === "/learning/studio"
                  ? pathname === item.href
                  : pathname === item.href || pathname.startsWith(`${item.href}/`);
              return (
                <Link
                  key={item.href}
                  href={item.href}
                  className={[
                    "inline-flex items-center gap-1.5 rounded-lg border px-2.5 py-1.5 text-xs font-medium transition",
                    active
                      ? "border-teal-300 bg-card text-teal-900 shadow-sm"
                      : "border-transparent text-teal-800 hover:border-teal-200 hover:bg-card",
                  ].join(" ")}
                >
                  <Icon className="h-3.5 w-3.5" />
                  {item.label}
                </Link>
              );
            })}
            <span className="mx-1 h-5 w-px bg-teal-200" aria-hidden />
            {LEARNER_LINKS.map((item) => (
              <Link
                key={item.href}
                href={item.href}
                className="inline-flex items-center gap-1 rounded-lg border border-transparent px-2.5 py-1.5 text-xs font-medium text-teal-800 transition hover:border-teal-200 hover:bg-card"
              >
                <PlayCircle className="h-3.5 w-3.5" />
                {item.label}
              </Link>
            ))}
          </div>
        </div>

        {children}
      </PageShell>
  );
}
