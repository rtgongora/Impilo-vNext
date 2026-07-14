"use client";

/**
 * Virtual care discovery — the governed virtual-hospital directory for citizens, with
 * HONEST availability from the BFF: only virtual hospitals routable over an existing
 * specialty-pool seam are requestable today; the rest are shown as "Coming soon" with
 * no call-to-action. No static copies — cards come from /internal/v1/virtual-care.
 */

import Link from "next/link";
import { MonitorSmartphone, Loader2, Clock } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useVirtualHospitals, type VirtualHospitalCard } from "@/hooks/queries/useVirtualCare";

function HospitalCard({ hospital }: { hospital: VirtualHospitalCard }) {
  const requestable = hospital.availability === "REQUESTABLE";
  return (
    <li className="flex flex-col rounded-xl border border-border bg-card p-4 shadow-sm">
      <div className="flex items-start justify-between gap-2">
        <h3 className="font-medium text-foreground">{hospital.name}</h3>
        {requestable ? (
          <span className="shrink-0 rounded-full bg-teal-100 px-2 py-0.5 text-xs font-medium text-teal-700">
            Available
          </span>
        ) : (
          <span className="inline-flex shrink-0 items-center gap-1 rounded-full bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-500">
            <Clock className="h-3 w-3" aria-hidden /> Coming soon
          </span>
        )}
      </div>
      {hospital.purpose && (
        <p className="mt-1 line-clamp-3 text-sm text-muted-foreground">{hospital.purpose}</p>
      )}
      {hospital.serviceLines && hospital.serviceLines.length > 0 && (
        <p className="mt-2 text-xs text-muted-foreground">
          {hospital.serviceLines.slice(0, 4).join(" · ")}
        </p>
      )}
      <div className="mt-auto pt-3">
        {requestable ? (
          <Link
            href={`/citizen/virtual-care/request?vh=${encodeURIComponent(hospital.id)}`}
            className="inline-flex items-center rounded-lg bg-teal-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-teal-700"
          >
            Request a teleconsult
          </Link>
        ) : (
          <p className="text-xs text-muted-foreground">
            Not yet taking requests — this service opens once its care team is connected.
          </p>
        )}
      </div>
    </li>
  );
}

export default function DiscoverVirtualCarePage() {
  const directory = useVirtualHospitals("citizen");
  const hospitals = directory.data?.data?.virtualHospitals ?? [];
  const requestable = hospitals.filter((h) => h.availability === "REQUESTABLE");
  const comingSoon = hospitals.filter((h) => h.availability !== "REQUESTABLE");

  return (
    <AppLayout>
      <PageShell
        title="Virtual Care"
        subtitle="Request a teleconsult from Zimbabwe's virtual hospitals — no travel needed"
        icon={<MonitorSmartphone className="h-6 w-6" />}
      >
        <div className="space-y-6">
          <div className="flex items-center justify-between gap-3">
            <p className="text-sm text-muted-foreground">
              A provider from the virtual hospital&apos;s care pool accepts your request and
              consults with you by video, audio or secure message.
            </p>
            <Link
              href="/citizen/virtual-care"
              className="shrink-0 text-xs font-medium text-teal-700 hover:underline"
            >
              My virtual care requests
            </Link>
          </div>

          {directory.isLoading && (
            <p className="inline-flex items-center gap-2 text-sm text-muted-foreground">
              <Loader2 className="h-4 w-4 animate-spin" aria-hidden /> Loading virtual hospitals…
            </p>
          )}

          {directory.isError && !directory.isLoading && (
            <div className="rounded-lg border border-destructive/40 bg-destructive/5 p-4 text-sm">
              <p className="font-medium text-foreground">We couldn&apos;t load the virtual care directory.</p>
              <button
                type="button"
                onClick={() => directory.refetch()}
                className="mt-2 rounded-md border border-border px-3 py-1.5 text-xs font-medium hover:border-primary/30"
              >
                Try again
              </button>
            </div>
          )}

          {!directory.isLoading && !directory.isError && (
            <>
              <section>
                <h2 className="mb-3 text-sm font-semibold text-foreground">
                  Taking requests now
                </h2>
                {requestable.length === 0 ? (
                  <p className="rounded-lg border border-dashed border-border p-4 text-sm text-muted-foreground">
                    No virtual hospitals are taking requests right now.
                  </p>
                ) : (
                  <ul className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
                    {requestable.map((h) => (
                      <HospitalCard key={h.id} hospital={h} />
                    ))}
                  </ul>
                )}
              </section>

              {comingSoon.length > 0 && (
                <section>
                  <h2 className="mb-3 text-sm font-semibold text-foreground">Coming soon</h2>
                  <ul className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
                    {comingSoon.map((h) => (
                      <HospitalCard key={h.id} hospital={h} />
                    ))}
                  </ul>
                </section>
              )}
            </>
          )}
        </div>
      </PageShell>
    </AppLayout>
  );
}
