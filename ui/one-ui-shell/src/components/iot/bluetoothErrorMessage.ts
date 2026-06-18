/**
 * User-friendly Web Bluetooth error messages for vitals scanning.
 * Ported from the Impilo Practitioner desktop app.
 */

export interface FriendlyBleError {
  title: string;
  message: string;
}

export function formatBluetoothError(error: unknown, vitalName = ""): FriendlyBleError {
  const err = error as { name?: string; message?: string } | undefined;
  const name = err?.name ?? "";
  const raw = String(err?.message ?? error ?? "").trim();
  const lower = raw.toLowerCase();
  const vital = vitalName ? `${vitalName} ` : "";

  if (name === "NotFoundError" || lower.includes("no device selected")) {
    return {
      title: "No device found",
      message: `We couldn't find a nearby Bluetooth ${vital}device. Make sure it is turned on, charged, and in pairing mode, then try again.`,
    };
  }

  if (
    name === "NotAllowedError" ||
    name === "SecurityError" ||
    lower.includes("permission") ||
    lower.includes("denied")
  ) {
    return {
      title: "Bluetooth access needed",
      message:
        "Please allow Bluetooth access for this app in your system settings, then try scanning again.",
    };
  }

  if (
    lower.includes("invalid service") ||
    lower.includes("invalid uuid") ||
    lower.includes("must be a valid uuid")
  ) {
    return {
      title: "Device not configured",
      message: `This ${vital}measurement is not set up for Bluetooth scanning yet. You can enter the value manually, or contact your administrator.`,
    };
  }

  if (name === "NetworkError" || lower.includes("gatt server is disconnected")) {
    return {
      title: "Connection lost",
      message:
        "The Bluetooth connection was interrupted. Move closer to the device, wake it up, and try again.",
    };
  }

  if (lower.includes("user cancelled") || lower.includes("user canceled") || name === "AbortError") {
    return {
      title: "Scan cancelled",
      message: "Bluetooth scanning was cancelled. Tap Connect when you're ready to try again.",
    };
  }

  if (typeof navigator !== "undefined" && !navigator.bluetooth) {
    return {
      title: "Bluetooth unavailable",
      message:
        "Bluetooth is not supported in this browser. Use Chrome or Edge on desktop with Bluetooth enabled, or enter the value manually.",
    };
  }

  return {
    title: "Could not connect",
    message: `Unable to connect to the ${vital}device. Check that Bluetooth is on, the device is nearby and powered on, then try again.`,
  };
}
