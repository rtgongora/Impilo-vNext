"use client";

/**
 * Admin Queue Configuration — Manage queue definitions, service types, SLAs.
 * Route: /admin/queues
 */

import Link from "next/link";
import { ArrowLeft, Clock, Loader2, Settings, Users } from "lucide-react";
import { useQuery } from "@tanstack/react-query";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { apiClient } from "@/lib/api-client";

const SERVICE_TYPE_STYLES: Record<string, string> = {
  OPD: "bg-blue-100 text-blue-700",
  EMERGENCY: "bg-red-100 text-red-700",
  PHARMACY: "bg-green-100 text-green-700",
  LABORATORY: "bg-amber-100 text-amber-700",
  GENERAL: "bg-gray-100 text-gray-600",
};

export default function AdminQueuesPage() {
  const facility = useFacilityStore((s) => s.facility);

  const { data, isLoading } = useQuery<{ data: Array<{ id: string; attributes: Record<string, unknown> }> }>({
    queryKey: ["queue-definitions", facility?.id],
    queryFn: () => apiClient.get(`/internal/v1/queue/definitions?facility_id=${facility?.id}`),
    enabled: !!facility?.id,
  });

  const definitions = data?.data ?? [];

  return (
    <AppLayout>
      <PageShell title="Queue Configuration" subtitle="Service types, SLA targets, and operating hours">
        <div className="mb-4">
          <Link href="/admin" className="inline-flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700">
            <ArrowLeft className="w-4 h-4" /> Back to Admin
          </Link>
        </div>

        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
          </div>
        ) : definitions.length === 0 ? (
          <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
            <Settings className="w-10 h-10 text-gray-300 mx-auto mb-3" />
            <p className="text-gray-400 text-sm">No queue definitions configured.</p>
          </div>
        ) : (
          <div className="space-y-3">
            {definitions.map((def) => {
              const a = def.attributes;
              const serviceStyle = SERVICE_TYPE_STYLES[(a.serviceType as string)] ?? SERVICE_TYPE_STYLES.GENERAL;
              return (
                <div key={def.id} className="bg-white rounded-lg border border-gray-200 p-5">
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-3">
                      <Users className="w-5 h-5 text-gray-400" />
                      <div>
                        <p className="text-sm font-medium text-gray-900">{a.name as string}</p>
                        <div className="flex items-center gap-2 mt-1">
                          <span className={`px-2 py-0.5 text-xs rounded-full font-medium ${serviceStyle}`}>
                            {a.serviceType as string}
                          </span>
                          <span className="text-xs text-gray-500">Type: {a.queueType as string}</span>
                        </div>
                      </div>
                    </div>
                    <div className="flex items-center gap-4 text-xs text-gray-500">
                      <div className="flex items-center gap-1">
                        <Clock className="w-3 h-3" />
                        <span>SLA: {a.slaTargetMinutes as number}min</span>
                      </div>
                      {(a.maxCapacity as number) && (
                        <span>Capacity: {a.maxCapacity as number}</span>
                      )}
                    </div>
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
