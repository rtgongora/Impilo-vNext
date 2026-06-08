"use client";

/**
 * Facility booking / Mvumo configuration — admin surface.
 * Route: /scheduling/bookings/config
 */

import Link from "next/link";
import { useEffect, useState } from "react";
import { ArrowLeft, Loader2, Save, Shield } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { FacilityWorkClusterRibbon } from "@/components/experience/FacilityWorkClusterRibbon";
import { OrganizationPlaneContextBar } from "@/components/experience/OrganizationPlaneContextBar";
import { PageShell } from "@/components/PageShell";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import {
  useBookingFacilityConfig,
  useUpdateBookingFacilityConfig,
} from "@/hooks/queries/useBookings";
import type { BookingChannel } from "@/lib/booking-bff";

export default function BookingConfigPage() {
  const facility = useFacilityStore((s) => s.facility);
  const { data: config, isLoading } = useBookingFacilityConfig(facility?.id);
  const updateConfig = useUpdateBookingFacilityConfig();

  const [requireConsent, setRequireConsent] = useState(true);
  const [requireAgreement, setRequireAgreement] = useState(false);
  const [requireAuthorisation, setRequireAuthorisation] = useState(false);
  const [requirePayment, setRequirePayment] = useState(false);
  const [defaultChannel, setDefaultChannel] = useState<BookingChannel>("PHYSICAL");
  const [windowDays, setWindowDays] = useState(30);

  useEffect(() => {
    if (!config) return;
    setRequireConsent(config.requireMvumoConsent ?? true);
    setRequireAgreement(config.requireMvumoAgreement ?? false);
    setRequireAuthorisation(config.requireMvumoAuthorisation ?? false);
    setRequirePayment(config.requirePaymentBeforeConfirm ?? false);
    setDefaultChannel(config.defaultBookingChannel ?? "PHYSICAL");
    setWindowDays(config.availabilityWindowDays ?? 30);
  }, [config]);

  async function handleSave() {
    if (!facility?.id) return;
    await updateConfig.mutateAsync({
      facilityId: facility.id,
      requireMvumoConsent: requireConsent,
      requireMvumoAgreement: requireAgreement,
      requireMvumoAuthorisation: requireAuthorisation,
      requirePaymentBeforeConfirm: requirePayment,
      defaultBookingChannel: defaultChannel,
      availabilityWindowDays: windowDays,
    });
  }

  return (
    <AppLayout>
      <OrganizationPlaneContextBar />
      <FacilityWorkClusterRibbon shiftExpected={false} />
      <PageShell
        title="Booking configuration"
        subtitle="Mvumo requirements and availability rules for facility bookings"
      >
        <Link
          href="/scheduling/booking-requests"
          className="mb-4 inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700"
        >
          <ArrowLeft className="h-4 w-4" /> Booking requests
        </Link>

        {!facility?.id ? (
          <p className="text-sm text-gray-500">Select a facility to manage booking configuration.</p>
        ) : isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="h-6 w-6 animate-spin text-gray-400" />
          </div>
        ) : (
          <div className="max-w-lg space-y-6 rounded-xl border border-gray-200 bg-white p-5">
            <h2 className="text-sm font-semibold text-gray-900 flex items-center gap-2">
              <Shield className="h-4 w-4 text-impilo-500" />
              Mvumo gates — {facility.name}
            </h2>

            <div className="space-y-3 text-sm text-gray-700">
              <label className="flex items-center gap-2">
                <input
                  type="checkbox"
                  checked={requireConsent}
                  onChange={(e) => setRequireConsent(e.target.checked)}
                />
                Require Mvumo consent before confirmation
              </label>
              <label className="flex items-center gap-2">
                <input
                  type="checkbox"
                  checked={requireAgreement}
                  onChange={(e) => setRequireAgreement(e.target.checked)}
                />
                Require service agreement
              </label>
              <label className="flex items-center gap-2">
                <input
                  type="checkbox"
                  checked={requireAuthorisation}
                  onChange={(e) => setRequireAuthorisation(e.target.checked)}
                />
                Require data-sharing authorisation
              </label>
              <label className="flex items-center gap-2">
                <input
                  type="checkbox"
                  checked={requirePayment}
                  onChange={(e) => setRequirePayment(e.target.checked)}
                />
                Require payment acknowledgement before confirmation
              </label>
            </div>

            <div>
              <label className="mb-1 block text-xs font-medium text-gray-600">Default booking channel</label>
              <select
                value={defaultChannel}
                onChange={(e) => setDefaultChannel(e.target.value as BookingChannel)}
                className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm"
              >
                <option value="PHYSICAL">In person</option>
                <option value="VIRTUAL">Virtual</option>
                <option value="HOME">Home visit</option>
                <option value="COMMUNITY">Community</option>
                <option value="HYBRID">Hybrid</option>
              </select>
            </div>

            <div>
              <label className="mb-1 block text-xs font-medium text-gray-600">
                Availability window (days ahead)
              </label>
              <input
                type="number"
                min={1}
                max={180}
                value={windowDays}
                onChange={(e) => setWindowDays(Number(e.target.value))}
                className="w-full rounded-lg border border-gray-300 px-3 py-2 text-sm"
              />
            </div>

            <button
              type="button"
              onClick={() => void handleSave()}
              disabled={updateConfig.isPending}
              className="inline-flex items-center gap-2 rounded-lg bg-impilo-500 px-4 py-2.5 text-sm font-medium text-white hover:bg-impilo-600 disabled:opacity-50"
            >
              {updateConfig.isPending ? (
                <Loader2 className="h-4 w-4 animate-spin" />
              ) : (
                <Save className="h-4 w-4" />
              )}
              Save configuration
            </button>
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
