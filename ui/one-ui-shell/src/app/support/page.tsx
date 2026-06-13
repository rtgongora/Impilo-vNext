"use client";

/**
 * Support Hub — absorbs support-console sidecar
 * Tickets, knowledge base, messaging, incidents.
 * Route: /support | Guard: auth
 */

import Link from "next/link";
import { LifeBuoy, Ticket, BookOpen, MessageSquare, Brain } from "lucide-react";
import { HelpdeskLearningSuggestions } from "@/components/learning/HelpdeskLearningSuggestions";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";

const SECTIONS = [
  { href: "/support/tickets", label: "Support Tickets", description: "Create, track, and resolve support requests", Icon: Ticket },
  { href: "/support/knowledge-base", label: "Knowledge Base", description: "Help articles, guides, and FAQs", Icon: BookOpen },
  {
    href: "/intelligence",
    label: "Health Intelligence",
    description: "Fused search, triage packs, and learning-aware assistance",
    Icon: Brain,
  },
  { href: "/communication", label: "Messages", description: "Secure messaging and clinical paging", Icon: MessageSquare },
];

export default function SupportPage() {
  return (
    <AppLayout>
      <PageShell title="Support" subtitle="Help, tickets, knowledge base, and messaging" icon={<LifeBuoy className="h-6 w-6" />}>
        <div className="mb-6">
          <HelpdeskLearningSuggestions issueType="GENERAL" title="Guided learning for common support topics" />
        </div>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          {SECTIONS.map(({ href, label, description, Icon }) => (
            <Link key={href} href={href} className="rounded-lg border border-border bg-card p-5 hover:border-impilo-400 hover:shadow-sm transition-all">
              <div className="flex items-center gap-3 mb-2">
                <div className="rounded-lg bg-teal-50 p-2"><Icon className="h-5 w-5 text-teal-600" /></div>
                <h3 className="font-semibold text-foreground">{label}</h3>
              </div>
              <p className="text-sm text-muted-foreground">{description}</p>
            </Link>
          ))}
        </div>
      </PageShell>
    </AppLayout>
  );
}
