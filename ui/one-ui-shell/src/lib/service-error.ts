/** Normalize API / query errors into user-safe messages (no white-screen crashes). */

export function formatServiceError(error: unknown): string {
  if (error instanceof Error) {
    const msg = error.message;
    if (/fetch failed|network|ECONNREFUSED|Failed to fetch/i.test(msg)) {
      return "Cannot reach the service. It may not be deployed in this preview yet.";
    }
    if (/500|502|503|504|Internal Server Error/i.test(msg)) {
      return "The backend service returned an error. It may not be running in this preview wave yet.";
    }
    if (/401|403|Unauthorized|Forbidden/i.test(msg)) {
      return "You do not have permission for this action in the current context.";
    }
    if (/404|Not Found/i.test(msg)) {
      return "This capability is not available at this endpoint yet.";
    }
    return msg.length > 180 ? `${msg.slice(0, 177)}…` : msg;
  }
  if (typeof error === "string") return error;
  return "Request failed. The service may be unavailable in this preview.";
}

export function isLikelyServiceUnavailable(error: unknown): boolean {
  const text = error instanceof Error ? error.message : String(error ?? "");
  return /500|502|503|504|ECONNREFUSED|I\/O error|not be deployed|unavailable/i.test(text);
}
