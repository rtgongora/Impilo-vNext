"use client";

import { useState } from "react";
import { Award } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useLiveEvents, useLiveIssueCertificate, useLiveParticipant } from "@/hooks/queries/useLive";
import { liveApi } from "@/lib/live";

export default function LiveCertificatesPage() {
  const { data: endedEvents = [] } = useLiveEvents("ENDED");
  const { participantId } = useLiveParticipant();
  const issue = useLiveIssueCertificate();
  const [verifyCode, setVerifyCode] = useState("");
  const [verifyResult, setVerifyResult] = useState<string | null>(null);

  async function handleVerify() {
    const cert = await liveApi.verifyCertificate(verifyCode.trim());
    setVerifyResult(
      `${cert.certificateType} · ${cert.verificationCode} · issued ${cert.issuedAt ?? "—"}`,
    );
  }

  return (
    <AppLayout>
      <PageShell
        title="Live Certificates"
        subtitle="Issue attendance and CPD certificates from completed live events"
        icon={<Award className="h-6 w-6" />}
      >
        <section className="mb-8 rounded-2xl border border-gray-200 bg-white p-4">
          <h2 className="text-sm font-semibold text-gray-900 mb-2">Verify certificate</h2>
          <div className="flex flex-wrap gap-2">
            <input
              value={verifyCode}
              onChange={(e) => setVerifyCode(e.target.value)}
              placeholder="Verification code"
              className="rounded-lg border border-gray-300 px-3 py-2 text-sm min-w-[220px]"
            />
            <button
              type="button"
              onClick={handleVerify}
              className="rounded-lg bg-violet-600 px-3 py-2 text-sm text-white"
            >
              Verify
            </button>
          </div>
          {verifyResult ? <p className="mt-2 text-sm text-gray-700">{verifyResult}</p> : null}
        </section>

        <section>
          <h2 className="text-sm font-semibold uppercase tracking-wide text-gray-500 mb-3">
            Issue from ended events
          </h2>
          <ul className="space-y-2">
            {endedEvents.map((event) => (
              <li
                key={event.id}
                className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-gray-200 bg-white p-3 text-sm"
              >
                <div>
                  <p className="font-medium text-gray-900">{event.title}</p>
                  <p className="text-xs text-gray-500">{event.eventType}</p>
                </div>
                <div className="flex gap-2">
                  <button
                    type="button"
                    onClick={() =>
                      issue.mutate({ eventId: event.id, participantId, type: "attendance" })
                    }
                    disabled={issue.isPending}
                    className="rounded-lg border border-gray-300 px-3 py-1.5 text-xs"
                  >
                    Attendance cert
                  </button>
                  {event.cpdEnabled ? (
                    <button
                      type="button"
                      onClick={() =>
                        issue.mutate({ eventId: event.id, participantId, type: "cpd" })
                      }
                      disabled={issue.isPending}
                      className="rounded-lg bg-indigo-600 px-3 py-1.5 text-xs text-white"
                    >
                      CPD cert
                    </button>
                  ) : null}
                </div>
              </li>
            ))}
            {endedEvents.length === 0 ? (
              <li className="text-sm text-gray-500">No ended events available for certificate issuance.</li>
            ) : null}
          </ul>
        </section>
      </PageShell>
    </AppLayout>
  );
}
