"use client";

import Link from "next/link";
import { useState } from "react";
import { useParams } from "next/navigation";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  useIssuanceRequest,
  useStartProofing,
  useApproveIssuance,
  useIssue,
  useDeliverIssuance,
  useRejectIssuance,
  type IssuanceState,
} from "@/hooks/queries/useVitoIssuance";
import { useCreateDelegatedPickup } from "@/hooks/queries/useVitoDelegatedPickup";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { FileText, Printer, PackageCheck } from "lucide-react";

const STATE_STYLES: Record<IssuanceState, string> = {
  SUBMITTED: "bg-blue-50 text-blue-700 border-blue-100",
  PROOFING: "bg-amber-50 text-amber-700 border-amber-100",
  APPROVED: "bg-emerald-50 text-emerald-700 border-emerald-100",
  ISSUED: "bg-sky-50 text-sky-700 border-sky-100",
  DELIVERED: "bg-green-50 text-green-700 border-green-100",
  REJECTED: "bg-red-50 text-red-700 border-red-100",
};

const TYPE_LABELS: Record<string, string> = {
  FIRST_ISSUE: "First issue",
  REPLACEMENT: "Replacement",
  RENEWAL: "Renewal",
};

const CHANNEL_LABELS: Record<string, string> = {
  ONLINE: "Online",
  IN_PERSON: "In person",
};

