"use client";

/**
 * Route / Referral — assign a destination to a diagnostic order (criterion A, routing half).
 * Route: /diagnostics/orders/route?orderId=… | Zone: lab | Guard: facility
 *
 * Real write path: UI → BFF (/internal/v1/diagnostics/orders/{id}/route) → OROS → DB.
 * Destinations are entered directly pending the directory-backed picker (W8 provider directory).
 */

import { Suspense, useState } from "react";
import { useSearchParams } from "next/navigation";
import { Loader2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useRouteDiagnosticOrder } from "@/hooks/queries/useDiagnosticsOrders";

const DESTINATION_TYPES = [
  "INTERNAL_DEPARTMENT",
  "INTERNAL_SERVICE_POINT",
  "EXTERNAL_PROVIDER",
  "EXTERNAL_FACILITY",
  "PATIENT_CARRIED",
] as const;

const RETURN_METHODS = ["IN_PLATFORM", "SECURE_LINK", "PATIENT_CARRIED", "EXTERNAL_SYSTEM"] as const;

function RoutingForm() {
  const params = useSearchParams();
  const [orderId, setOrderId] = useState(params.get("orderId") ?? "");
  const [destinationType, setDestinationType] = useState<string>("INTERNAL_DEPARTMENT");
  const [destinationName, setDestinationName] = useState("");
  const [facilityId, setFacilityId] = useState("");
  const [departmentId, setDepartmentId] = useState("");
  const [servicePointId, setServicePointId] = useState("");
  const [providerId, setProviderId] = useState("");
  const [returnMethod, setReturnMethod] = useState<string>("IN_PLATFORM");
  const [routed, setRouted] = useState(false);

  const routeMut = useRouteDiagnosticOrder();
  const canSubmit = orderId.trim().length > 0 && !routeMut.isPending;

  const showDept = destinationType === "INTERNAL_DEPARTMENT";
  const showServicePoint = destinationType === "INTERNAL_SERVICE_POINT";
  const showProvider = destinationType === "EXTERNAL_PROVIDER";
  const showFacility =
    destinationType === "EXTERNAL_FACILITY" || showDept || showServicePoint || showProvider;

  function submit(e: React.FormEvent) {
    e.preventDefault();
    setRouted(false);
    routeMut.mutate(
      {
        orderId: orderId.trim(),
        destinationType,
        destinationName: destinationName.trim() || undefined,
        destinationFacilityId: showFacility ? facilityId.trim() : undefined,
        destinationDepartmentId: showDept ? departmentId.trim() : undefined,
        destinationServicePointId: showServicePoint ? servicePointId.trim() : undefined,
        destinationProviderId: showProvider ? providerId.trim() : undefined,
        expectedReturnMethod: returnMethod,
      },
      { onSuccess: () => setRouted(true) },
    );
  }

  return (
    <form onSubmit={submit} className="max-w-2xl space-y-4">
      <label className="block">
        <span className="mb-1 block text-sm font-medium">Order ID<span className="text-danger"> *</span></span>
        <input value={orderId} onChange={(e) => setOrderId(e.target.value)}
          className="impilo-pill-input w-full" aria-label="Order ID" placeholder="ORD-…" />
      </label>

      <label className="block">
        <span className="mb-1 block text-sm font-medium">Destination type</span>
        <select value={destinationType} onChange={(e) => setDestinationType(e.target.value)}
          className="impilo-pill-input w-full" aria-label="Destination type">
          {DESTINATION_TYPES.map((t) => <option key={t} value={t}>{t.replace(/_/g, " ")}</option>)}
        </select>
      </label>

      <label className="block">
        <span className="mb-1 block text-sm font-medium">Destination name</span>
        <input value={destinationName} onChange={(e) => setDestinationName(e.target.value)}
          className="impilo-pill-input w-full" aria-label="Destination name" placeholder="e.g. City Radiology" />
      </label>

      {showFacility && (
        <label className="block">
          <span className="mb-1 block text-sm font-medium">Facility ID</span>
          <input value={facilityId} onChange={(e) => setFacilityId(e.target.value)}
            className="impilo-pill-input w-full" aria-label="Facility ID" />
        </label>
      )}
      {showDept && (
        <label className="block">
          <span className="mb-1 block text-sm font-medium">Department ID</span>
          <input value={departmentId} onChange={(e) => setDepartmentId(e.target.value)}
            className="impilo-pill-input w-full" aria-label="Department ID" />
        </label>
      )}
      {showServicePoint && (
        <label className="block">
          <span className="mb-1 block text-sm font-medium">Service point ID</span>
          <input value={servicePointId} onChange={(e) => setServicePointId(e.target.value)}
            className="impilo-pill-input w-full" aria-label="Service point ID" />
        </label>
      )}
      {showProvider && (
        <label className="block">
          <span className="mb-1 block text-sm font-medium">Provider ID</span>
          <input value={providerId} onChange={(e) => setProviderId(e.target.value)}
            className="impilo-pill-input w-full" aria-label="Provider ID" />
        </label>
      )}

      <label className="block">
        <span className="mb-1 block text-sm font-medium">Expected result-return method</span>
        <select value={returnMethod} onChange={(e) => setReturnMethod(e.target.value)}
          className="impilo-pill-input w-full" aria-label="Return method">
          {RETURN_METHODS.map((m) => <option key={m} value={m}>{m.replace(/_/g, " ")}</option>)}
        </select>
      </label>

      <div className="flex items-center gap-3">
        <button type="submit" disabled={!canSubmit}
          className="rounded-lg bg-primary px-4 py-2 text-sm font-medium text-primary-foreground disabled:opacity-50">
          {routeMut.isPending ? (
            <span className="flex items-center gap-2"><Loader2 className="h-4 w-4 animate-spin" /> Routing…</span>
          ) : "Route order"}
        </button>
        {routeMut.isError && (
          <span className="text-sm text-danger" role="alert">Failed to route order. Please retry.</span>
        )}
        {routed && (
          <span className="text-sm text-success-foreground" role="status">Order routed.</span>
        )}
      </div>
    </form>
  );
}

export default function RouteDiagnosticOrderPage() {
  return (
    <AppLayout>
      <PageShell title="Route Order" subtitle="Assign a fulfilment destination (referral)">
        <Suspense fallback={<div className="p-8 text-sm text-muted-foreground">Loading…</div>}>
          <RoutingForm />
        </Suspense>
      </PageShell>
    </AppLayout>
  );
}
