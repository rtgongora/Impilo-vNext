"use client";

/**
 * Today's confirmed appointments — provider operational workload.
 * Route: /scheduling/today
 */

import Link from "next/link";
import { useMemo, useState } from "react";
import { AlertTriangle, ArrowLeft, CalendarDays, CheckCircle2, Clock, Loader2, Video } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { FacilityWorkClusterRibbon } from "@/components/experience/FacilityWorkClusterRibbon";
import { OrganizationPlaneContextBar } from "@/components/experience/OrganizationPlaneContextBar";
import { PageShell } from "@/components/PageShell";
import { useCheckInAppointment, type AppointmentCheckInMeta } from "@/hooks/queries/useAppointments";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { apiClient } from "@/lib/api-client";
import { APPOINTMENT_STATUS_STYLES, normalizeAppointmentList } from "@/lib/booking-bff";
import { useQuery } from "@tanstack/react-query";

function isToday(iso?: string): boolean {
  if (!iso) return false;
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return false;
  const now = new Date();
  return (
    d.getFullYear() === now.getFullYear() &&
    d.getMonth() === now.getMonth() &&
    d.getDate() === now.getDate()
  );
}

function formatTime(iso?: string): string {
  if (!iso) return "—";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
}

export default function TodayAppointmentsPage() {
  const facility = useFacilityStore((s) => s.facility);
  const checkIn = useCheckInAppointment();
  const [checkInOutcome, setCheckInOutcome] = useState<{ id: string; queueLinked: boolean } | null>(null);

  function handleCheckIn(appointmentId: string) {
    setCheckInOutcome(null);
    checkIn.mutate(appointmentId, {
      onSuccess: (response) => {
        // Honest queue truth from the BFF: queue_linked=false means the patient
        // checked in but was NOT placed in any queue. Never invent a token.
        const meta = (response?.meta ?? {}) as AppointmentCheckInMeta;
        setCheckInOutcome({ id: appointmentId, queueLinked: meta.queue_linked === true });
      },
    });
  }

  const { data: appointments = [], isLoading } = useQuery({
    queryKey: ["today-appointments", facility?.id],
    queryFn: async () => {
      const params = new URLSearchParams({ size: "100" });
      if (facility?.id) params.set("facility_id", facility.id);
      params.set("status", "CONFIRMED");
      const response = await apiClient.get(
        `/internal/v1/appointments?${params.toString()}`,
      );
      return normalizeAppointmentList(response);
    },
    enabled: !!facility?.id,
  });

  const today = useMemo(
    () =>
      appointments
        .filter((a) => isToday(a.startTime))
        .sort((a, b) => new Date(a.startTime ?? 0).getTime() - new Date(b.startTime ?? 0).getTime()),
    [appointments],
  );

  return (
    <AppLayout>
      <OrganizationPlaneContextBar />
      <FacilityWorkClusterRibbon shiftExpected={false} />
      <PageShell
        title="Today's appointments"
        subtitle="Confirmed scheduled workload for the selected facility"
      >
        <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
          <Link
            href="/scheduling"
            className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
          >
            <ArrowLeft className="h-4 w-4" /> Scheduling
          </Link>
          <Link
            href="/scheduling/booking-requests"
            className="rounded-lg border border-border px-3 py-1.5 text-xs font-medium text-foreground hover:bg-background"
          >
            Booking requests
          </Link>
        </div>

        {!facility?.id ? (
          <p className="text-sm text-muted-foreground">Select a facility to view today&apos;s appointments.</p>
        ) : isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
          </div>
        ) : today.length === 0 ? (
          <div className="rounded-lg border border-border bg-card p-12 text-center">
            <CalendarDays className="mx-auto mb-3 h-10 w-10 text-muted-foreground" />
            <p className="text-sm text-muted-foreground">No confirmed appointments scheduled for today.</p>
          </div>
        ) : (
          <div className="space-y-2">
            {today.map((appt) => {
              const statusStyle =
                APPOINTMENT_STATUS_STYLES[appt.status.toUpperCase()] ??
                "bg-neutral-100 text-muted-foreground";
              const isVirtual =
                appt.channel?.toUpperCase() === "VIRTUAL" ||
                appt.appointmentType?.toUpperCase() === "TELEMEDICINE";
              return (
                <div
                  key={appt.id}
                  className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-border bg-card p-4"
                >
                  <div>
                    <div className="flex flex-wrap items-center gap-2">
                      <span className="text-sm font-semibold text-foreground">
                        {formatTime(appt.startTime)}
                      </span>
                      <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${statusStyle}`}>
                        {appt.status.replace(/_/g, " ")}
                      </span>
                      {appt.checkInStatus === "CHECKED_IN_NO_QUEUE" && (
                        <span
                          className="inline-flex items-center gap-1 rounded-full bg-amber-100 px-2 py-0.5 text-xs font-medium text-amber-800"
                          title="The patient checked in but no queue placement was created"
                        >
                          <AlertTriangle className="h-3 w-3" /> Checked in — NOT in queue
                        </span>
                      )}
                    </div>
                    <p className="mt-0.5 text-sm text-foreground">
                      {appt.appointmentType.replace(/_/g, " ")}
                    </p>
                    {appt.providerName && (
                      <p className="text-xs text-muted-foreground flex items-center gap-1 mt-0.5">
                        <Clock className="h-3 w-3" />
                        {appt.providerName}
                      </p>
                    )}
                  </div>
                  <div className="flex gap-2">
                    <button
                      type="button"
                      onClick={() => handleCheckIn(appt.id)}
                      disabled={checkIn.isPending}
                      className="inline-flex items-center gap-1 rounded-lg bg-primary px-3 py-1.5 text-xs font-medium text-white hover:bg-primary-hover disabled:opacity-50"
                    >
                      <CheckCircle2 className="h-3 w-3" />
                      Check in
                    </button>
                    {isVirtual && (
                      <Link
                        href={
                          appt.teleconsultSessionId
                            ? `/telemedicine/session/${appt.teleconsultSessionId}`
                            : "/telemedicine"
                        }
                        className="inline-flex items-center gap-1 rounded-lg border border-green-300 px-3 py-1.5 text-xs font-medium text-green-800 hover:bg-green-50"
                      >
                        <Video className="h-3 w-3" />
                        Join
                      </Link>
                    )}
                  </div>

                  {/* Honest check-in outcome — driven by the BFF queue_linked meta */}
                  {checkInOutcome?.id === appt.id &&
                    (checkInOutcome.queueLinked ? (
                      <div className="flex w-full items-center gap-1.5 rounded-lg border border-success/25 bg-success-soft px-3 py-2 text-xs text-green-800">
                        <CheckCircle2 className="h-3.5 w-3.5 shrink-0" />
                        Checked in — patient placed in the facility queue.
                      </div>
                    ) : (
                      <div className="flex w-full items-center gap-1.5 rounded-lg border border-warning/35 bg-warning-soft px-3 py-2 text-xs text-amber-800">
                        <AlertTriangle className="h-3.5 w-3.5 shrink-0" />
                        Checked in — NOT in queue. No queue placement was created for this
                        check-in; add the patient from the Patient Queue board.
                      </div>
                    ))}
                </div>
              );
            })}
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