function formatDatetime(iso: string) {
  return new Date(iso).toLocaleString(undefined, {
    year: "numeric",
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

function labelize(value: string) {
  return value.charAt(0) + value.slice(1).toLowerCase();
}

export default function IssuanceDetailPage() {
  const params = useParams();
  const requestId = typeof params.requestId === "string" ? params.requestId : undefined;

  const request = useIssuanceRequest(requestId);
  const startProofing = useStartProofing();
  const approveIssuance = useApproveIssuance();
  const issue = useIssue();
  const deliverIssuance = useDeliverIssuance();
  const rejectIssuance = useRejectIssuance();
  const createPickup = useCreateDelegatedPickup();
  const facility = useFacilityStore((s) => s.facility);

  const [rejectReason, setRejectReason] = useState("");
  const [showRejectForm, setShowRejectForm] = useState(false);
  const [delegateName, setDelegateName] = useState("");
  const [pickupResult, setPickupResult] = useState<{ pickupToken: string; otp: string; expiresAt: string } | null>(null);

  const item = request.data?.data;
  const state = item?.state;

  const canProof = state === "SUBMITTED";
  const canApprove = state === "PROOFING";
  const canIssue = state === "APPROVED";
  const canDeliver = state === "ISSUED";
  const canReject = state !== undefined && state !== "DELIVERED" && state !== "REJECTED";

  const anyPending =
    startProofing.isPending ||
    approveIssuance.isPending ||
    issue.isPending ||
    deliverIssuance.isPending ||
    rejectIssuance.isPending;

  function handleReject() {
    if (!requestId || !rejectReason.trim()) return;
    rejectIssuance.mutate(
      { requestId, reason: rejectReason.trim() },
      {
        onSuccess: () => {
          setShowRejectForm(false);
          setRejectReason("");
        },
      }
    );
  }

  function handleCreatePickup() {
    if (!requestId || !delegateName.trim() || !facility?.id) return;
    const numericRequestId = Number.parseInt(requestId, 10);
    if (Number.isNaN(numericRequestId)) return;
    createPickup.mutate(
      {
        issuanceRequestId: numericRequestId,
        delegateName: delegateName.trim(),
        facilityId: facility.id,
        facilityName: facility.name,
      },
      {
        onSuccess: (res) => {
          if (res?.pickupToken && res?.otp) {
            setPickupResult({ pickupToken: res.pickupToken, otp: res.otp, expiresAt: res.expiresAt });
          }
        },
      },
    );
  }

  return (
    <AppLayout>
      <PageShell
        title="Issuance request"
        subtitle="Review and advance an identity document issuance workflow request"
        icon={<FileText className="h-6 w-6" />}
      >
        <div className="space-y-6">
          <div className="flex flex-wrap items-center gap-3">
            <Link
              href="/operations/vito/issuance"
              className="text-sm text-gray-600 hover:text-gray-900 underline-offset-2 hover:underline"
            >
              ← Issuance queue
            </Link>
          </div>

          {request.isLoading ? (
            <div className="rounded-2xl border border-gray-200 bg-white p-8 text-center text-sm text-gray-500">
              Loading request…
            </div>
          ) : request.isError || !item ? (
            <div className="rounded-2xl border border-gray-200 bg-white p-8 text-center text-sm text-red-600">
              Failed to load issuance request. It may not exist or the Experience BFF is unreachable.
            </div>
          ) : (
            <>
              <div className="rounded-2xl border border-gray-200 bg-white p-5 space-y-4">
                <div className="flex flex-wrap items-center gap-3">
                  <h2 className="text-sm font-semibold text-gray-900">Request details</h2>
                  <span
                    className={`inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-medium ${STATE_STYLES[item.state]}`}
                  >
                    {labelize(item.state)}
                  </span>
                </div>
                <dl className="grid gap-3 sm:grid-cols-2">
                  <div>
                    <dt className="text-xs text-gray-500">Request ID</dt>
                    <dd className="mt-0.5 font-mono text-sm text-gray-900 break-all">{item.id}</dd>
                  </div>
                  <div>
                    <dt className="text-xs text-gray-500">Health ID</dt>
                    <dd className="mt-0.5 font-mono text-sm text-gray-900">{item.healthId}</dd>
                  </div>
                  <div>
                    <dt className="text-xs text-gray-500">Type</dt>
                    <dd className="mt-0.5 text-sm text-gray-900">{TYPE_LABELS[item.type] ?? item.type}</dd>
                  </div>
                  <div>
                    <dt className="text-xs text-gray-500">Channel</dt>
                    <dd className="mt-0.5 text-sm text-gray-900">{CHANNEL_LABELS[item.channel] ?? item.channel}</dd>
                  </div>
                  <div>
                    <dt className="text-xs text-gray-500">Requested</dt>
                    <dd className="mt-0.5 text-sm text-gray-900">{formatDatetime(item.requestedAt)}</dd>
                  </div>
                  <div>
                    <dt className="text-xs text-gray-500">Last updated</dt>
                    <dd className="mt-0.5 text-sm text-gray-900">{formatDatetime(item.updatedAt)}</dd>
                  </div>
                  {item.notes && (
                    <div className="sm:col-span-2">
                      <dt className="text-xs text-gray-500">Notes</dt>
                      <dd className="mt-0.5 text-sm text-gray-900">{item.notes}</dd>
                    </div>
                  )}
                </dl>
              </div>

              <div className="rounded-2xl border border-gray-200 bg-white p-5 space-y-4">
                <h2 className="text-sm font-semibold text-gray-900">Workflow actions</h2>

                <div className="flex flex-wrap gap-3">
                  <button
                    type="button"
                    disabled={!canProof || anyPending}
                    onClick={() => requestId && startProofing.mutate(requestId)}
                    className="inline-flex items-center gap-2 rounded-xl border border-amber-200 bg-amber-50 px-4 py-2 text-sm font-medium text-amber-800 hover:border-amber-300 disabled:opacity-40 disabled:cursor-not-allowed"
                  >
                    {startProofing.isPending ? "Starting…" : "Start proofing"}
                  </button>

                  <button
                    type="button"
                    disabled={!canApprove || anyPending}
                    onClick={() => requestId && approveIssuance.mutate(requestId)}
                    className="inline-flex items-center gap-2 rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-2 text-sm font-medium text-emerald-800 hover:border-emerald-300 disabled:opacity-40 disabled:cursor-not-allowed"
                  >
                    {approveIssuance.isPending ? "Approving…" : "Approve"}
                  </button>

                  <button
                    type="button"
                    disabled={!canIssue || anyPending}
                    onClick={() => requestId && issue.mutate(requestId)}
                    className="inline-flex items-center gap-2 rounded-xl border border-sky-200 bg-sky-50 px-4 py-2 text-sm font-medium text-sky-800 hover:border-sky-300 disabled:opacity-40 disabled:cursor-not-allowed"
                  >
                    {issue.isPending ? "Issuing…" : "Issue"}
                  </button>

                  <button
                    type="button"
                    disabled={!canDeliver || anyPending}
                    onClick={() => requestId && deliverIssuance.mutate(requestId)}
                    className="inline-flex items-center gap-2 rounded-xl border border-green-200 bg-green-50 px-4 py-2 text-sm font-medium text-green-800 hover:border-green-300 disabled:opacity-40 disabled:cursor-not-allowed"
                  >
                    {deliverIssuance.isPending ? "Delivering…" : "Mark delivered"}
                  </button>

                  {canReject && !showRejectForm && (
                    <button
                      type="button"
                      disabled={anyPending}
                      onClick={() => setShowRejectForm(true)}
                      className="inline-flex items-center gap-2 rounded-xl border border-red-200 bg-red-50 px-4 py-2 text-sm font-medium text-red-700 hover:border-red-300 disabled:opacity-40 disabled:cursor-not-allowed"
                    >
                      Reject
                    </button>
                  )}
                </div>

                {showRejectForm && (
                  <div className="rounded-xl border border-red-100 bg-red-50 p-4 space-y-3">
                    <p className="text-sm font-medium text-red-800">Reject this request</p>
                    <label className="flex flex-col gap-1 text-xs text-red-700">
                      Reason (required)
                      <textarea
                        rows={3}
                        className="rounded-lg border border-red-200 bg-white px-3 py-2 text-sm text-gray-900 focus:outline-none focus:ring-2 focus:ring-red-300"
                        value={rejectReason}
                        onChange={(e) => setRejectReason(e.target.value)}
                        placeholder="Describe the reason for rejection…"
                      />
                    </label>
                    <div className="flex gap-2">
                      <button
                        type="button"
                        disabled={!rejectReason.trim() || rejectIssuance.isPending}
                        onClick={handleReject}
                        className="rounded-xl bg-red-600 px-4 py-2 text-sm font-medium text-white hover:bg-red-700 disabled:opacity-40 disabled:cursor-not-allowed"
                      >
                        {rejectIssuance.isPending ? "Rejecting…" : "Confirm rejection"}
                      </button>
                      <button
                        type="button"
                        onClick={() => {
                          setShowRejectForm(false);
                          setRejectReason("");
                        }}
                        className="rounded-xl border border-red-200 px-4 py-2 text-sm font-medium text-red-700 hover:border-red-300"
                      >
                        Cancel
                      </button>
                    </div>
                    {rejectIssuance.isError && (
                      <p className="text-xs text-red-700">Rejection failed. Please try again.</p>
                    )}
                  </div>
                )}

                {(startProofing.isError ||
                  approveIssuance.isError ||
                  issue.isError ||
                  deliverIssuance.isError) && (
                  <p className="text-sm text-red-600">
                    Action failed. Check that the Experience BFF is reachable and your session has the required permissions.
                  </p>
                )}
              </div>

              {(state === "APPROVED" || state === "ISSUED" || state === "DELIVERED") && (
                <div className="rounded-2xl border border-gray-200 bg-white p-5 space-y-4">
                  <h2 className="text-sm font-semibold text-gray-900">Card operations</h2>
                  <p className="text-sm text-gray-500">
                    Continue the Health ID card journey after approval — print the card, verify delegate pickup, or review
                    slips.
                  </p>
                  <div className="flex flex-wrap gap-3">
                    {(state === "APPROVED" || state === "ISSUED" || state === "DELIVERED") && (
                      <Link
                        href="/operations/vito/print"
                        className="inline-flex items-center gap-2 rounded-xl border border-gray-200 bg-white px-4 py-2 text-sm font-medium text-gray-800 hover:border-gray-300"
                      >
                        <Printer className="h-4 w-4 text-impilo-600" />
                        Print &amp; slips
                      </Link>
                    )}
                    {(state === "ISSUED" || state === "DELIVERED") && (
                      <Link
                        href="/operations/vito/cards/pickup"
                        className="inline-flex items-center gap-2 rounded-xl border border-sky-200 bg-sky-50 px-4 py-2 text-sm font-medium text-sky-800 hover:border-sky-300"
                      >
                        <PackageCheck className="h-4 w-4" />
                        Card pickup verify
                      </Link>
                    )}
                    <Link
                      href="/operations/vito/cards"
                      className="inline-flex items-center gap-2 rounded-xl border border-gray-200 px-4 py-2 text-sm font-medium text-gray-700 hover:border-gray-300"
                    >
                      Card registry
                    </Link>
                  </div>
                  <p className="text-xs text-gray-500">
                    Health ID: <span className="font-mono text-gray-700">{item.healthId}</span>
                  </p>
                </div>
              )}

              {(state === "ISSUED" || state === "DELIVERED") && (
                <div className="rounded-2xl border border-gray-200 bg-white p-5 space-y-4">
                  <h2 className="text-sm font-semibold text-gray-900">Delegated pickup package</h2>
                  <p className="text-sm text-gray-500">
                    Create a pickup token and OTP for delegate handover at{" "}
                    {facility?.name ?? "the active facility context"}.
                  </p>
                  {!facility?.id && (
                    <p className="text-xs text-amber-700 bg-amber-50 border border-amber-100 rounded-lg px-3 py-2">
                      Select a facility context before creating a pickup package.
                    </p>
                  )}
                  <label className="flex flex-col gap-1 text-xs text-gray-600">
                    Delegate name
                    <input
                      className="rounded-lg border border-gray-300 px-3 py-2 text-sm"
                      value={delegateName}
                      onChange={(e) => setDelegateName(e.target.value)}
                      placeholder="Person collecting the card"
                    />
                  </label>
                  <button
                    type="button"
                    disabled={!delegateName.trim() || !facility?.id || createPickup.isPending}
                    onClick={handleCreatePickup}
                    className="inline-flex items-center gap-2 rounded-xl border border-sky-200 bg-sky-50 px-4 py-2 text-sm font-medium text-sky-800 hover:border-sky-300 disabled:opacity-40"
                  >
                    {createPickup.isPending ? "Creating…" : "Create pickup package"}
                  </button>
                  {pickupResult && (
                    <div className="rounded-xl border border-green-100 bg-green-50 p-4 text-sm space-y-2">
                      <p className="font-medium text-green-900">Pickup package ready</p>
                      <p>
                        Token: <span className="font-mono">{pickupResult.pickupToken}</span>
                      </p>
                      <p>
                        OTP: <span className="font-mono tracking-widest">{pickupResult.otp}</span>
                      </p>
                      <p className="text-xs text-green-800">Expires {formatDatetime(pickupResult.expiresAt)}</p>
                      <Link
                        href="/operations/vito/cards/pickup"
                        className="inline-flex text-sm font-medium text-green-900 underline underline-offset-2"
                      >
                        Open pickup verify →
                      </Link>
                    </div>
                  )}
                  {createPickup.isError && (
                    <p className="text-sm text-red-600">Failed to create pickup package. Check BFF/VITO availability.</p>
                  )}
                </div>
              )}
            </>
          )}
        </div>
      </PageShell>
    </AppLayout>
  );
}
