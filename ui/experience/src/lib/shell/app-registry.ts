/**
 * Curated shell app catalog — routes and role gates match Experience `routes.ts` / sidebar.
 */

import { matchesRequiredRole } from "@/lib/auth/role-groups";
import type { AppDefinition, ShellCommand } from "./types";

export const SHELL_TASKBAR_HEIGHT_PX = 56;

export const SHELL_APPS: AppDefinition[] = [
  {
    id: "app-home",
    appCode: "home",
    name: "Home",
    description: "Role-aware dashboard and workplace hub",
    icon: "LayoutDashboard",
    category: "citizen",
    href: "/home",
    activeFlag: true,
    systemAppFlag: false,
    weight: 0,
  },
  {
    id: "app-clinical",
    appCode: "clinical",
    name: "Clinical Hub",
    description: "Clinical care modules",
    icon: "Stethoscope",
    category: "clinical",
    href: "/clinical",
    activeFlag: true,
    requiredRole: "CLINICAL",
    systemAppFlag: false,
    weight: 10,
  },
  {
    id: "app-queue",
    appCode: "queue",
    name: "Queue",
    description: "Queues, triage, and ward flow",
    icon: "Users",
    category: "clinical",
    href: "/queue",
    activeFlag: true,
    requiredRole: "QUEUE",
    systemAppFlag: false,
    weight: 20,
  },
  {
    id: "app-ehr-queue",
    appCode: "ehr_search",
    name: "Find patient",
    description: "Open a chart from queue search",
    icon: "Search",
    category: "clinical",
    href: "/queue/search",
    activeFlag: true,
    requiredRole: "QUEUE",
    systemAppFlag: false,
    weight: 25,
  },
  {
    id: "app-pharmacy",
    appCode: "pharmacy",
    name: "Pharmacy",
    description: "Prescriptions and dispensing",
    icon: "Pill",
    category: "clinical",
    href: "/pharmacy",
    activeFlag: true,
    requiredRole: "DISPENSER",
    systemAppFlag: false,
    weight: 30,
  },
  {
    id: "app-lab",
    appCode: "lab",
    name: "Laboratory",
    description: "LIMS / lab worklist (OROS)",
    icon: "FlaskConical",
    category: "clinical",
    href: "/lab",
    activeFlag: true,
    requiredRole: "CLINICAL",
    systemAppFlag: false,
    weight: 40,
  },
  {
    id: "app-finance",
    appCode: "finance",
    name: "Finance",
    description: "Billing, claims, MusheX",
    icon: "Wallet",
    category: "finance",
    href: "/finance",
    activeFlag: true,
    requiredRole: "FINANCE",
    systemAppFlag: false,
    weight: 10,
  },
  {
    id: "app-registry",
    appCode: "registry",
    name: "Registry",
    description: "Clients, providers, facilities (VITO / VARAPI / TUSO)",
    icon: "Database",
    category: "registry",
    href: "/registry",
    activeFlag: true,
    requiredRole: "CLINICAL",
    systemAppFlag: false,
    weight: 10,
  },
  {
    id: "app-operations-vito",
    appCode: "ops_vito",
    name: "Identity ops",
    description: "VITO operational tooling",
    icon: "Shield",
    category: "operations",
    href: "/operations/vito",
    activeFlag: true,
    requiredRole: "ADMIN",
    systemAppFlag: false,
    weight: 20,
  },
  {
    id: "app-marketplace",
    appCode: "marketplace",
    name: "Marketplace",
    description: "Msika / commerce flows",
    icon: "BriefcaseBusiness",
    category: "operations",
    href: "/marketplace",
    activeFlag: true,
    requiredRole: "COMMERCE",
    systemAppFlag: false,
    weight: 30,
  },
  {
    id: "app-citizen",
    appCode: "citizen",
    name: "Citizen services",
    description: "Health ID and citizen self-service",
    icon: "IdCard",
    category: "citizen",
    href: "/citizen",
    activeFlag: true,
    systemAppFlag: false,
    weight: 40,
  },
  {
    id: "app-documents",
    appCode: "my_documents",
    name: "My documents",
    description: "Personal document vault",
    icon: "FileText",
    category: "citizen",
    href: "/home/documents",
    activeFlag: true,
    systemAppFlag: false,
    weight: 50,
  },
  {
    id: "app-search",
    appCode: "knowledge_search",
    name: "Knowledge search",
    description: "Indexed health and service knowledge",
    icon: "BookOpen",
    category: "intelligence",
    href: "/search",
    activeFlag: true,
    systemAppFlag: false,
    weight: 10,
  },
  {
    id: "app-settings",
    appCode: "settings",
    name: "Settings",
    description: "Account and experience preferences",
    icon: "Settings2",
    category: "system",
    href: "/settings",
    activeFlag: true,
    systemAppFlag: true,
    weight: 100,
  },
  {
    id: "app-shell-file-manager",
    appCode: "shell_file_manager",
    name: "File manager",
    description: "vNext document and resource explorer",
    icon: "FolderOpen",
    category: "system",
    href: "/shell/file-manager",
    activeFlag: true,
    systemAppFlag: true,
    weight: 5,
  },
  {
    id: "app-shell-task-manager",
    appCode: "shell_task_manager",
    name: "Task manager",
    description: "Open apps and workspaces",
    icon: "Layers",
    category: "system",
    href: "/shell/task-manager",
    activeFlag: true,
    systemAppFlag: true,
    weight: 4,
  },
];

