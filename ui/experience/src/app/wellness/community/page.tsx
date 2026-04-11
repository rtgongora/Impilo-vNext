"use client";

/**
 * Wellness Community — Health OS §5, §6
 * Connect with others on wellness journeys: forums, groups, shared goals.
 * Route: /wellness/community | Zone: wellness | Guard: auth
 */

import { Users2, Search, MessageSquare, TrendingUp, Heart } from "lucide-react";
import { AppLayout } from "@/components/AppLayout";
import { PageShell } from "@/components/PageShell";

const COMMUNITY_SECTIONS = [
  { label: "Discussion Forums", description: "Share experiences, ask questions, and connect with peers", Icon: MessageSquare, color: "bg-purple-50 text-purple-600" },
  { label: "Success Stories", description: "Read inspiring health and wellness transformation stories", Icon: TrendingUp, color: "bg-green-50 text-green-600" },
  { label: "Wellness Groups", description: "Join groups based on health interests and goals", Icon: Users2, color: "bg-blue-50 text-blue-600" },
  { label: "Peer Support", description: "Connect with others managing similar health conditions", Icon: Heart, color: "bg-pink-50 text-pink-600" },
];

export default function WellnessCommunityPage() {
  return (
    <AppLayout>
      <PageShell
        title="Wellness Community"
        subtitle="Connect with others on wellness journeys, share experiences, and find support"
        icon={<Users2 className="h-6 w-6" />}
      >
        <div className="space-y-6">
          {/* Search */}
          <div className="relative w-full md:w-96">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-gray-400" />
            <input
              type="text"
              placeholder="Search community discussions..."
              className="w-full rounded-lg border border-gray-300 py-2.5 pl-10 pr-4 text-sm focus:border-purple-500 focus:outline-none focus:ring-1 focus:ring-purple-500"
            />
          </div>

          {/* Community sections */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {COMMUNITY_SECTIONS.map(({ label, description, Icon, color }) => (
              <div
                key={label}
                className="rounded-xl border border-gray-200 bg-white p-5 hover:border-purple-400 hover:shadow-md transition-all cursor-pointer group"
              >
                <div className="flex items-center gap-3 mb-2">
                  <div className={`rounded-lg p-2 ${color.split(" ")[0]}`}>
                    <Icon className={`h-5 w-5 ${color.split(" ")[1]}`} />
                  </div>
                  <h3 className="font-semibold text-gray-900 group-hover:text-purple-600 transition-colors">{label}</h3>
                </div>
                <p className="text-sm text-gray-600">{description}</p>
              </div>
            ))}
          </div>
        </div>
      </PageShell>
    </AppLayout>
  );
}
