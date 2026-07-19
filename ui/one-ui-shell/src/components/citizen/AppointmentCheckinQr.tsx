"use client";

/**
 * Self-check-in QR (CJ11). The citizen mints a short-lived signed token for their own
 * appointment and shows it as a scannable QR at the facility kiosk. The token binds the
 * appointment id + intent and expires; ownership is enforced server-side at mint time.
 */

import { useState } from "react";
import { QRCodeSVG } from "qrcode.react";
import { QrCode, AlertCircle, RefreshCw } from "lucide-react";
import { useCheckinToken } from "@/hooks/queries/useCheckinToken";

export function AppointmentCheckinQr({ appointmentId }: { appointmentId: string }) {
  const [show, setShow] = useState(false);
  const { data, isLoading, isError, refetch, isFetching } = useCheckinToken(appointmentId, show);
  const token = data?.data?.token;
  const expiresAt = data?.data?.expiresAt;

  return (
    <section className="rounded-lg border border-border bg-card p-4">
      <div className="flex items-start gap-3">
        <QrCode className="mt-0.5 h-5 w-5 shrink-0 text-primary" />
        <div className="flex-1">
          <h2 className="text-sm font-medium text-foreground">Self check-in</h2>
          <p className="mt-1 text-sm text-muted-foreground">
            Show this QR code at the facility kiosk to check in for your appointment — no need to
            queue at the desk.
          </p>

          {!show ? (
            <button
              type="button"
              onClick={() => setShow(true)}
              className="mt-3 rounded-md bg-primary px-3 py-1.5 text-sm font-medium text-white"
            >
              Show my check-in QR
            </button>
          ) : (
            <div className="mt-3">
              {isLoading || isFetching ? (
                <p className="text-sm text-muted-foreground">Preparing your check-in code…</p>
              ) : isError || !token ? (
                <div className="flex items-center gap-2 text-sm text-danger">
                  <AlertCircle className="h-4 w-4" />
                  <span>We couldn&apos;t prepare your check-in code.</span>
                  <button type="button" onClick={() => void refetch()} className="underline">
                    Try again
                  </button>
                </div>
              ) : (
                <div className="flex flex-col items-center gap-2">
                  <div className="rounded-lg bg-white p-3" data-testid="checkin-qr">
                    <QRCodeSVG value={token} size={180} level="M" includeMargin={false} />
                  </div>
                  {expiresAt && (
                    <p className="text-xs text-muted-foreground">
                      Valid until {new Date(expiresAt).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}
                    </p>
                  )}
                  <button
                    type="button"
                    onClick={() => void refetch()}
                    className="flex items-center gap-1 text-xs text-primary-hover underline"
                  >
                    <RefreshCw className="h-3 w-3" /> Refresh code
                  </button>
                </div>
              )}
            </div>
          )}
        </div>
      </div>
    </section>
  );
}
