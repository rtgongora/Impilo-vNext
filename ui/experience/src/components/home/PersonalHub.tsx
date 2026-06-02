"use client";

import { useState, type ComponentType } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import {
  Activity,
  AlertTriangle,
  Bluetooth,
  Building2,
  Calendar,
  Clock,
  Heart,
  Megaphone,
  MessageSquare,
  Pill,
  QrCode,
  ScanLine,
  Shield,
  ShoppingCart,
  Store,
  Trophy,
  Users,
  Video,
  Wallet,
} from "lucide-react";
import type { AuthUser } from "@/hooks/useAuthStore";
import { showBackendPendingToast } from "@/hooks/useBackendPendingToast";
import {
  PendingApiRoute,
  PersonalHealthTab,
  PersonalSocialTab,
} from "@/lib/experience/pending-api-routes";

type PersonalSection = "health" | "social";

interface CommunityGroup {
  id: string;
  attributes: Record<string, unknown>;
}

export interface PersonalHubProps {
  user: AuthUser | null;
  communityGroups: CommunityGroup[];
  onJoinGroup: (groupId: string) => void;
  joinPending?: boolean;
}

const HEALTH_TABS: { id: PersonalHealthTab; label: string; icon?: ComponentType<{ className?: string }> }[] = [
  { id: PersonalHealthTab.Home, label: "Home" },
  { id: PersonalHealthTab.HealthId, label: "Health ID", icon: QrCode },
  { id: PersonalHealthTab.Appointments, label: "Appointments", icon: Calendar },
  { id: PersonalHealthTab.Records, label: "Timeline", icon: Clock },
  { id: PersonalHealthTab.Medications, label: "Medications", icon: Pill },
  { id: PersonalHealthTab.Wallet, label: "Wallet", icon: Wallet },
  { id: PersonalHealthTab.Privacy, label: "Privacy", icon: Shield },
  { id: PersonalHealthTab.Monitoring, label: "Monitoring", icon: Bluetooth },
  { id: PersonalHealthTab.Messages, label: "Messages", icon: MessageSquare },
  { id: PersonalHealthTab.Marketplace, label: "Marketplace", icon: ShoppingCart },
  { id: PersonalHealthTab.Wellness, label: "Wellness", icon: Activity },
  { id: PersonalHealthTab.Services, label: "Services" },
  { id: PersonalHealthTab.Queue, label: "Queue" },
  { id: PersonalHealthTab.Emergency, label: "SOS", icon: AlertTriangle },
];

const SOCIAL_NAV: { id: PersonalSocialTab; label: string; description: string; icon: ComponentType<{ className?: string }> }[] = [
  { id: PersonalSocialTab.Timeline, label: "Timeline", description: "Your health feed", icon: MessageSquare },
  { id: PersonalSocialTab.Communities, label: "Communities", description: "Support groups", icon: Users },
  { id: PersonalSocialTab.Clubs, label: "Clubs", description: "Wellness & fitness", icon: Trophy },
  { id: PersonalSocialTab.Pages, label: "Pages", description: "Professionals", icon: Building2 },
  { id: PersonalSocialTab.Crowdfunding, label: "Fundraising", description: "Support causes", icon: Megaphone },
];

function healthTabAction(tab: PersonalHealthTab): { href?: string; pending?: PendingApiRoute; label?: string } {
  switch (tab) {
    case PersonalHealthTab.Home:
      return {};
    case PersonalHealthTab.HealthId:
      return { href: "/citizen/health-id/qr" };
    case PersonalHealthTab.Appointments:
      return { href: "/scheduling" };
    case PersonalHealthTab.Medications:
      return { href: "/home/medications" };
    case PersonalHealthTab.Marketplace:
      return { href: "/marketplace" };
    case PersonalHealthTab.Wellness:
      return { href: "/wellness" };
    case PersonalHealthTab.Messages:
      return { href: "/home/notifications" };
    case PersonalHealthTab.Records:
      return { pending: PendingApiRoute.PortalHealthTimeline, label: "Health timeline" };
    case PersonalHealthTab.Wallet:
      return { pending: PendingApiRoute.PortalWallet, label: "Health wallet" };
    case PersonalHealthTab.Privacy:
      return { pending: PendingApiRoute.PortalConsentDashboard, label: "Consent dashboard" };
    case PersonalHealthTab.Monitoring:
      return { pending: PendingApiRoute.PortalRemoteMonitoring, label: "Remote monitoring" };
    case PersonalHealthTab.Services:
      return { pending: PendingApiRoute.PortalServiceDiscovery, label: "Service discovery" };
    case PersonalHealthTab.Queue:
      return { pending: PendingApiRoute.PortalQueueStatus, label: "Queue status" };
    case PersonalHealthTab.Emergency:
      return { pending: PendingApiRoute.PortalEmergencySos, label: "Emergency SOS" };
    default:
      return {};
  }
}

