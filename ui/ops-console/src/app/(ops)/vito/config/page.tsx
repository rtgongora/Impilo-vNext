"use client";

import React, { useEffect, useState } from "react";
import { vitoApi, type RegistryMode } from "@/lib/vitoApi";

/**
 * Client Configuration — toggle OpenCR vs Standalone mode.
 * Displays current registry mode and OpenCR connection status.
 */
export default function VitoConfigPage() {
  const [config, setConfig] = useState<RegistryMode>({
    mode: "STANDALONE",
    opencrEnabled: false,
  });
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    async function loadMode() {
      try {
        const data = await vitoApi.getRegistryMode();
        setConfig(data);
      } catch {
        // Default to standalone
      } finally {
        setLoading(false);
      }
    }
    loadMode();
  }, []);

  if (loading) {
    return (
      <div className="max-w-7xl mx-auto px-6 py-8">
        <p className="text-neutral-500">Loading configuration...</p>
      </div>
    );
  }

  return (
    <div className="max-w-7xl mx-auto px-6 py-8">
      <div className="mb-6">
        <h1 className="text-2xl font-semibold text-neutral-900">
          Registry Configuration
        </h1>
        <p className="text-sm text-neutral-500 mt-1">
          VITO registry mode and external system integration
        </p>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        {/* Current Mode */}
        <div className="bg-card rounded-[12px] shadow-subtle border border-neutral-100 p-6">
          <h2 className="text-sm font-medium text-neutral-500 mb-4">Current Mode</h2>
          <div className="flex items-center gap-3">
            <span className={`w-3 h-3 rounded-full ${
              config.mode === "OPENCR" ? "bg-info" : "bg-success"
            }`} />
            <span className="text-lg font-semibold text-neutral-900">{config.mode}</span>
          </div>
          <p className="text-sm text-neutral-500 mt-3">
            {config.mode === "OPENCR"
              ? "Connected to external OpenCR instance for federated identity matching. FHIR $match operations route to the external registry."
              : "Operating as a standalone registry. All identity operations are handled locally. Use this mode for independent deployments."}
          </p>
          <p className="text-xs text-neutral-500 mt-4 bg-neutral-50 p-3 rounded-[8px]">
            Mode is configured via <code>VITO_REGISTRY_MODE</code> environment variable.
            Restart the service to switch modes.
          </p>
        </div>

        {/* OpenCR Status */}
        <div className="bg-card rounded-[12px] shadow-subtle border border-neutral-100 p-6">
          <h2 className="text-sm font-medium text-neutral-500 mb-4">OpenCR Integration</h2>
          <div className="flex items-center gap-3">
            <span className={`w-3 h-3 rounded-full ${
              config.opencrEnabled ? "bg-success" : "bg-neutral-500"
            }`} />
            <span className="text-lg font-semibold text-neutral-900">
              {config.opencrEnabled ? "Enabled" : "Disabled"}
            </span>
          </div>
          <p className="text-sm text-neutral-500 mt-3">
            {config.opencrEnabled
              ? "FHIR $match operations are routed to the external OpenCR instance for cross-registry identity resolution."
              : "OpenCR integration is not active. Enable by setting VITO_REGISTRY_MODE=OPENCR."}
          </p>

          <div className="mt-4 space-y-2">
            <div className="flex justify-between text-sm">
              <span className="text-neutral-500">Protocol</span>
              <span className="text-neutral-900">HL7 FHIR R4</span>
            </div>
            <div className="flex justify-between text-sm">
              <span className="text-neutral-500">Operation</span>
              <span className="text-neutral-900">Patient/$match</span>
            </div>
            <div className="flex justify-between text-sm">
              <span className="text-neutral-500">Fallback</span>
              <span className="text-neutral-900">Standalone (graceful degradation)</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
