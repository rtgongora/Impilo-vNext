"use client";

/**
 * My Devices — Health OS §2
 * Paired health devices and sensors for remote monitoring.
 * Route: /monitoring/devices | Zone: monitoring | Guard: auth
 */

import { Smartphone, Plus, Bluetooth, Wifi, BatteryMedium } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";

export default function DevicesPage() {
  return (
    <AppLayout>
      <PageShell
        title="My Devices"
        subtitle="Paired health devices and sensors for remote monitoring"
        icon={<Smartphone className="h-6 w-6" />}
      >
        <div className="space-y-6">
          {/* Actions bar */}
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-3">
              <span className="text-sm text-gray-500">Connected devices: 0</span>
            </div>
            <button className="inline-flex items-center gap-2 rounded-lg bg-orange-600 px-4 py-2 text-sm font-medium text-white hover:bg-orange-700 transition-colors">
              <Plus className="h-4 w-4" />
              Pair Device
            </button>
          </div>

          {/* Supported device types */}
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            {[
              { label: "Bluetooth Devices", description: "Blood pressure monitors, glucometers, pulse oximeters", Icon: Bluetooth, color: "bg-blue-50 text-blue-600" },
              { label: "Wi-Fi Devices", description: "Smart scales, sleep trackers, home monitors", Icon: Wifi, color: "bg-green-50 text-green-600" },
              { label: "Wearables", description: "Fitness bands, smartwatches, CGM sensors", Icon: BatteryMedium, color: "bg-purple-50 text-purple-600" },
            ].map(({ label, description, Icon, color }) => (
              <div key={label} className="rounded-lg border border-gray-200 bg-white p-5">
                <div className="flex items-center gap-3 mb-2">
                  <div className={`rounded-lg p-2 ${color.split(" ")[0]}`}>
                    <Icon className={`h-5 w-5 ${color.split(" ")[1]}`} />
                  </div>
                  <h3 className="font-semibold text-gray-900">{label}</h3>
                </div>
                <p className="text-sm text-gray-600">{description}</p>
              </div>
            ))}
          </div>

          {/* Empty state */}
          <div className="rounded-lg border border-dashed border-gray-300 bg-gray-50 p-12 text-center">
            <Smartphone className="mx-auto h-12 w-12 text-gray-400" />
            <h3 className="mt-4 text-sm font-semibold text-gray-900">No devices paired</h3>
            <p className="mt-2 text-sm text-gray-600">
              Pair a health device to automatically sync readings and track your health metrics.
            </p>
          </div>
        </div>
      </PageShell>
    </AppLayout>
  );
}
