"use client";

/**
 * Workspace Context Switcher — Health OS Experience Doctrine
 *
 * The unified zone switcher for the three execution contexts:
 * - WORK — regulated clinical and operational execution
 * - MY PROFESSIONAL — licensed practice management
 * - MY LIFE — personal health and wellbeing
 *
 * "One coherent experience shell, one person anchor, many roles,
 *  many contexts, many experience modes, one governed runtime."
 *
 * The switcher adapts visibility, enabled features, friction levels,
 * and navigation based on the active zone and the user's role.
 */

import { useState, useCallback, useMemo } from "react";
import { useRouter, usePathname } from "next/navigation";
import {
  Stethoscope, Award, Heart, ChevronDown, Check, Shield,
  Building2, Users, Lock, Unlock,
} from "lucide-react";
import { useAuthStore } from "@/hooks/useAuthStore";
import { useFacilityStore } from "@/hooks/useFacilityStore";
import { useShiftStore } from "@/hooks/useShiftStore";
import { useRoleGroup } from "@/hooks/useRoleGroup";

// ── Zone Definitions ─────────────────────────────────────────────────

interface Zone {
  id: "WORK" | "MY_PROFESSIONAL" | "MY_LIFE";
  label: string;
  description: string;
  icon: typeof Stethoscope;
  gradient: string;
  requiredRole?: string;
  entryPath: string;
  frictionLevel: "MINIMAL" | "STANDARD" | "ELEVATED" | "MAXIMUM";
}

const ZONES: Zone[] = [
  {
    id: "WORK",
    label: "Work",
    description: "Clinical care, facility operations, regulated work",
    icon: Stethoscope,
    gradient: "from-emerald-500 to-green-600",
    requiredRole: "PROVIDER",
    entryPath: "/queue",
    frictionLevel: "MAXIMUM",
  },
  {
    id: "MY_PROFESSIONAL",
    label: "My Professional",
    description: "Licensure, CPD, privileges, scheduling",
    icon: Award,
    gradient: "from-blue-500 to-indigo-600",
    requiredRole: "PROVIDER",
    entryPath: "/provider/activate",
    frictionLevel: "ELEVATED",
  },
  {
    id: "MY_LIFE",
    label: "My Life",
    description: "Personal health, wellness, wallet, marketplace",
    icon: Heart,
    gradient: "from-rose-500 to-pink-600",
    entryPath: "/citizen",
    frictionLevel: "MINIMAL",
  },
];

// ── Zone-to-Route Mapping ────────────────────────────────────────────

function detectCurrentZone(pathname: string): Zone["id"] {
  // WORK zone paths
  if (/^\/(queue|ehr|shift|beds|pharmacy|lab|scheduling|telemedicine|operations)/.test(pathname)) {
    return "WORK";
  }
  // MY PROFESSIONAL zone paths
  if (/^\/(provider|id-services|registry|admin|reports|finance|coverage\/contracts)/.test(pathname)) {
    return "MY_PROFESSIONAL";
  }
  // MY LIFE zone paths
  if (/^\/(citizen|wellness|wallet|marketplace|community|discover|caregiving|share|verify)/.test(pathname)) {
    return "MY_LIFE";
  }
  // Default based on path context
  if (/^\/(ai-governance|clinical-tools|inventory|public-health|monitoring)/.test(pathname)) {
    return "MY_PROFESSIONAL";
  }
  return "WORK"; // Default to work context
}

// ── Component ────────────────────────────────────────────────────────

