"use client";

import Link from "next/link";
import { Droplet, Heart, CalendarDays, History, MessageSquare, Settings, Loader2, AlertTriangle } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useDonorByPerson, useDonorNextEligibility } from "@/hooks/queries/useMadi";

const LINKS = [
  { href: "/madi/donor/register", label: "Register as donor", Icon: Droplet },
  { href: "/madi/donor/profile", label: "My profile", Icon: Heart },
  { href: "/madi/donor/drives", label: "Find a drive", Icon: CalendarDays },
  { href: "/madi/donor/history", label: "Donation history", Icon: History },
  { href: "/madi/donor/feedback", label: "Share feedback", Icon: MessageSquare },
  { href: "/madi/donor/preferences", label: "Communication preferences", Icon: Settings },
];

export default function DonorHubPage() {
  const user = useAuthStore((s) => s.user);
  const personCpid = user?.healthId || user?.id || "";
  const { data: donor, isPending, isError } = useDonorByPerson(personCpid || undefined);
  const donorId = donor?.donorId;
  const { data: eligibility } = useDonorNextEligibility(donorId);

  return (
    <AppLayout>
      <PageShell
        title="My Donor Hub"
        subtitle="Give blood safely — your voluntary contribution saves lives"
        icon={<Heart className="h-6 w-6" />}
      >
        {isPending && personCpid && (
          <div className="mb-4 flex items-center gap-2 text-sm text-gray-500">
            <Loader2 className="h-4 w-4 animate-spin" /> Checking donor registration…
          </div>
        )}

        {!isPending && !donor && !isError && (
          <div className="mb-6 rounded-2xl border border-dashed border-rose-300 bg-rose-50 p-6 text-center">
            <Droplet className="mx-auto h-10 w-10 text-rose-400" />
            <h3 className="mt-3 font-semibold text-gray-900">You are not registered as a donor yet</h3>
            <p className="mt-1 text-sm text-gray-600">
              Registration is voluntary. Your health information stays protected.
            </p>
            <Link
              href="/madi/donor/register"
              className="mt-4 inline-flex items-center gap-2 rounded-xl bg-rose-600 px-4 py-2 text-sm font-medium text-white hover:bg-rose-700"
            >
              Become a blood donor
            </Link>
          </div>
        )}

        {donor && (
          <div className="mb-6 rounded-2xl border border-rose-200 bg-white p-4">
            <p className="text-sm text-gray-600">
              Registered donor · blood group{" "}
              <span className="font-semibold text-gray-900">
                {donor.bloodGroup}
                {donor.rhFactor ? ` ${donor.rhFactor}` : ""}
              </span>
            </p>
            {eligibility && (
              <p className="mt-2 text-sm text-gray-700">
                {eligibility.eligible === false
                  ? `Not eligible to donate right now${eligibility.reason ? `: ${eligibility.reason}` : "."}`
                  : "You may be eligible to donate — confirm at your next drive."}
              </p>
            )}
          </div>
        )}

        {isError && (
          <div className="mb-4 flex items-center gap-2 rounded-xl border border-amber-200 bg-amber-50 p-3 text-sm text-amber-900">
            <AlertTriangle className="h-4 w-4 shrink-0" />
            Could not load donor profile. You can still register or browse drives.
          </div>
        )}

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
          {LINKS.map(({ href, label, Icon }) => (
            <Link
              key={href}
              href={href}
              className="flex items-center gap-3 rounded-xl border border-gray-200 bg-white p-4 hover:border-rose-300 transition-colors"
            >
              <Icon className="h-5 w-5 text-rose-500" />
              <span className="text-sm font-medium text-gray-900">{label}</span>
            </Link>
          ))}
        </div>
      </PageShell>
    </AppLayout>
  );
}
