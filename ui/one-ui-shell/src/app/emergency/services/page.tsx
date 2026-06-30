"use client";

/**
 * Nearest emergency services — honest seam. Daidzai does not own a map engine;
 * routing/nearest-service is owner-routed to Ndila. This page explains that and
 * links to the SOS path. It does NOT fake a live availability/map widget.
 * Route: /emergency/services
 */

import Link from "next/link";
import { MapPin, Ambulance } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { NompiloContextualGuidance } from "@/components/intelligent/NompiloContextualGuidance";

export default function EmergencyServicesPage() {
  return (
    <AppLayout>
      <PageShell
        title="Nearest services"
        subtitle="Find facilities able to help right now"
        serviceSlug="daidzai"
      >
        <div className="grid gap-6 lg:grid-cols-[1fr_320px]">
          <div className="space-y-4">
            <div className="flex items-start gap-3 rounded-2xl border border-teal-200 bg-teal-50 p-5 text-sm text-teal-900">
              <MapPin className="mt-0.5 h-5 w-5 shrink-0" />
              <div>
                <div className="font-semibold">Nearest-service routing is provided by Ndila</div>
                <p className="mt-1">
                  Directions and nearest-facility routing come from Ndila (the maps service). For a
                  life-threatening emergency, raise an SOS instead of travelling — the response team
                  can come to you.
                </p>
              </div>
            </div>

            <Link
              href="/emergency/sos"
              className="flex items-center justify-center gap-2 rounded-xl bg-red-600 px-4 py-3 text-base font-semibold text-white shadow-sm transition hover:bg-red-700"
            >
              <Ambulance className="h-5 w-5" /> Raise an SOS instead
            </Link>
          </div>

          <NompiloContextualGuidance routePath="/emergency/services" />
        </div>
      </PageShell>
    </AppLayout>
  );
}
