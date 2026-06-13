"use client";

/**
 * Self-Service Kiosk — Patient check-in terminal with consent step.
 * Route: /kiosk
 *
 * Flow: Consent → Search → Select → Check-In → Success
 *
 * Includes a consent step per the digital consent pipeline.
 * Patients must accept Privacy Policy and Terms of Use before
 * proceeding. Consent is recorded with channel=KIOSK.
 */

import { useState, type FormEvent } from "react";
import Link from "next/link";
import {
  Search,
  Loader2,
  CheckCircle2,
  UserPlus,
  AlertCircle,
  Shield,
} from "lucide-react";
import { useMutation } from "@tanstack/react-query";
import { usePatients } from "@/hooks/queries/usePatients";
import { apiClient } from "@/lib/api-client";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { CURRENT_CONSENT_VERSION } from "@/hooks/useConsentStore";

export default function KioskPage() {
  const facility = useFacilityStore((s) => s.facility);
  const [step, setStep] = useState<"consent" | "search" | "done">("consent");
  const [searchTerm, setSearchTerm] = useState("");
  const [searchSubmitted, setSearchSubmitted] = useState("");
  const [checkedInName, setCheckedInName] = useState("");

  const { data: patientsData, isLoading: searching } = usePatients(
    searchSubmitted ? { search: searchSubmitted } : undefined,
  );
  const patients = searchSubmitted ? (patientsData?.data ?? []) : [];

  const checkIn = useMutation({
    mutationFn: (body: { patient_id: string; facility_id: string; queue_type: string; priority: string }) =>
      apiClient.post("/internal/v1/queue/entries", body),
    onSuccess: () => setStep("done"),
  });

  function handleConsentAccept() {
    // Record kiosk consent server-side (fire-and-forget)
    apiClient
      .post("/internal/v1/consent/accept", {
        version: CURRENT_CONSENT_VERSION,
        privacyPolicyAccepted: true,
        termsOfUseAccepted: true,
        channel: "KIOSK",
        deviceType: "KIOSK_TERMINAL",
      })
      .catch(() => {});

    setStep("search");
  }

  function handleSearch(e: FormEvent) {
    e.preventDefault();
    setSearchSubmitted(searchTerm.trim());
  }

  function handleCheckIn(patientId: string, patientName: string) {
    if (!facility) return;
    setCheckedInName(patientName);
    checkIn.mutate({
      patient_id: patientId,
      facility_id: facility.id,
      queue_type: "WALK_IN",
      priority: "NORMAL",
    });
  }

  function resetKiosk() {
    setStep("consent");
    setSearchTerm("");
    setSearchSubmitted("");
    setCheckedInName("");
  }

  // ── Step 3: Check-in complete ──────────────────────────────────
  if (step === "done") {
    return (
      <div className="min-h-screen bg-[color:var(--surface-soft)] flex items-center justify-center p-8">
        <div className="max-w-md w-full text-center">
          <CheckCircle2 className="w-20 h-20 text-[color:var(--success)] mx-auto mb-6" />
          <h1 className="text-3xl font-bold text-[color:var(--text-primary)] mb-2">Check-In Complete</h1>
          <p className="text-lg text-[color:var(--text-secondary)] mb-2">{checkedInName}</p>
          <p className="text-sm text-[color:var(--text-muted)] mb-8">
            You have been added to the queue at {facility?.name}. Please wait to be called.
          </p>
          <button
            onClick={resetKiosk}
            className="px-8 py-4 bg-[color:var(--primary)] text-white text-lg font-medium rounded-3xl hover:bg-[color:var(--primary-hover)] transition-colors shadow-impilo-card"
          >
            Next Patient
          </button>
        </div>
      </div>
    );
  }

  // ── Step 1: Consent ────────────────────────────────────────────
  if (step === "consent") {
    return (
      <div className="min-h-screen bg-[color:var(--background)] flex flex-col items-center justify-center p-8">
        <div className="max-w-lg w-full">
          <div className="text-center mb-8">
            <Shield className="w-16 h-16 text-[color:var(--primary)] mx-auto mb-4" />
            <h1 className="text-3xl font-bold text-[color:var(--text-primary)]">Privacy Consent</h1>
            <p className="text-[color:var(--text-secondary)] mt-2">
              {facility ? facility.name : "Impilo Health Kiosk"}
            </p>
          </div>

          <div className="bg-card rounded-[2rem] border-2 border-[color:var(--border-soft)] p-8 shadow-impilo-floating">
            <p className="text-base text-[color:var(--text-secondary)] mb-6">
              Before checking in, please acknowledge the Impilo Privacy Policy
              and Terms of Use. Your information is protected under privacy-by-design
              principles.
            </p>

            <div className="space-y-3 mb-6 text-sm text-[color:var(--text-secondary)]">
              <div className="flex items-start gap-3 p-3 bg-[color:var(--surface-soft)] rounded-2xl">
                <Shield className="w-5 h-5 text-[color:var(--primary)] shrink-0 mt-0.5" />
                <div>
                  <p className="font-medium text-[color:var(--text-primary)]">Privacy Policy</p>
                  <p className="text-xs mt-0.5 text-[color:var(--text-secondary)]">
                    How we handle your personal data and health information.{" "}
                    <Link href="/privacy" target="_blank" className="text-[color:var(--info)] underline">
                      Read full policy
                    </Link>
                  </p>
                </div>
              </div>
              <div className="flex items-start gap-3 p-3 bg-[color:var(--nompilo-soft)] rounded-2xl">
                <Shield className="w-5 h-5 text-[color:var(--nompilo)] shrink-0 mt-0.5" />
                <div>
                  <p className="font-medium text-[color:var(--text-primary)]">Terms of Use</p>
                  <p className="text-xs mt-0.5 text-[color:var(--text-secondary)]">
                    The rules governing use of the Impilo platform.{" "}
                    <Link href="/terms" target="_blank" className="text-[color:var(--info)] underline">
                      Read full terms
                    </Link>
                  </p>
                </div>
              </div>
            </div>

            <button
              onClick={handleConsentAccept}
              className="w-full py-5 bg-[color:var(--primary)] text-white text-xl font-bold rounded-3xl hover:bg-[color:var(--primary-hover)] transition-colors flex items-center justify-center gap-3"
            >
              <CheckCircle2 className="w-7 h-7" />
              I Accept — Continue to Check-In
            </button>

            <p className="mt-4 text-center text-xs text-[color:var(--text-muted)]">
              By tapping Accept, you agree to the Privacy Policy and Terms of Use (v{CURRENT_CONSENT_VERSION}).
            </p>
          </div>
        </div>
      </div>
    );
  }

  // ── Step 2: Search and check-in ────────────────────────────────
  return (
    <div className="min-h-screen bg-[color:var(--background)] flex flex-col items-center justify-center p-8">
      <div className="max-w-lg w-full">
        <div className="text-center mb-8">
          <UserPlus className="w-16 h-16 text-[color:var(--primary)] mx-auto mb-4" />
          <h1 className="text-3xl font-bold text-[color:var(--text-primary)]">Self Check-In</h1>
          <p className="text-[color:var(--text-secondary)] mt-2">
            {facility ? facility.name : "Welcome"}
          </p>
        </div>

        {!facility ? (
          <div className="bg-card rounded-3xl border border-[color:var(--border-soft)] p-8 text-center shadow-impilo-card">
            <AlertCircle className="w-10 h-10 text-amber-500 mx-auto mb-3" />
            <p className="text-[color:var(--text-secondary)]">Kiosk not configured. Please set up facility context.</p>
          </div>
        ) : (
          <>
            <form onSubmit={handleSearch} className="mb-6">
              <div className="flex gap-3">
                <input
                  type="text"
                  value={searchTerm}
                  onChange={(e) => setSearchTerm(e.target.value)}
                  placeholder="Enter your name or ID number"
                  className="flex-1 px-5 py-4 text-lg border-2 border-[color:var(--border-soft)] rounded-3xl focus:border-[color:var(--primary)] focus:ring-2 focus:ring-[color:var(--primary-soft)]"
                  autoFocus
                />
                <button type="submit"
                  className="px-6 py-4 bg-[color:var(--primary)] text-white rounded-3xl hover:bg-[color:var(--primary-hover)] transition-colors">
                  <Search className="w-6 h-6" />
                </button>
              </div>
            </form>

            {searching && (
              <div className="flex items-center justify-center py-8">
                <Loader2 className="w-8 h-8 animate-spin text-[color:var(--text-muted)]" />
              </div>
            )}

            {patients.length > 0 && (
              <div className="space-y-3">
                {patients.map((patient) => (
                  <button key={patient.id}
                    onClick={() => handleCheckIn(patient.id, patient.attributes.displayName ?? patient.attributes.givenName ?? "Patient")}
                    disabled={checkIn.isPending}
                    className="w-full text-left bg-card rounded-3xl border-2 border-[color:var(--border-soft)] p-5 hover:border-[color:var(--primary-muted)] hover:shadow-impilo-card transition-all">
                    <p className="text-lg font-semibold text-[color:var(--text-primary)]">
                      {patient.attributes.displayName ?? `${patient.attributes.givenName} ${patient.attributes.familyName}`}
                    </p>
                    <p className="text-sm text-[color:var(--text-secondary)] mt-1">
                      DOB: {patient.attributes.dateOfBirth ?? "—"} · {patient.attributes.gender ?? "—"}
                    </p>
                  </button>
                ))}
              </div>
            )}

            {searchSubmitted && !searching && patients.length === 0 && (
              <div className="bg-card rounded-3xl border border-[color:var(--border-soft)] p-8 text-center shadow-impilo-card">
                <p className="text-[color:var(--text-secondary)]">No patients found for &quot;{searchSubmitted}&quot;</p>
                <p className="text-sm text-[color:var(--text-muted)] mt-1">Please check your name or ID and try again.</p>
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
}
