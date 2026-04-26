/**
 * Where the OS-like shell chrome (taskbar, global shortcuts) is shown.
 * Auth/legal/minimal flows stay uncluttered.
 */
export function shouldShowExperienceShell(pathname: string | null, isAuthenticated: boolean): boolean {
  if (!pathname || !isAuthenticated) return false;
  if (pathname.startsWith("/auth")) return false;
  if (
    pathname.startsWith("/consent") ||
    pathname.startsWith("/privacy") ||
    pathname.startsWith("/terms") ||
    pathname.startsWith("/account-deletion") ||
    pathname === "/kiosk"
  ) {
    return false;
  }
  return true;
}
