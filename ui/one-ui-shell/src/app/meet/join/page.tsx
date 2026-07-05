"use client";

import { Suspense, useEffect, useRef, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { Link2, Loader2, ShieldAlert } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { resolveMeetingInvite } from "@/hooks/queries/useMeeting";

/**
 * /meet/join?token=… — resolves an HMAC-signed meeting invite for the AUTHENTICATED user:
 * khuluma verifies the token, joins them to the meeting conversation with the embedded role,
 * and we route into the /meet room (where the KNOCK lobby takes over). Unauthenticated guests
 * are an honest platform seam (tshepo guest tier undefined) — the auth guard sends them to
 * sign-in first, and the link keeps working afterwards.
 */
function MeetJoinInner() {
  const router = useRouter();
  const search = useSearchParams();
  const token = search?.get("token") ?? "";
  const [error, setError] = useState<string | null>(null);
  const attempted = useRef(false);

  useEffect(() => {
    if (!token || attempted.current) return;
    attempted.current = true;
    resolveMeetingInvite(token)
      .then((resolved) => router.replace(`/meet/${resolved.conversationId}`))
      .catch(() => setError("This invite link is invalid, expired, or belongs to another workspace."));
  }, [token, router]);

  if (!token) {
    return (
      <div className="flex flex-col items-center gap-2 py-16 text-center">
        <ShieldAlert className="h-8 w-8 text-amber-500" />
        <p className="text-sm">No invite token in this link.</p>
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex flex-col items-center gap-2 py-16 text-center" data-testid="invite-error">
        <ShieldAlert className="h-8 w-8 text-red-500" />
        <p className="text-sm">{error}</p>
      </div>
    );
  }

  return (
    <div className="flex flex-col items-center gap-2 py-16 text-center" data-testid="invite-resolving">
      <Loader2 className="h-8 w-8 animate-spin text-emerald-500" />
      <p className="text-sm">Verifying your meeting invite…</p>
    </div>
  );
}

export default function MeetJoinPage() {
  return (
    <AppLayout>
      <PageShell title="Join meeting" subtitle="Meeting invite" icon={<Link2 className="h-5 w-5" />}>
        <Suspense fallback={null}>
          <MeetJoinInner />
        </Suspense>
      </PageShell>
    </AppLayout>
  );
}
