"use client";

import { useState } from "react";
import Link from "next/link";
import { MessageSquare, Loader2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useDonorByPerson, useSubmitDonorFeedback } from "@/hooks/queries/useMadi";

export default function DonorFeedbackPage() {
  const user = useAuthStore((s) => s.user);
  const personCpid = user?.healthId || user?.id || "";
  const { data: donor } = useDonorByPerson(personCpid || undefined);
  const feedback = useSubmitDonorFeedback(donor?.donorId ?? "");
  const [rating, setRating] = useState(5);
  const [comments, setComments] = useState("");
  const [submitted, setSubmitted] = useState(false);

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!donor?.donorId) return;
    feedback.mutate({ rating, comments }, { onSuccess: () => setSubmitted(true) });
  }

  return (
    <AppLayout>
      <PageShell title="Donor Feedback" subtitle="Help us improve the donation experience" icon={<MessageSquare className="h-6 w-6" />}>
        {!donor && (
          <div className="rounded-2xl border border-dashed border-gray-300 p-8 text-center">
            <p className="text-sm text-gray-600">Register and donate before leaving feedback.</p>
            <Link href="/madi/donor/register" className="mt-3 inline-block text-sm text-rose-600">Register →</Link>
          </div>
        )}

        {donor && submitted && (
          <div className="rounded-xl border border-green-200 bg-green-50 p-4 text-sm text-green-800">
            Thank you — your feedback helps improve donor care.
          </div>
        )}

        {donor && !submitted && (
          <form onSubmit={handleSubmit} className="max-w-md space-y-4 rounded-2xl border border-gray-200 bg-white p-6">
            <label className="block text-sm font-medium text-gray-700">
              Rating (1–5)
              <input
                type="number"
                min={1}
                max={5}
                value={rating}
                onChange={(e) => setRating(Number(e.target.value))}
                className="mt-1 w-full rounded-xl border border-gray-300 px-3 py-2 text-sm"
              />
            </label>
            <label className="block text-sm font-medium text-gray-700">
              Comments (optional)
              <textarea
                value={comments}
                onChange={(e) => setComments(e.target.value)}
                rows={4}
                className="mt-1 w-full rounded-xl border border-gray-300 px-3 py-2 text-sm resize-none"
                placeholder="What went well? What could be better?"
              />
            </label>
            <button
              type="submit"
              disabled={feedback.isPending}
              className="inline-flex items-center gap-2 rounded-xl bg-rose-600 px-4 py-2 text-sm font-medium text-white hover:bg-rose-700 disabled:opacity-50"
            >
              {feedback.isPending ? <Loader2 className="h-4 w-4 animate-spin" /> : null}
              Submit feedback
            </button>
          </form>
        )}
      </PageShell>
    </AppLayout>
  );
}
