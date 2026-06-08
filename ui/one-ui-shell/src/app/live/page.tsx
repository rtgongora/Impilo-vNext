"use client";

import Link from "next/link";
import {
  CalendarPlus,
  Clapperboard,
  GraduationCap,
  LayoutDashboard,
  Radio,
  Search,
  Settings2,
  Bookmark,
  Award,
} from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";
import { LiveEventList } from "@/components/live/LiveEventList";
import { useLiveEvents } from "@/hooks/queries/useLive";

const WORK_SECTIONS = [
  { href: "/live/manage", label: "Event Management", description: "Schedule, publish and moderate live sessions", Icon: Settings2 },
  { href: "/live/create", label: "Create Event", description: "Author a webinar, broadcast or training session", Icon: CalendarPlus },
  { href: "/live/cpd", label: "Live CPD", description: "Track CPD-eligible attendance from live events", Icon: GraduationCap },
  { href: "/live/certificates", label: "Certificates", description: "Issue and verify live event certificates", Icon: Award },
];

const LIFE_SECTIONS = [
  { href: "/live/discover", label: "Discover Talks", description: "Find citizen health talks and community broadcasts", Icon: Search },
  { href: "/live/my-events", label: "My Events", description: "Events you registered for", Icon: LayoutDashboard },
  { href: "/live/saved", label: "Saved Events", description: "Bookmarked sessions to watch later", Icon: Bookmark },
  { href: "/live/replays", label: "Replays", description: "Catch up on ended sessions", Icon: Clapperboard },
];

export default function LiveHubPage() {
  const { data: upcoming = [], isLoading } = useLiveEvents("SCHEDULED");
  const { data: liveNow = [] } = useLiveEvents("LIVE");

  return (
    <AppLayout>
      <PageShell
        title="Impilo Live"
        subtitle="Governed live events, webinars, broadcasts, CPD and citizen health talks"
        icon={<Radio className="h-6 w-6" />}
      >
        <div className="space-y-8">
          {(liveNow.length > 0 || upcoming.length > 0) && (
            <section>
              <div className="flex items-center justify-between mb-3">
                <h2 className="text-sm font-semibold uppercase tracking-wide text-gray-500">
                  {liveNow.length > 0 ? "Live now" : "Upcoming"}
                </h2>
                <Link href="/live/discover" className="text-xs text-violet-700 hover:underline">
                  View all
                </Link>
              </div>
              <LiveEventList
                events={liveNow.length > 0 ? liveNow : upcoming.slice(0, 4)}
                loading={isLoading}
                emptyMessage="No scheduled sessions yet."
              />
            </section>
          )}

          <section>
            <h2 className="text-sm font-semibold uppercase tracking-wide text-gray-500 mb-3">Work</h2>
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
              {WORK_SECTIONS.map(({ href, label, description, Icon }) => (
                <Link
                  key={href}
                  href={href}
                  className="rounded-2xl border border-gray-200 bg-white p-5 hover:border-violet-300 hover:shadow-sm transition-all"
                >
                  <div className="flex items-center gap-3 mb-2">
                    <div className="rounded-xl bg-violet-50 p-2">
                      <Icon className="h-5 w-5 text-violet-600" />
                    </div>
                    <h3 className="font-semibold text-gray-900">{label}</h3>
                  </div>
                  <p className="text-sm text-gray-600">{description}</p>
                </Link>
              ))}
            </div>
          </section>

          <section>
            <h2 className="text-sm font-semibold uppercase tracking-wide text-gray-500 mb-3">
              My Life — Live Health Talks
            </h2>
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
              {LIFE_SECTIONS.map(({ href, label, description, Icon }) => (
                <Link
                  key={href}
                  href={href}
                  className="rounded-2xl border border-gray-200 bg-white p-5 hover:border-violet-300 hover:shadow-sm transition-all"
                >
                  <div className="flex items-center gap-3 mb-2">
                    <div className="rounded-xl bg-violet-50 p-2">
                      <Icon className="h-5 w-5 text-violet-600" />
                    </div>
                    <h3 className="font-semibold text-gray-900">{label}</h3>
                  </div>
                  <p className="text-sm text-gray-600">{description}</p>
                </Link>
              ))}
            </div>
          </section>

          <div className="rounded-2xl border border-violet-200 bg-violet-50 p-4 text-sm text-violet-900">
            <strong className="block mb-1">About Impilo Live</strong>
            Impilo Live connects professional training, facility broadcasts, and citizen health
            talks under governed trust headers. Attendance, CPD evidence, and certificates are
            auditable; media rooms use the same RTC gateway as telemedicine.
          </div>
        </div>
      </PageShell>
    </AppLayout>
  );
}
