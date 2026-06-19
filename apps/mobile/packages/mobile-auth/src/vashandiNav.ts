/**
 * Mobile Vashandi workforce navigation — filtered by Session Experience Contract workspaces.
 */

export type VashandiMobileScreen = "roster" | "attendance" | "availability" | "facility_staff";

export interface MobileVashandiNavItem {
  id: string;
  label: string;
  screen: VashandiMobileScreen;
  webHref: string;
  requiredWorkspaces: string[];
}

export const MOBILE_VASHANDI_NAV_ITEMS: MobileVashandiNavItem[] = [
  {
    id: "my_roster",
    label: "My Roster",
    screen: "roster",
    webHref: "/work/vashandi/rosters",
    requiredWorkspaces: ["vashandi.my_roster", "vashandi.rosters"],
  },
  {
    id: "my_attendance",
    label: "My Attendance",
    screen: "attendance",
    webHref: "/work/vashandi/attendance",
    requiredWorkspaces: ["vashandi.my_attendance"],
  },
  {
    id: "my_availability",
    label: "My Availability",
    screen: "availability",
    webHref: "/work/vashandi/leave-availability",
    requiredWorkspaces: ["vashandi.leave_availability"],
  },
  {
    id: "facility_staff",
    label: "Facility Staff",
    screen: "facility_staff",
    webHref: "/work/vashandi/workforce",
    requiredWorkspaces: ["vashandi.facility_staff"],
  },
];

function workspaceAllowed(visibleVashandiWorkspaces: string[] | undefined, required: string[]): boolean {
  const visible = new Set(visibleVashandiWorkspaces ?? []);
  return required.some((workspace) => visible.has(workspace));
}

export function resolveMobileVashandiNav(input: {
  visibleVashandiWorkspaces?: string[];
  workTabVisible?: boolean;
}): MobileVashandiNavItem[] {
  if (input.workTabVisible === false) return [];
  return MOBILE_VASHANDI_NAV_ITEMS.filter((item) =>
    workspaceAllowed(input.visibleVashandiWorkspaces, item.requiredWorkspaces),
  );
}

export function hasMobileVashandiEntry(input: {
  visibleVashandiWorkspaces?: string[];
  workTabVisible?: boolean;
}): boolean {
  return resolveMobileVashandiNav(input).length > 0;
}
