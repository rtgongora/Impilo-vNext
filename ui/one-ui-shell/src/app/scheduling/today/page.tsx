"use client";

/**
 * Today's confirmed appointments — provider operational workload.
 * Route: /scheduling/today
 */

import Link from "next/link";
import { useMemo } from "react";
import { ArrowLeft, CalendarDays, CheckCircle2, Clock, Loader2, Video } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { FacilityWorkClusterRibbon } from "@/components/experience/FacilityWorkClusterRibbon";
import { OrganizationPlaneContextBar } from "@/components/experience/OrganizationPlaneContextBar";
import { PageShell } from "@/components/PageShell";
import { useCheckInAppointment } from "@/hooks/queries/useAppointments";
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
            className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700"
          >
            <ArrowLeft className="h-4 w-4" /> Scheduling
          </Link>
          <Link
            href="/scheduling/booking-requests"
            className="rounded-lg border border-gray-200 px-3 py-1.5 text-xs font-medium text-gray-700 hover:bg-gray-50"
          >
            Booking requests
          </Link>
        </div>

        {!facility?.id ? (
          <p className="text-sm text-gray-500">Select a facility to view today&apos;s appointments.</p>
        ) : isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="h-6 w-6 animate-spin text-gray-400" />
          </div>
        ) : today.length === 0 ? (
          <div className="rounded-lg border border-gray-200 bg-white p-12 text-center">
            <CalendarDays className="mx-auto mb-3 h-10 w-10 text-gray-300" />
            <p className="text-sm text-gray-500">No confirmed appointments scheduled for today.</p>
          </div>
        ) : (
          <div className="space-y-2">
            {today.map((appt) => {
              const statusStyle =
                APPOINTMENT_STATUS_STYLES[appt.status.toUpperCase()] ??
                "bg-gray-100 text-gray-600";
              const isVirtual =
                appt.channel?.toUpperCase() === "VIRTUAL" ||
                appt.appointmentType?.toUpperCase() === "TELEMEDICINE";
              return (
                <div
                  key={appt.id}
                  className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-gray-200 bg-white p-4"
                >
                  <div>
                    <div className="flex items-center gap-2">
                      <span className="text-sm font-semibold text-gray-900">
                        {formatTime(appt.startTime)}
                      </span>
                      <span className={`rounded-full px-2 py-0.5 text-xs font-medium ${statusStyle}`}>
                        {appt.status.replace(/_/g, " ")}
                      </span>
                    </div>
                    <p className="mt-0.5 text-sm text-gray-700">
                      {appt.appointmentType.replace(/_/g, " ")}
                    </p>
                    {appt.providerName && (
                      <p className="text-xs text-gray-500 flex items-center gap-1 mt-0.5">
                        <Clock className="h-3 w-3" />
                        {appt.providerName}
                      </p>
                    )}
                  </div>
                  <div className="flex gap-2">
                    <button
                      type="button"
                      onClick={() => checkIn.mutate(appt.id)}
                      disabled={checkIn.isPending}
                      className="inline-flex items-center gap-1 rounded-lg bg-impilo-500 px-3 py-1.5 text-xs font-medium text-white hover:bg-impilo-600 disabled:opacity-50"
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
                </div>
              );
            })}
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
