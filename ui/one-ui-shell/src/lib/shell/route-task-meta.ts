import { matchRouteDefinition } from "@/lib/routes";
import type { RunningTaskType } from "@/lib/shell/types";
import { findShellAppByCode, SHELL_APPS } from "@/lib/shell/app-registry";

export interface RouteTaskMeta {
  title: string;
  appId: string;
  taskType: RunningTaskType;
  contextRef?: string;
}

/**
 * Derive shell task metadata from the current pathname for taskbar / recents.
 * Uses route registry when available; falls back to a generic module task.
 */
export function deriveRouteTaskMeta(pathname: string): RouteTaskMeta {
  const def = matchRouteDefinition(pathname);
  if (pathname.startsWith("/ehr/")) {
    const parts = pathname.split("/").filter(Boolean);
    const patientId = parts[1];
    if (parts[2] === "imaging" && parts[3] === "viewer") {
      const app = findShellAppByCode("ehr_search");
      return {
        title: "DICOM viewer",
        appId: app?.id ?? "ehr",
        taskType: "dicom_viewer",
        contextRef: patientId ? `patient:${patientId}:dicom-viewer` : undefined,
      };
    }
    const segment = parts[2] ?? "summary";
    const app = findShellAppByCode("ehr_search");
    return {
      title: patientId ? `Patient chart · ${segment}` : "Patient chart",
      appId: app?.id ?? "ehr",
      taskType: "patient_chart",
      contextRef: patientId ? `patient:${patientId}` : undefined,
    };
  }

  if (def) {
    const zone = def.zone;
    let taskType: RunningTaskType = "module";
    if (zone === "registry") taskType = "registry_record";
    if (zone === "facility") taskType = "facility";
    if (zone === "workspace") taskType = "workspace";
    if (def.path.startsWith("/shell/")) taskType = "shell_utility";

    return {
      title: def.pageTitle,
      appId: def.zone,
      taskType,
    };
  }

  const app = SHELL_APPS.find((item) => pathname === item.href || pathname.startsWith(`${item.href}/`));
  if (app) {
    return {
      title: app.appCode === "learning" ? "Impilo Fundo" : app.name,
      appId: app.id,
      taskType: "module",
    };
  }

  return {
    title: "Experience",
    appId: "experience",
    taskType: "module",
  };
}
