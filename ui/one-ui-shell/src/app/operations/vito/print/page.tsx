"use client";

import Link from "next/link";
import { useState } from "react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useSubmitPrintJob } from "@/hooks/queries/useVitoPrint";
import { getEmergencyCapsuleUrl, getPickupSlipUrl } from "@/hooks/queries/useVitoSlips";
import { ExternalLink, FileText, Printer } from "lucide-react";

export default function VitoPrintPage() {
  const submitPrintJob = useSubmitPrintJob();

  const [cardId, setCardId] = useState("");
  const [template, setTemplate] = useState("");
  const [printResult, setPrintResult] = useState<{ cardId: number; cardNumber: string; status: string; message: string } | null>(null);

  const [capsuleHealthId, setCapsuleHealthId] = useState("");
  const [capsuleHasAllergies, setCapsuleHasAllergies] = useState(false);
  const [capsuleHasMajorConditions, setCapsuleHasMajorConditions] = useState(false);
  const [capsulePayerIndicator, setCapsulePayerIndicator] = useState("");

  const [pickupToken, setPickupToken] = useState("");
  const [pickupOtp, setPickupOtp] = useState("");
  const [pickupDelegateName, setPickupDelegateName] = useState("");
  const [pickupFacilityName, setPickupFacilityName] = useState("");
  const [pickupExpiresAt, setPickupExpiresAt] = useState("");

  function handleSubmitPrint(e: React.FormEvent) {
    e.preventDefault();
    const id = parseInt(cardId, 10);
    if (isNaN(id)) return;
    submitPrintJob.mutate(
      { cardId: id, template: template || undefined },
      {
        onSuccess: (res) => {
          if (res) setPrintResult(res);
        },
      }
    );
  }

  function openEmergencyCapsule() {
    if (!capsuleHealthId.trim()) return;
    const url = getEmergencyCapsuleUrl({
      healthId: capsuleHealthId.trim(),
      hasAllergies: capsuleHasAllergies,
      hasMajorConditions: capsuleHasMajorConditions,
      payerIndicator: capsulePayerIndicator || undefined,
    });
    window.open(url, "_blank", "noopener,noreferrer");
  }

  function openPickupSlip() {
    if (!pickupToken.trim() || !pickupOtp.trim() || !pickupDelegateName.trim() || !pickupFacilityName.trim() || !pickupExpiresAt.trim()) return;
    const url = getPickupSlipUrl({
      pickupToken: pickupToken.trim(),
      otp: pickupOtp.trim(),
      delegateName: pickupDelegateName.trim(),
      facilityName: pickupFacilityName.trim(),
      expiresAt: pickupExpiresAt.trim(),
    });
    window.open(url, "_blank", "noopener,noreferrer");
  }

  const capsuleReady = capsuleHealthId.trim().length > 0;
  const pickupReady =
    pickupToken.trim().length > 0 &&
    pickupOtp.trim().length > 0 &&
    pickupDelegateName.trim().length > 0 &&
    pickupFacilityName.trim().length > 0 &&
    pickupExpiresAt.trim().length > 0;

  return (
    <AppLayout>
      <PageShell
        title="Print & Slips"
        subtitle="Submit card print jobs and generate PDF slips for emergency capsules and card pickup authorisations"
        icon={<Printer className="h-6 w-6" />}
      >
        <div className="space-y-6">
          <div className="flex flex-wrap gap-3">
            <Link
              href="/operations/vito"
              className="text-sm text-gray-600 hover:text-gray-900 underline-offset-2 hover:underline"
            >
              ← Identity operations
            </Link>
          </div>

          <div className="grid gap-6 lg:grid-cols-2">
            <div className="rounded-2xl border border-gray-200 bg-white p-5 space-y-4">
              <div className="flex items-center gap-2">
                <Printer className="h-4 w-4 text-impilo-600" />
                <h2 className="text-sm font-semibold text-gray-900">Card print job</h2>
              </div>
              <p className="text-sm text-gray-500">
                Submit a card to the print queue. The card must already exist in the registry before printing.
              </p>
              <form onSubmit={handleSubmitPrint} className="space-y-3">
                <label className="text-xs text-gray-500 flex flex-col gap-1">
                  Card ID
                  <input
                    type="number"
                    min={1}
                    className="rounded-lg border border-gray-300 px-3 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-impilo-400"
                    placeholder="e.g. 1042"
                    value={cardId}
                    onChange={(e) => setCardId(e.target.value)}
                    required
                  />
                </label>
                <label className="text-xs text-gray-500 flex flex-col gap-1">
                  Template (optional)
                  <input
                    type="text"
                    className="rounded-lg border border-gray-300 px-3 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-impilo-400"
                    placeholder="e.g. STANDARD_V2"
                    value={template}
                    onChange={(e) => setTemplate(e.target.value)}
                  />
                </label>
                <button
                  type="submit"
                  disabled={submitPrintJob.isPending || !cardId}
                  className="w-full rounded-xl bg-impilo-600 px-4 py-2 text-sm font-medium text-white hover:bg-impilo-700 disabled:opacity-50"
                >
                  {submitPrintJob.isPending ? "Submitting…" : "Submit print job"}
                </button>
                {submitPrintJob.isError && (
                  <p className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700 border border-red-100">
                    Failed to submit print job. Please try again.
                  </p>
                )}
                {printResult && (
                  <div className="rounded-lg bg-emerald-50 border border-emerald-100 px-3 py-2 space-y-1">
                    <p className="text-sm font-medium text-emerald-800">Job submitted</p>
                    <p className="text-xs text-emerald-700">Card: {printResult.cardNumber}</p>
                    <p className="text-xs text-emerald-700">Status: {printResult.status}</p>
                    <p className="text-xs text-emerald-700">{printResult.message}</p>
                  </div>
                )}
              </form>
            </div>

            <div className="rounded-2xl border border-gray-200 bg-white p-5 space-y-4">
              <div className="flex items-center gap-2">
                <FileText className="h-4 w-4 text-sky-600" />
                <h2 className="text-sm font-semibold text-gray-900">Emergency capsule slip</h2>
              </div>
              <p className="text-sm text-gray-500">
                Generate a PDF emergency capsule containing critical health identifiers and clinical flags. Opens in a new tab.
              </p>
              <div className="space-y-3">
                <label className="text-xs text-gray-500 flex flex-col gap-1">
                  Health ID
                  <input
                    type="text"
                    className="rounded-lg border border-gray-300 px-3 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-sky-400"
                    placeholder="e.g. ZW-0001-2024-XXXXXX"
                    value={capsuleHealthId}
                    onChange={(e) => setCapsuleHealthId(e.target.value)}
                  />
                </label>
                <label className="text-xs text-gray-500 flex flex-col gap-1">
                  Payer indicator (optional)
                  <input
                    type="text"
                    className="rounded-lg border border-gray-300 px-3 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-sky-400"
                    placeholder="e.g. NHIF"
                    value={capsulePayerIndicator}
                    onChange={(e) => setCapsulePayerIndicator(e.target.value)}
                  />
                </label>
                <div className="flex flex-wrap gap-4">
                  <label className="flex items-center gap-2 text-sm text-gray-600 cursor-pointer">
                    <input
                      type="checkbox"
                      className="rounded border-gray-300"
                      checked={capsuleHasAllergies}
                      onChange={(e) => setCapsuleHasAllergies(e.target.checked)}
                    />
                    Has allergies
                  </label>
                  <label className="flex items-center gap-2 text-sm text-gray-600 cursor-pointer">
                    <input
                      type="checkbox"
                      className="rounded border-gray-300"
                      checked={capsuleHasMajorConditions}
                      onChange={(e) => setCapsuleHasMajorConditions(e.target.checked)}
                    />
                    Has major conditions
                  </label>
                </div>
                <button
                  type="button"
                  disabled={!capsuleReady}
                  onClick={openEmergencyCapsule}
                  className="w-full inline-flex items-center justify-center gap-2 rounded-xl bg-sky-600 px-4 py-2 text-sm font-medium text-white hover:bg-sky-700 disabled:opacity-50"
                >
                  <ExternalLink className="h-4 w-4" />
                  Open emergency capsule PDF
                </button>
              </div>
            </div>
          </div>

          <div className="rounded-2xl border border-gray-200 bg-white p-5 space-y-4">
            <div className="flex items-center gap-2">
              <FileText className="h-4 w-4 text-amber-600" />
              <h2 className="text-sm font-semibold text-gray-900">Card pickup authorisation slip</h2>
            </div>
            <p className="text-sm text-gray-500">
              Generate a PDF pickup authorisation slip for a delegate collecting a card on behalf of a client. Opens in a new tab.
            </p>
            <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
              <label className="text-xs text-gray-500 flex flex-col gap-1">
                Pickup token
                <input
                  type="text"
                  className="rounded-lg border border-gray-300 px-3 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-amber-400"
                  placeholder="Pickup token"
                  value={pickupToken}
                  onChange={(e) => setPickupToken(e.target.value)}
                />
              </label>
              <label className="text-xs text-gray-500 flex flex-col gap-1">
                OTP
                <input
                  type="text"
                  className="rounded-lg border border-gray-300 px-3 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-amber-400"
                  placeholder="One-time PIN"
                  value={pickupOtp}
                  onChange={(e) => setPickupOtp(e.target.value)}
                />
              </label>
              <label className="text-xs text-gray-500 flex flex-col gap-1">
                Delegate name
                <input
                  type="text"
                  className="rounded-lg border border-gray-300 px-3 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-amber-400"
                  placeholder="Full name of delegate"
                  value={pickupDelegateName}
                  onChange={(e) => setPickupDelegateName(e.target.value)}
                />
              </label>
              <label className="text-xs text-gray-500 flex flex-col gap-1">
                Facility name
                <input
                  type="text"
                  className="rounded-lg border border-gray-300 px-3 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-amber-400"
                  placeholder="Issuing facility"
                  value={pickupFacilityName}
                  onChange={(e) => setPickupFacilityName(e.target.value)}
                />
              </label>
              <label className="text-xs text-gray-500 flex flex-col gap-1 sm:col-span-2 lg:col-span-1">
                Expires at (ISO 8601)
                <input
                  type="datetime-local"
                  className="rounded-lg border border-gray-300 px-3 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-amber-400"
                  value={pickupExpiresAt}
                  onChange={(e) => setPickupExpiresAt(e.target.value)}
                />
              </label>
            </div>
            <button
              type="button"
              disabled={!pickupReady}
              onClick={openPickupSlip}
              className="inline-flex items-center gap-2 rounded-xl bg-amber-600 px-4 py-2 text-sm font-medium text-white hover:bg-amber-700 disabled:opacity-50"
            >
              <ExternalLink className="h-4 w-4" />
              Open pickup slip PDF
            </button>
          </div>
        </div>
      </PageShell>
    </AppLayout>
  );
}
