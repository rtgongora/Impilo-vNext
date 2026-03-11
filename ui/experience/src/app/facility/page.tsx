"use client";

/**
 * Select Facility — Facility selection page.
 * Route: /facility | pageTitle: "Select Facility"
 */

import { useRouter } from "next/navigation";
import { Building2, MapPin, Loader2 } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useFacilities } from "@/hooks/queries/useFacilities";
import { useFacilityStore } from "@/hooks/useFacilityStore";

export default function FacilityPage() {
  const router = useRouter();
  const { data, isLoading } = useFacilities();
  const setFacility = useFacilityStore((s) => s.setFacility);

  const facilities = data?.data ?? [];

  function handleSelect(facility: (typeof facilities)[number]) {
    setFacility({
      id: facility.id,
      name: facility.attributes.name,
      code: facility.attributes.code,
      facilityType: facility.attributes.facilityType,
      capabilities: facility.attributes.capabilities,
    });
    router.push("/workspace");
  }

  return (
    <AppLayout>
      <PageShell
        title="Select Facility"
        subtitle="Choose the facility you will be working at today"
      >
        {isLoading ? (
          <div className="flex items-center justify-center py-16">
            <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
            <span className="ml-2 text-sm text-gray-500">Loading facilities...</span>
          </div>
        ) : facilities.length === 0 ? (
          <div className="bg-white rounded-lg border border-gray-200 p-12 text-center">
            <Building2 className="w-10 h-10 text-gray-300 mx-auto mb-3" />
            <p className="text-gray-400 text-sm">No facilities available</p>
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {facilities.map((facility) => (
              <button
                key={facility.id}
                onClick={() => handleSelect(facility)}
                className="bg-white rounded-lg border border-gray-200 p-5 text-left hover:border-blue-300 hover:shadow-md transition-all group"
              >
                <div className="flex items-start gap-3">
                  <div className="w-10 h-10 rounded-lg bg-blue-100 text-blue-600 flex items-center justify-center shrink-0 group-hover:bg-blue-200 transition-colors">
                    <Building2 className="w-5 h-5" />
                  </div>
                  <div className="min-w-0">
                    <h3 className="font-medium text-gray-900 text-sm truncate">
                      {facility.attributes.name}
                    </h3>
                    <p className="text-xs text-gray-500 mt-0.5">
                      {facility.attributes.facilityType}
                    </p>
                    <div className="flex items-center gap-1 mt-2 text-xs text-gray-400">
                      <MapPin className="w-3 h-3" />
                      <span>{facility.attributes.code}</span>
                    </div>
                    <div className="mt-2">
                      <span
                        className={`inline-block px-2 py-0.5 text-xs rounded-full ${
                          facility.attributes.status === "ACTIVE"
                            ? "bg-green-100 text-green-700"
                            : "bg-gray-100 text-gray-600"
                        }`}
                      >
                        {facility.attributes.status}
                      </span>
                    </div>
                  </div>
                </div>
              </button>
            ))}
          </div>
        )}
      </PageShell>
    </AppLayout>
  );
}