export function PersonalHub({ user, communityGroups, onJoinGroup, joinPending }: PersonalHubProps) {
  const router = useRouter();
  const [activeSection, setActiveSection] = useState<PersonalSection>("health");
  const [activeHealthTab, setActiveHealthTab] = useState<PersonalHealthTab>(PersonalHealthTab.Home);
  const [activeSocialTab, setActiveSocialTab] = useState<PersonalSocialTab>(PersonalSocialTab.Timeline);

  const displayName = user?.displayName ?? "Member";
  const initials = displayName
    .split(" ")
    .map((n) => n[0])
    .join("")
    .slice(0, 2)
    .toUpperCase();

  const walletBalance = 0;
  const unreadMessages = 0;
  const upcomingAppointments = 0;
  const pendingRefills = 0;

  const openHealthTab = (tab: PersonalHealthTab) => {
    const action = healthTabAction(tab);
    if (action.pending) {
      showBackendPendingToast(action.pending, action.label);
      setActiveHealthTab(tab);
      return;
    }
    if (action.href) {
      router.push(action.href);
      return;
    }
    setActiveHealthTab(tab);
  };

  const renderHealthPanel = () => {
    if (activeHealthTab === PersonalHealthTab.Home) {
      return (
        <div className="space-y-4">
          <div className="grid md:grid-cols-3 gap-4">
            <div className="md:col-span-2 rounded-lg border bg-white p-4">
              <div className="flex items-center gap-4 mb-4">
                <div className="h-14 w-14 rounded-full bg-impilo-100 flex items-center justify-center text-lg font-semibold text-impilo-700">
                  {initials}
                </div>
                <div>
                  <h2 className="text-xl font-bold text-gray-900">{displayName}</h2>
                  <p className="text-sm text-gray-500">Personal health account</p>
                </div>
              </div>
              <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
                {[
                  { id: "appointment", label: "Book visit", icon: Calendar, tab: PersonalHealthTab.Appointments },
                  { id: "telehealth", label: "Video call", icon: Video, href: "/telemedicine" },
                  { id: "rx", label: "Refill Rx", icon: Pill, tab: PersonalHealthTab.Medications },
                  { id: "msg", label: "Messages", icon: MessageSquare, tab: PersonalHealthTab.Messages },
                ].map((action) => (
                  <button
                    key={action.id}
                    type="button"
                    onClick={() => {
                      if (action.href) {
                        router.push(action.href);
                        return;
                      }
                      if (action.tab) openHealthTab(action.tab);
                    }}
                    className="h-16 flex flex-col items-center justify-center gap-1 rounded-lg border hover:bg-gray-50 text-xs font-medium"
                  >
                    <action.icon className="h-4 w-4 text-impilo-600" />
                    {action.label}
                  </button>
                ))}
              </div>
            </div>
            <div className="rounded-lg border bg-gradient-to-br from-impilo-50 to-white p-4">
              <Wallet className="h-5 w-5 text-impilo-600 mb-2" />
              <p className="text-2xl font-bold">${walletBalance.toFixed(2)}</p>
              <p className="text-xs text-gray-500 mb-3">Health wallet (demo)</p>
              <button
                type="button"
                onClick={() => showBackendPendingToast(PendingApiRoute.PortalWalletTopUp, "Add funds")}
                className="w-full text-sm py-2 rounded-lg bg-impilo-600 text-white hover:bg-impilo-700"
              >
                Add funds
              </button>
            </div>
          </div>
          <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
            {[
              { label: "Upcoming visits", value: upcomingAppointments, tab: PersonalHealthTab.Appointments },
              { label: "Pending refills", value: pendingRefills, tab: PersonalHealthTab.Medications },
              { label: "Unread messages", value: unreadMessages, tab: PersonalHealthTab.Messages },
              { label: "Communities", value: communityGroups.length, section: "social" as const },
            ].map((card) => (
              <button
                key={card.label}
                type="button"
                onClick={() => {
                  if (card.section === "social") {
                    setActiveSection("social");
                    return;
                  }
                  if (card.tab) openHealthTab(card.tab);
                }}
                className="rounded-lg border bg-white p-4 text-center hover:shadow-md transition-shadow"
              >
                <p className="text-2xl font-bold text-gray-900">{card.value}</p>
                <p className="text-xs text-gray-500">{card.label}</p>
              </button>
            ))}
          </div>
        </div>
      );
    }

    const action = healthTabAction(activeHealthTab);
    if (action.href) {
      return (
        <p className="text-sm text-gray-600">
          Opening Experience route:{" "}
          <Link href={action.href} className="text-impilo-600 font-medium">
            {action.href}
          </Link>
        </p>
      );
    }

    return (
      <div className="rounded-lg border border-dashed border-amber-200 bg-amber-50/50 p-8 text-center">
        <p className="text-sm font-medium text-amber-950">Portal module preview</p>
        <p className="text-xs text-amber-800 mt-2 max-w-md mx-auto">
          This tab matches the Lovable Personal Hub. Connect the BFF route below when ready; registry and client
          flows stay on existing Experience paths.
        </p>
        <button
          type="button"
          onClick={() => action.pending && showBackendPendingToast(action.pending, action.label)}
          className="mt-4 text-sm font-medium text-impilo-600 underline"
        >
          Trigger backend pending toast
        </button>
      </div>
    );
  };

  const renderSocialPanel = () => {
    const current = SOCIAL_NAV.find((s) => s.id === activeSocialTab);
    const pendingMap: Record<PersonalSocialTab, PendingApiRoute> = {
      [PersonalSocialTab.Timeline]: PendingApiRoute.SocialTimelineFeed,
      [PersonalSocialTab.Communities]: PendingApiRoute.SocialCommunities,
      [PersonalSocialTab.Clubs]: PendingApiRoute.SocialClubs,
      [PersonalSocialTab.Pages]: PendingApiRoute.SocialProfessionalPages,
      [PersonalSocialTab.Crowdfunding]: PendingApiRoute.SocialCrowdfunding,
    };

    return (
      <div className="space-y-4">
        <div className="flex items-center gap-3 mb-2">
          {current && (
            <>
              <div className="h-10 w-10 rounded-xl bg-purple-100 flex items-center justify-center">
                <current.icon className="h-5 w-5 text-purple-600" />
              </div>
              <div>
                <h3 className="text-lg font-semibold">{current.label}</h3>
                <p className="text-sm text-gray-500">{current.description}</p>
              </div>
            </>
          )}
        </div>
        <button
          type="button"
          onClick={() => showBackendPendingToast(pendingMap[activeSocialTab], current?.label)}
          className="w-full rounded-lg border bg-white p-6 text-left hover:border-purple-200"
        >
          <p className="text-sm text-gray-600">Load {current?.label} feed (BFF pending)</p>
          <p className="text-xs font-mono text-gray-400 mt-1">{pendingMap[activeSocialTab]}</p>
        </button>
        {activeSocialTab === PersonalSocialTab.Communities && communityGroups.length > 0 && (
          <div className="rounded-lg border bg-white p-4">
            <h4 className="text-sm font-semibold mb-3">Experience communities (live)</h4>
            <div className="space-y-2">
              {communityGroups.slice(0, 6).map((g) => (
                <div key={g.id} className="flex items-center justify-between p-2 rounded-lg bg-gray-50">
                  <div>
                    <p className="text-sm font-medium">{String(g.attributes.name ?? g.id)}</p>
                    <p className="text-xs text-gray-500">
                      {String(g.attributes.groupType ?? "group")} · {String(g.attributes.memberCount ?? 0)} members
                    </p>
                  </div>
                  <button
                    type="button"
                    disabled={joinPending}
                    onClick={() => onJoinGroup(g.id)}
                    className="text-xs text-impilo-600 font-medium"
                  >
                    Join
                  </button>
                </div>
              ))}
            </div>
          </div>
        )}
        <button
          type="button"
          onClick={() => showBackendPendingToast(PendingApiRoute.SocialNewsWidget, "News widget")}
          className="text-sm text-gray-500"
        >
          News sidebar (BFF pending)
        </button>
      </div>
    );
  };

  return (
    <div className="space-y-8">
      <div className="flex flex-wrap items-center gap-2">
        <button
          type="button"
          onClick={() => setActiveSection("health")}
          className={`px-4 py-2 text-sm font-medium rounded-lg ${
            activeSection === "health" ? "bg-pink-500 text-white" : "border bg-white text-gray-700"
          }`}
        >
          <Heart className="h-4 w-4 inline mr-1.5" />
          My Health
        </button>
        <button
          type="button"
          onClick={() => setActiveSection("social")}
          className={`px-4 py-2 text-sm font-medium rounded-lg ${
            activeSection === "social" ? "bg-purple-500 text-white" : "border bg-white text-gray-700"
          }`}
        >
          <Users className="h-4 w-4 inline mr-1.5" />
          Social Hub
        </button>
        <div className="flex-1" />
        <button
          type="button"
          onClick={() => showBackendPendingToast(PendingApiRoute.PortalDocumentScan, "Document scanner")}
          className="inline-flex items-center gap-2 px-3 py-2 text-sm border rounded-lg hover:bg-gray-50"
        >
          <ScanLine className="h-4 w-4" />
          Scan document
        </button>
      </div>

      {activeSection === "health" && (
        <div className="space-y-6">
          <div className="overflow-x-auto mb-2 pb-2">
            <div className="inline-flex gap-1 min-w-full">
              {HEALTH_TABS.map((tab) => (
                <button
                  key={tab.id}
                  type="button"
                  onClick={() => openHealthTab(tab.id)}
                  className={`shrink-0 px-3 py-2 text-xs sm:text-sm rounded-lg border whitespace-nowrap ${
                    activeHealthTab === tab.id
                      ? "bg-impilo-600 text-white border-impilo-600"
                      : "bg-white text-gray-600"
                  } ${tab.id === PersonalHealthTab.Emergency ? "text-red-600 border-red-200" : ""}`}
                >
                  {tab.icon && <tab.icon className="h-3.5 w-3.5 inline mr-1" />}
                  {tab.label}
                </button>
              ))}
            </div>
          </div>
          {renderHealthPanel()}
        </div>
      )}

      {activeSection === "social" && (
        <div className="flex flex-col lg:flex-row gap-6">
          <div className="hidden lg:block w-56 shrink-0 lg:sticky lg:top-6 lg:max-h-[calc(100vh-6rem)] lg:overflow-y-auto">
            <nav className="rounded-xl border bg-white p-3 space-y-1.5">
              {SOCIAL_NAV.map((item) => (
                <button
                  key={item.id}
                  type="button"
                  onClick={() => setActiveSocialTab(item.id)}
                  className={`w-full flex items-center gap-2 px-3 py-2 rounded-lg text-left text-sm ${
                    activeSocialTab === item.id ? "bg-purple-50 text-purple-700 font-medium" : "text-gray-600"
                  }`}
                >
                  <item.icon className="h-4 w-4" />
                  {item.label}
                </button>
              ))}
            </nav>
            <button
              type="button"
              onClick={() => {
                setActiveSection("health");
                openHealthTab(PersonalHealthTab.Marketplace);
              }}
              className="mt-3 w-full rounded-lg border border-green-200 bg-green-50 p-3 text-left text-sm hover:bg-green-100"
            >
              <Store className="h-4 w-4 inline mr-1 text-green-600" />
              Marketplace
            </button>
          </div>
          <div className="flex-1 min-w-0">
            <div className="lg:hidden flex gap-1 overflow-x-auto mb-3 pb-1">
              {SOCIAL_NAV.map((item) => (
                <button
                  key={item.id}
                  type="button"
                  onClick={() => setActiveSocialTab(item.id)}
                  className={`shrink-0 px-2 py-1 text-xs rounded-full border ${
                    activeSocialTab === item.id ? "bg-purple-500 text-white" : "bg-white"
                  }`}
                >
                  {item.label}
                </button>
              ))}
            </div>
            {renderSocialPanel()}
          </div>
        </div>
      )}

      <div className="rounded-lg border bg-gray-50 p-3 text-xs text-gray-500">
        Registry intake, client registry, and citizen Health ID flows use existing Experience routes — not replaced
        by this hub.
      </div>
    </div>
  );
}

