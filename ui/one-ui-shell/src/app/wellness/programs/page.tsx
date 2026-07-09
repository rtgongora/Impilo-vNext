"use client";

/**
 * Prevention Programs — Health OS §5, §6
 * Enroll in wellness and prevention programmes.
 * Route: /wellness/programs | Zone: wellness | Guard: auth
 */

import { BookOpen, Search, Filter } from "lucide-react";
import { useMemo, useState } from "react";
import { GlassSurface, LuminousStage } from "shared-ui";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useJoinWellnessChallenge, useWellnessChallenges } from "@/hooks/queries/useCitizenWellness";

export default function WellnessProgramsPage() {
  const cpid = useAuthStore((s) => s.user?.id);
  const [search, setSearch] = useState("");
  const challengesQ = useWellnessChallenges();
  const joinChallenge = useJoinWellnessChallenge(cpid);

  const programs = useMemo(() => {
    const rows = challengesQ.data ?? [];
    return rows.filter((r) =>
      `${r.title} ${r.description ?? ""}`.toLowerCase().includes(search.toLowerCase()),
    );
  }, [challengesQ.data, search]);

  return (
    <AppLayout>
      <PageShell
        title="Prevention Programs"
        subtitle="Enroll in wellness and prevention programmes designed for your health needs"
        icon={<BookOpen className="h-6 w-6" />}
      >
        <LuminousStage className="space-y-6 p-5 sm:p-6">
        <div className="space-y-6">
          {/* Search and filter */}
          <div className="flex gap-3">
            <div className="relative flex-1">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
              <input
                type="text"
                placeholder="Search programmes..."
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                className="w-full rounded-lg border border-border py-2.5 pl-10 pr-4 text-sm focus:border-emerald-500 focus:outline-none focus:ring-1 focus:ring-emerald-500"
              />
            </div>
            <button className="inline-flex items-center gap-2 rounded-lg border border-border px-4 py-2 text-sm text-foreground hover:bg-background transition-colors">
              <Filter className="h-4 w-4" />
              Filters
            </button>
          </div>

          {/* My enrollments - empty state */}
          <div>
            <h3 className="text-sm font-semibold text-foreground mb-3">My Enrollments</h3>
            <div className="rounded-lg border border-dashed border-border bg-background p-6 text-center">
              <p className="text-sm text-muted-foreground">Programme enrollment is backed by challenge participation in this release.</p>
            </div>
          </div>

          {/* Available programs */}
          <div>
            <h3 className="text-sm font-semibold text-foreground mb-3">Available Programmes</h3>
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
              {programs.map((program) => (
                <GlassSurface
                  key={program.id}
                  className="p-5 transition-all hover:shadow-glow-teal cursor-pointer group"
                >
                  <div className="flex items-center justify-between mb-2">
                    <h4 className="font-semibold text-foreground group-hover:text-primary transition-colors">{program.title}</h4>
                    <span className={`rounded px-2 py-0.5 text-xs font-medium ${program.status === "ACTIVE" ? "bg-green-50 text-green-700" : "bg-neutral-100 text-muted-foreground"}`}>
                      {program.status === "ACTIVE" ? "Open" : program.status}
                    </span>
                  </div>
                  <p className="text-sm text-muted-foreground mb-2">{program.description ?? "Wellness programme"}</p>
                  <p className="text-xs text-muted-foreground">Target: {program.targetValue} {program.targetUnit}</p>
                  <button
                    onClick={() => void joinChallenge.mutateAsync({ challengeId: program.id })}
                    disabled={!cpid}
                    className="mt-3 rounded-lg bg-emerald-600 px-3 py-1.5 text-xs font-medium text-white disabled:opacity-50"
                  >
                    Enroll
                  </button>
                </GlassSurface>
              ))}
            </div>
          </div>
        </div>
        </LuminousStage>
      </PageShell>
    </AppLayout>
  );
}