export const SHELL_COMMANDS: ShellCommand[] = [
  {
    id: "cmd-open-start",
    label: "Open Start menu",
    description: "Launcher",
    keywords: ["start", "launcher", "menu", "apps"],
    action: { type: "open-start" },
  },
  {
    id: "cmd-open-search",
    label: "Open search",
    keywords: ["search", "find", "go"],
    action: { type: "open-search" },
  },
  {
    id: "cmd-home",
    label: "Go to Home",
    keywords: ["home", "dashboard"],
    action: { type: "navigate", href: "/home" },
  },
  {
    id: "cmd-file-manager",
    label: "Open File manager",
    keywords: ["files", "documents", "explorer", "vault"],
    action: { type: "open-file-manager" },
  },
  {
    id: "cmd-task-manager",
    label: "Open Task manager",
    keywords: ["tasks", "windows", "workspaces", "running"],
    action: { type: "open-task-manager" },
  },
  {
    id: "cmd-queue",
    label: "Open Queue",
    keywords: ["queue", "triage"],
    action: { type: "navigate", href: "/queue" },
    requiredRole: "QUEUE",
  },
  {
    id: "cmd-clinical",
    label: "Open Clinical hub",
    keywords: ["clinical", "care"],
    action: { type: "navigate", href: "/clinical" },
    requiredRole: "CLINICAL",
  },
];

export function appVisibleForUser(
  app: AppDefinition,
  hasRole: (role: string) => boolean,
): boolean {
  if (!app.activeFlag) return false;
  if (!app.requiredRole) return true;
  return matchesRequiredRole(hasRole, app.requiredRole);
}

export function listVisibleShellApps(hasRole: (role: string) => boolean): AppDefinition[] {
  return SHELL_APPS.filter((a) => appVisibleForUser(a, hasRole)).sort((a, b) => {
    const wa = a.weight ?? 50;
    const wb = b.weight ?? 50;
    if (wa !== wb) return wa - wb;
    return a.name.localeCompare(b.name);
  });
}

export function findShellAppByCode(code: string): AppDefinition | undefined {
  return SHELL_APPS.find((a) => a.appCode === code);
}

export function visibleShellCommands(
  hasRole: (role: string) => boolean,
): ShellCommand[] {
  return SHELL_COMMANDS.filter((c) => !c.requiredRole || matchesRequiredRole(hasRole, c.requiredRole));
}