export function WorkspaceContextSwitcher() {
  const [isOpen, setIsOpen] = useState(false);
  const router = useRouter();
  const pathname = usePathname();
  const auth = useAuthStore();
  const roleGroup = useRoleGroup();
  const facility = useFacilityStore();
  const shift = useShiftStore();

  const currentZoneId = useMemo(() => detectCurrentZone(pathname), [pathname]);
  const currentZone = useMemo(() => ZONES.find(z => z.id === currentZoneId) ?? ZONES[0], [currentZoneId]);

  const hasActiveProvider = useMemo(() => auth.hasActiveProvider?.() ?? false, [auth]);

  const availableZones = useMemo(() => {
    return ZONES.filter(zone => {
      // MY LIFE is always available
      if (zone.id === "MY_LIFE") return true;
      // WORK and MY PROFESSIONAL require provider role
      if (zone.requiredRole === "PROVIDER") {
        return hasActiveProvider || roleGroup.isAdmin;
      }
      return true;
    });
  }, [hasActiveProvider, roleGroup.isAdmin]);

  const switchZone = useCallback((zone: Zone) => {
    setIsOpen(false);

    // WORK zone requires facility + shift context
    if (zone.id === "WORK" && !shift.shift?.id) {
      router.push("/facility"); // Redirect to facility selection first
      return;
    }

    router.push(zone.entryPath);
  }, [router, shift.shift?.id]);

  const ZoneIcon = currentZone.icon;

  return (
    <div className="relative">
      {/* Current zone indicator */}
      <button
        onClick={() => setIsOpen(!isOpen)}
        className={`flex items-center gap-2 px-3 py-2 rounded-xl bg-gradient-to-r ${currentZone.gradient} text-white shadow-md hover:shadow-lg transition-all`}
      >
        <ZoneIcon className="h-4 w-4" />
        <span className="text-sm font-semibold">{currentZone.label}</span>
        <ChevronDown className={`h-3.5 w-3.5 transition-transform ${isOpen ? "rotate-180" : ""}`} />
      </button>

      {/* Context info bar */}
      {currentZone.id === "WORK" && facility.facility?.name && (
        <div className="absolute top-full left-0 mt-1 flex items-center gap-1.5 text-[10px] text-gray-500 whitespace-nowrap">
          <Building2 className="h-3 w-3" />
          <span>{facility.facility.name}</span>
          {shift.shift?.id && (
            <>
              <span>·</span>
              <Shield className="h-3 w-3" />
              <span>On shift</span>
            </>
          )}
        </div>
      )}

      {/* Zone switcher dropdown */}
      {isOpen && (
        <>
          <div className="fixed inset-0 z-30" onClick={() => setIsOpen(false)} />
          <div className="absolute top-full left-0 mt-2 w-80 bg-white rounded-xl shadow-2xl border border-gray-200 z-40 overflow-hidden">
            <div className="p-3 bg-gray-50 border-b">
              <p className="text-xs font-medium text-gray-500 uppercase tracking-wider">Switch Context</p>
              <p className="text-[10px] text-gray-400 mt-0.5">
                One person, many roles — switch between your execution contexts
              </p>
            </div>

            <div className="p-2 space-y-1">
              {availableZones.map(zone => {
                const Icon = zone.icon;
                const isActive = zone.id === currentZoneId;
                const needsShift = zone.id === "WORK" && !shift.shift?.id;

                return (
                  <button
                    key={zone.id}
                    onClick={() => switchZone(zone)}
                    className={`w-full flex items-start gap-3 p-3 rounded-lg transition-all ${
                      isActive
                        ? "bg-gradient-to-r " + zone.gradient + " text-white shadow-md"
                        : "hover:bg-gray-50 text-gray-700"
                    }`}
                  >
                    <div className={`flex-shrink-0 p-2 rounded-lg ${
                      isActive ? "bg-white/20" : "bg-gray-100"
                    }`}>
                      <Icon className={`h-5 w-5 ${isActive ? "text-white" : "text-gray-600"}`} />
                    </div>
                    <div className="flex-1 text-left">
                      <div className="flex items-center gap-2">
                        <span className={`text-sm font-semibold ${isActive ? "text-white" : ""}`}>
                          {zone.label}
                        </span>
                        {isActive && <Check className="h-3.5 w-3.5" />}
                        {needsShift && (
                          <span className="text-[10px] bg-amber-100 text-amber-700 px-1.5 py-0.5 rounded-full">
                            Needs shift
                          </span>
                        )}
                      </div>
                      <p className={`text-xs mt-0.5 ${isActive ? "text-white/80" : "text-gray-500"}`}>
                        {zone.description}
                      </p>
                      <div className={`flex items-center gap-1 mt-1 text-[10px] ${
                        isActive ? "text-white/60" : "text-gray-400"
                      }`}>
                        {zone.frictionLevel === "MINIMAL" ? (
                          <><Unlock className="h-3 w-3" /> Low friction</>
                        ) : zone.frictionLevel === "MAXIMUM" ? (
                          <><Lock className="h-3 w-3" /> Maximum governance</>
                        ) : (
                          <><Shield className="h-3 w-3" /> {zone.frictionLevel.toLowerCase()} friction</>
                        )}
                      </div>
                    </div>
                  </button>
                );
              })}
            </div>

            {/* Quick info */}
            <div className="p-3 bg-gray-50 border-t">
              <div className="flex items-center gap-2 text-[10px] text-gray-400">
                <Users className="h-3 w-3" />
                <span>
                  Signed in as {auth.user?.displayName ?? "User"} ·{" "}
                  {hasActiveProvider ? "Provider" : "Citizen"}
                </span>
              </div>
            </div>
          </div>
        </>
      )}
    </div>
  );
}
