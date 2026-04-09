/**
 * Scheduling cluster paths: org admins may use these with facility context only
 * (no workspace) — see AuthGuardProvider "workspace" guard branch.
 */
export function isSchedulingClusterPath(pathname: string): boolean {
  if (pathname === "/scheduling") return true;
  return pathname.startsWith("/scheduling/");
}
