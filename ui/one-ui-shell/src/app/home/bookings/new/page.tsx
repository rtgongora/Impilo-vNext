"use client";

/**
 * Book a service wizard — citizen creates a booking transaction (not an appointment).
 * Route: /home/bookings/new
 */

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useMemo, useRef, useState } from "react";
import {
  Building2,
  Calendar,
  CheckCircle2,
  ClipboardList,
  Loader2,
  MapPin,
  Search,
  Shield,
  Stethoscope,
  Users,
  Video,
  Wallet,
} from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import {
  useBookingAvailability,
  useBookingRoutingProviders,
  useBookingRoutingResources,
  useBookingRoutingWorkspaces,
  useCreateBooking,
  useRequestBookingMvumo,
} from "@/hooks/queries/useBookings";
import { useFacilities, type FacilityResource } from "@/hooks/queries/useFacilities";
import type { BookingChannel, BookingTargetType } from "@/lib/booking-bff";
import { useAuthStore } from "@/hooks/useAuthStore";
import { RecognisedProviderChip } from "@/components/common/RecognisedProviderChip";

const TARGET_TYPES: {
  id: BookingTargetType;
  label: string;
  icon: React.ElementType;
}[] = [
  { id: "PRACTITIONER", label: "Specific practitioner", icon: Stethoscope },
  { id: "WORKSPACE", label: "Workspace / ward", icon: MapPin },
  { id: "FACILITY_SERVICE", label: "Facility clinical service", icon: ClipboardList },
  { id: "SPECIALTY_POOL", label: "Specialty pool", icon: Users },
  { id: "RESOURCE", label: "Room / resource", icon: Building2 },
];

const BOOKING_TYPES = [
  { value: "CONSULTATION", label: "Consultation" },
  { value: "FOLLOW_UP", label: "Follow-up" },
  { value: "TELEMEDICINE", label: "Telemedicine" },
  { value: "LAB", label: "Laboratory" },
  { value: "WELLNESS", label: "Wellness" },
  { value: "SCREENING", label: "Screening" },
];

const CHANNELS: { value: BookingChannel; label: string }[] = [
  { value: "PHYSICAL", label: "In person" },
  { value: "VIRTUAL", label: "Virtual / telemedicine" },
  { value: "HOME", label: "Home visit" },
  { value: "COMMUNITY", label: "Community outreach" },
];

const FALLBACK_TIMES = [
  "08:00", "08:30", "09:00", "09:30", "10:00", "10:30", "11:00", "11:30",
  "12:00", "13:00", "13:30", "14:00", "14:30", "15:00", "15:30", "16:00",
];

type WizardStep = "target" | "facility" | "datetime" | "details" | "mvumo" | "payment" | "review";

const STEPS: { id: WizardStep; label: string }[] = [
  { id: "target", label: "Who / what" },
  { id: "facility", label: "Facility" },
  { id: "datetime", label: "When" },
  { id: "details", label: "Details" },
  { id: "mvumo", label: "Consent" },
  { id: "payment", label: "Payment" },
  { id: "review", label: "Submit" },
];

