"use client";

/**
 * Citizen feedback landing — share feedback or track a case by reference.
 * No "my cases" list is rendered: the citizen list endpoint is not built, so we
 * never fabricate one. Route: /my-life/feedback
 */

import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { MessageSquarePlus, Search } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";

export default function MyFeedbackPage() {
  const router = useRouter();
  const [ref, setRef] = useState("");

  return (
    <AppLayout>
      <PageShell
        title="Feedback & safety"
        subtitle="Share your experience or report a concern — and track it under your case reference"
        serviceSlug="rito"
      >
        <div className="space-y-6">
          <Link
            href="/my-life/feedback/new"
            className="flex items-center gap-3 rounded-2xl border border-teal-300 bg-teal-50 p-5 transition-all hover:shadow-sm"
          >
            <div className="rounded-xl bg-teal-600 p-2.5">
              <MessageSquarePlus className="h-5 w-5 text-white" />
            </div>
            <div>
              <div className="font-semibold text-teal-900">Share feedback</div>
              <div className="text-sm text-teal-800">
                A complaint, compliment, suggestion or safety concern
              </div>
            </div>
          </Link>

          <div className="rounded-2xl border border-border bg-card p-5">
            <h3 className="mb-1 text-sm font-semibold text-foreground">Track a case</h3>
            <p className="mb-3 text-sm text-muted-foreground">
              Submit feedback or report a concern, then track it using the case reference you were
              given. Enter your reference below to see its status.
            </p>
            <form
              onSubmit={(e) => {
                e.preventDefault();
                const v = ref.trim();
                if (v) router.push(`/my-life/feedback/${encodeURIComponent(v)}`);
              }}
              className="flex flex-wrap gap-2"
            >
              <input
                value={ref}
                onChange={(e) => setRef(e.target.value)}
                placeholder="Case reference or ID"
                className="flex-1 rounded-lg border border-border px-3 py-2 text-sm"
              />
              <button
                type="submit"
                disabled={!ref.trim()}
                className="inline-flex items-center gap-1.5 rounded-lg bg-teal-600 px-4 py-2 text-sm font-medium text-white hover:bg-teal-700 disabled:opacity-50"
              >
                <Search className="h-4 w-4" /> Track
              </button>
            </form>
          </div>
        </div>
      </PageShell>
    </AppLayout>
  );
}
