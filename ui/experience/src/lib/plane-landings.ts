/**
 * Navigational scaffolding for registry vs organization administrative planes.
 */

import type { OrganizationAdminSurface, RegistryAdminSubtype } from "@/lib/operational-context";

export interface RegistryPlaneCard {
  subtype: RegistryAdminSubtype;
  title: string;
  description: string;
  href: string;
}

/** Sovereign registry sub-planes — each href is a real in-app route with live hooks. */
export const REGISTRY_PLANE_CARDS: RegistryPlaneCard[] = [
  {
    subtype: "client_registry",
    title: "Client registry (MPI)",
    description: "Patient directory search via `/internal/v1/patients` for CPID and demographic governance.",
    href: "/registry/clients",
  },
  {
    subtype: "provider_registry",
    title: "Provider registry",
    description: "Practitioner directory via `/internal/v1/registry/providers` with live search.",
    href: "/registry/providers",
  },
  {
    subtype: "facility_registry",
    title: "Facility registry",
    description: "Facility master data via the facilities API used across the experience layer.",
    href: "/registry/facilities",
  },
  {
    subtype: "terminology_admin",
    title: "Terminology & code systems",
    description: "Terminology browser backed by registry terminology endpoints.",
    href: "/registry/terminology",
  },
  {
    subtype: "trust_admin",
    title: "Trust, federation & keys",
    description: "Operational trust hub linking federation pods, keys, and consent admin (live BFF routes).",
    href: "/registry/trust",
  },
];

export interface OrganizationPlaneCard {
  surface: OrganizationAdminSurface;
  title: string;
  description: string;
  href: string;
}

/** Organization / facility operational administration — each href is a live route (hubs decompose broad admin). */
export const ORGANIZATION_PLANE_CARDS: OrganizationPlaneCard[] = [
  {
    surface: "finance_revenue",
    title: "Finance & revenue",
    description: "Billing, claims, payments, and tariffs backed by live finance BFF routes.",
    href: "/finance",
  },
  {
    surface: "facility_admin",
    title: "Facility & ward administration",
    description: "Operational hub for beds, queue configuration, users, and audit — all existing admin routes.",
    href: "/organization-admin/facility",
  },
  {
    surface: "settings",
    title: "Organisations & assignments",
    description:
      "Workforce governance — organisations, facility/site links, jurisdictions, role definitions, and assignment scope (BFF → workforce-governance-service).",
    href: "/organization-admin/governance",
  },
  {
    surface: "service_point_admin",
    title: "Service points & devices",
    description: "Trusted device inventory and endpoint posture from `/internal/v1/admin/devices`.",
    href: "/admin/devices",
  },
  {
    surface: "staffing_roster",
    title: "Staffing & scheduling",
    description: "Roster, on-call, and appointment scheduling for the selected facility workspace (no clinical shift required).",
    href: "/organization-admin/staffing",
  },
  {
    surface: "approvals",
    title: "Policies & approvals",
    description: "Access policies and governance templates on the live policy admin route.",
    href: "/admin/policies",
  },
  {
    surface: "reports",
    title: "Operational reports",
    description: "Facility and enterprise reporting suites outside the patient chart.",
    href: "/reports",
  },
  {
    surface: "settings",
    title: "Organization settings",
    description: "Account, notifications, display, and integration preferences for operators.",
    href: "/settings",
  },
];