export default function BookServiceWizardPage() {
  const router = useRouter();
  const { user } = useAuthStore();
  const [step, setStep] = useState<WizardStep>("target");
  const [targetType, setTargetType] = useState<BookingTargetType | "">("");
  const [targetRef, setTargetRef] = useState("");
  const [targetLabel, setTargetLabel] = useState("");
  const [facilitySearch, setFacilitySearch] = useState("");
  const [debouncedFacilitySearch, setDebouncedFacilitySearch] = useState("");
  const [selectedFacility, setSelectedFacility] = useState<FacilityResource | null>(null);
  const [showFacilityDropdown, setShowFacilityDropdown] = useState(false);
  const [bookingType, setBookingType] = useState("CONSULTATION");
  const [preferredDate, setPreferredDate] = useState("");
  const [preferredTime, setPreferredTime] = useState("09:00");
  const [channel, setChannel] = useState<BookingChannel>("PHYSICAL");
  const [reason, setReason] = useState("");
  const [mvumoRequested, setMvumoRequested] = useState(false);
  const [paymentAcknowledged, setPaymentAcknowledged] = useState(false);
  const [submittedId, setSubmittedId] = useState<string | null>(null);
  const searchTimeout = useRef<ReturnType<typeof setTimeout> | null>(null);

  const createBooking = useCreateBooking();
  const requestMvumo = useRequestBookingMvumo();

  const { data: facilitiesData, isLoading: facilitiesLoading } = useFacilities(
    debouncedFacilitySearch.length >= 2 ? { search: debouncedFacilitySearch, size: 12 } : { size: 8 },
  );
  const facilities = facilitiesData?.data ?? [];

  const facilityId = selectedFacility?.id;
  const { data: providers = [] } = useBookingRoutingProviders(
    facilityId,
    targetType === "PRACTITIONER" ? debouncedFacilitySearch : undefined,
  );
  const { data: workspaces = [] } = useBookingRoutingWorkspaces(
    targetType === "WORKSPACE" ? facilityId : undefined,
  );
  const { data: resources = [] } = useBookingRoutingResources(
    targetType === "RESOURCE" ? facilityId : undefined,
  );

  const routingOptions = useMemo(() => {
    if (targetType === "PRACTITIONER") return providers;
    if (targetType === "WORKSPACE") return workspaces;
    if (targetType === "RESOURCE") return resources;
    if (targetType === "FACILITY_SERVICE") {
      return [
        { id: "OPD", label: "Outpatient department" },
        { id: "EMERGENCY", label: "Emergency / casualty" },
        { id: "MATERNITY", label: "Maternity" },
        { id: "PHARMACY", label: "Pharmacy services" },
      ];
    }
    if (targetType === "SPECIALTY_POOL") {
      return [
        { id: "GENERAL", label: "General practice pool" },
        { id: "PAEDIATRICS", label: "Paediatrics pool" },
        { id: "MENTAL_HEALTH", label: "Mental health pool" },
      ];
    }
    return [];
  }, [targetType, providers, workspaces, resources]);

  const { data: availabilitySlots = [], isLoading: availabilityLoading } = useBookingAvailability(
    targetType || undefined,
    targetRef || undefined,
    facilityId,
    preferredDate || undefined,
  );

  useEffect(() => {
    if (searchTimeout.current) clearTimeout(searchTimeout.current);
    searchTimeout.current = setTimeout(() => setDebouncedFacilitySearch(facilitySearch), 300);
  }, [facilitySearch]);

  const stepIndex = STEPS.findIndex((s) => s.id === step);

  function handleFacilitySearchChange(value: string) {
    setFacilitySearch(value);
    setSelectedFacility(null);
    setShowFacilityDropdown(true);
  }

  async function handleRequestMvumo() {
    if (!submittedId) return;
    await requestMvumo.mutateAsync({
      bookingId: submittedId,
      mvumoType: "CONSENT",
      consentType: channel === "VIRTUAL" ? "TELEMEDICINE" : "TREATMENT",
    });
    setMvumoRequested(true);
  }

  async function handleSubmit() {
    if (!selectedFacility || !preferredDate || !targetType || !targetRef) return;
    const preferredStartTime = `${preferredDate}T${preferredTime}:00Z`;
    const result = await createBooking.mutateAsync({
      bookingType,
      facilityId: selectedFacility.id,
      targetType,
      targetRef,
      preferredStartTime,
      preferredChannel: channel,
      reasonForBooking: reason || undefined,
      serviceName: targetLabel || undefined,
    });
    if (result?.id) {
      setSubmittedId(result.id);
      router.push(`/home/bookings/${result.id}`);
    }
  }

  if (submittedId && step === "review") {
    return (
      <AppLayout>
        <PageShell title="Booking submitted">
          <div className="max-w-lg mx-auto text-center py-12">
            <CheckCircle2 className="w-14 h-14 text-primary mx-auto mb-4" />
            <h2 className="text-lg font-semibold text-foreground mb-2">Booking request submitted</h2>
            <p className="text-sm text-muted-foreground mb-6">
              Your booking is pending facility review. You will receive an appointment once confirmed.
            </p>
            <Link
              href={`/home/bookings/${submittedId}`}
              className="inline-flex rounded-lg bg-primary px-4 py-2 text-sm font-medium text-white hover:bg-primary-hover"
            >
              View booking details
            </Link>
          </div>
        </PageShell>
      </AppLayout>
    );
  }

  return (
    <AppLayout>
      <PageShell title="Book a service" subtitle="Request access to care — bookings become appointments when confirmed">
        <div className="mb-4 flex items-center gap-3">
          <Link href="/home/bookings" className="inline-flex text-sm text-muted-foreground hover:text-foreground">
            ← My Bookings
          </Link>
          {/* Display-only recognition badge for the booking citizen — never affects slot ordering. */}
          <RecognisedProviderChip healthId={user?.healthId ?? user?.id} />
        </div>

        <nav className="mb-6 flex flex-wrap gap-2">
          {STEPS.map((s, i) => (
            <button
              key={s.id}
              type="button"
              onClick={() => i <= stepIndex && setStep(s.id)}
              className={`rounded-full px-3 py-1 text-xs font-medium ${
                s.id === step
                  ? "bg-primary text-white"
                  : i < stepIndex
                    ? "bg-primary-soft text-primary-hover"
                    : "bg-neutral-100 text-muted-foreground"
              }`}
            >
              {s.label}
            </button>
          ))}
        </nav>

        <div className="rounded-xl border border-border bg-card p-5 shadow-sm space-y-4 max-w-2xl">
          {step === "target" && (
            <>
              <h2 className="text-sm font-semibold text-foreground">Who or what are you booking?</h2>
              <div className="grid gap-2 sm:grid-cols-2">
                {TARGET_TYPES.map((t) => {
                  const Icon = t.icon;
                  return (
                    <button
                      key={t.id}
                      type="button"
                      data-testid={`target-type-${t.id}`}
                      onClick={() => {
                        setTargetType(t.id);
                        setTargetRef("");
                        setTargetLabel("");
                        setStep("facility");
                      }}
                      className={`flex items-center gap-3 rounded-lg border p-3 text-left text-sm transition-colors ${
                        targetType === t.id
                          ? "border-impilo-500 bg-primary-soft"
                          : "border-border hover:border-impilo-300"
                      }`}
                    >
                      <Icon className="h-5 w-5 text-primary shrink-0" />
                      {t.label}
                    </button>
                  );
                })}
              </div>
            </>
          )}

          {step === "facility" && (
            <>
              <h2 className="text-sm font-semibold text-foreground">Choose a facility</h2>
              <div className="relative">
                <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
                <input
                  type="text"
                  value={facilitySearch}
                  onChange={(e) => handleFacilitySearchChange(e.target.value)}
                  onFocus={() => setShowFacilityDropdown(true)}
                  placeholder="Search clinics and hospitals…"
                  className="w-full rounded-lg border border-border py-2.5 pl-10 pr-3 text-sm"
                />
              </div>
              {showFacilityDropdown && !selectedFacility && (
                <div className="max-h-48 overflow-y-auto rounded-lg border border-border">
                  {facilitiesLoading ? (
                    <p className="px-3 py-3 text-xs text-muted-foreground">Searching…</p>
                  ) : (
                    facilities.map((f) => (
                      <button
                        key={f.id}
                        type="button"
                        data-testid={`booking-facility-${f.id}`}
                        onClick={() => {
                          setSelectedFacility(f);
                          setFacilitySearch(f.attributes.name);
                          setShowFacilityDropdown(false);
                          setStep("datetime");
                        }}
                        className="block w-full px-3 py-2 text-left text-sm hover:bg-primary-soft"
                      >
                        {f.attributes.name}
                      </button>
                    ))
                  )}
                </div>
              )}
            </>
          )}

          {step === "datetime" && selectedFacility && (
            <>
              <h2 className="text-sm font-semibold text-foreground">Preferred date & time</h2>
              {routingOptions.length > 0 && !targetRef && (
                <div>
                  <label className="mb-1 block text-xs font-medium text-muted-foreground">Select target</label>
                  <select
                    value={targetRef}
                    onChange={(e) => {
                      const opt = routingOptions.find((o) => o.id === e.target.value);
                      setTargetRef(e.target.value);
                      setTargetLabel(opt?.label ?? "");
                    }}
                    className="w-full rounded-lg border border-border px-3 py-2 text-sm"
                  >
                    <option value="">Choose…</option>
                    {routingOptions.map((o) => (
                      <option key={o.id} value={o.id}>
                        {o.label}
                        {o.subtitle ? ` — ${o.subtitle}` : ""}
                      </option>
                    ))}
                  </select>
                </div>
              )}
              <input
                type="date"
                value={preferredDate}
                onChange={(e) => setPreferredDate(e.target.value)}
                className="w-full rounded-lg border border-border px-3 py-2 text-sm"
              />
              {preferredDate && targetRef && availabilitySlots.length > 0 ? (
                <div className="grid grid-cols-4 gap-2">
                  {availabilitySlots.map((slot) => (
                    <button
                      key={slot.time}
                      type="button"
                      disabled={!slot.available}
                      onClick={() => setPreferredTime(slot.time)}
                      className={`rounded-lg border px-2 py-1.5 text-xs ${
                        preferredTime === slot.time
                          ? "border-impilo-500 bg-primary text-white"
                          : slot.available
                            ? "border-border"
                            : "opacity-40 line-through"
                      }`}
                    >
                      {slot.time}
                    </button>
                  ))}
                </div>
              ) : (
                <select
                  value={preferredTime}
                  onChange={(e) => setPreferredTime(e.target.value)}
                  className="w-full rounded-lg border border-border px-3 py-2 text-sm"
                >
                  {FALLBACK_TIMES.map((t) => (
                    <option key={t} value={t}>{t}</option>
                  ))}
                </select>
              )}
              {availabilityLoading && <Loader2 className="h-4 w-4 animate-spin text-muted-foreground" />}
              <button
                type="button"
                disabled={!preferredDate || !targetRef}
                onClick={() => setStep("details")}
                className="w-full rounded-lg bg-primary py-2.5 text-sm font-medium text-white disabled:opacity-50"
              >
                Continue
              </button>
            </>
          )}

          {step === "details" && (
            <>
              <h2 className="text-sm font-semibold text-foreground">Visit details</h2>
              <select
                value={bookingType}
                onChange={(e) => setBookingType(e.target.value)}
                className="w-full rounded-lg border border-border px-3 py-2 text-sm"
              >
                {BOOKING_TYPES.map((t) => (
                  <option key={t.value} value={t.value}>{t.label}</option>
                ))}
              </select>
              <select
                value={channel}
                onChange={(e) => setChannel(e.target.value as BookingChannel)}
                className="w-full rounded-lg border border-border px-3 py-2 text-sm"
              >
                {CHANNELS.map((c) => (
                  <option key={c.value} value={c.value}>{c.label}</option>
                ))}
              </select>
              <input
                type="text"
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                placeholder="Reason for booking (optional)"
                className="w-full rounded-lg border border-border px-3 py-2 text-sm"
              />
              <button
                type="button"
                onClick={() => setStep("mvumo")}
                className="w-full rounded-lg bg-primary py-2.5 text-sm font-medium text-white"
              >
                Continue to consent
              </button>
            </>
          )}

          {step === "mvumo" && (
            <>
              <h2 className="text-sm font-semibold text-foreground flex items-center gap-2">
                <Shield className="h-4 w-4 text-primary" />
                Mvumo consent & authorisation
              </h2>
              <p className="text-xs text-muted-foreground">
                Some bookings require digital consent, agreement, or authorisation before the facility can confirm your visit.
              </p>
              <button
                type="button"
                onClick={() => void handleRequestMvumo()}
                disabled={requestMvumo.isPending || mvumoRequested}
                className="w-full rounded-lg border border-impilo-300 py-2.5 text-sm font-medium text-primary-hover hover:bg-primary-soft disabled:opacity-50"
              >
                {mvumoRequested ? "Consent requested" : "Request Mvumo consent"}
              </button>
              <button
                type="button"
                onClick={() => setStep("payment")}
                className="w-full rounded-lg bg-primary py-2.5 text-sm font-medium text-white"
              >
                Continue
              </button>
            </>
          )}

          {step === "payment" && (
            <>
              <h2 className="text-sm font-semibold text-foreground flex items-center gap-2">
                <Wallet className="h-4 w-4 text-primary" />
                Payment acknowledgement
              </h2>
              <p className="text-xs text-muted-foreground">
                Acknowledge that fees, exemptions, or programme coverage may apply before your booking is confirmed.
              </p>
              <label className="flex items-start gap-2 text-sm text-foreground">
                <input
                  type="checkbox"
                  checked={paymentAcknowledged}
                  onChange={(e) => setPaymentAcknowledged(e.target.checked)}
                  className="mt-1"
                />
                I understand payment or coverage may be required before confirmation.
              </label>
              <button
                type="button"
                disabled={!paymentAcknowledged}
                onClick={() => setStep("review")}
                className="w-full rounded-lg bg-primary py-2.5 text-sm font-medium text-white disabled:opacity-50"
              >
                Review & submit
              </button>
            </>
          )}

          {step === "review" && (
            <>
              <h2 className="text-sm font-semibold text-foreground">Review your booking</h2>
              <dl className="space-y-2 text-sm text-foreground">
                <div><dt className="text-xs text-muted-foreground">Facility</dt><dd>{selectedFacility?.attributes.name}</dd></div>
                <div><dt className="text-xs text-muted-foreground">Target</dt><dd>{targetLabel || targetRef}</dd></div>
                <div><dt className="text-xs text-muted-foreground">When</dt><dd>{preferredDate} {preferredTime}</dd></div>
                <div><dt className="text-xs text-muted-foreground">Channel</dt><dd>{channel}</dd></div>
              </dl>
              {createBooking.isError && (
                <p className="text-xs text-red-600">Could not submit booking. Please try again.</p>
              )}
              <button
                type="button"
                onClick={() => void handleSubmit()}
                disabled={createBooking.isPending}
                className="w-full inline-flex items-center justify-center gap-2 rounded-lg bg-primary py-2.5 text-sm font-medium text-white disabled:opacity-50"
              >
                {createBooking.isPending ? (
                  <><Loader2 className="h-4 w-4 animate-spin" /> Submitting…</>
                ) : (
                  <><Calendar className="h-4 w-4" /> Submit booking request</>
                )}
              </button>
            </>
          )}
        </div>

        {step !== "target" && (
          <button
            type="button"
            onClick={() => setStep(STEPS[Math.max(0, stepIndex - 1)].id)}
            className="mt-4 text-sm text-muted-foreground hover:text-foreground"
          >
            ← Back
          </button>
        )}
      </PageShell>
    </AppLayout>
  );
}
