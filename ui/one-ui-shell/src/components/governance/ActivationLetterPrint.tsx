"use client";

/**
 * Printable activation letter — the paper channel for provider onboarding where
 * email/SMS is unreliable (print-CSS, no server-side PDF dependency).
 *
 * SAFETY INVARIANT: the letter carries the Provider ID, a time-boxed activation
 * link and one-time code, expiry, and helpdesk contact. It must NEVER carry a
 * password or any clinical content — credentials are set by the provider at
 * activation (Keycloak owns them). Enforced by ActivationLetterPrint.test.tsx.
 */

export interface ActivationLetterProps {
  displayName: string;
  providerId?: string;
  activationUrl: string;
  activationCode?: string;
  expiresAt?: string;
  facilityName?: string;
  helpdeskContact?: string;
}

export function ActivationLetterPrint({
  displayName,
  providerId,
  activationUrl,
  activationCode,
  expiresAt,
  facilityName,
  helpdeskContact = "helpdesk@mohcc.gov.zw",
}: ActivationLetterProps) {
  return (
    <div className="activation-letter mx-auto max-w-2xl bg-white p-10 text-neutral-900 print:p-0" data-testid="activation-letter">
      <style>{`
        @media print {
          body * { visibility: hidden; }
          .activation-letter, .activation-letter * { visibility: visible; }
          .activation-letter { position: absolute; inset: 0; }
        }
      `}</style>
      <header className="border-b border-neutral-300 pb-4">
        <p className="text-sm font-semibold uppercase tracking-wide">Ministry of Health and Child Care — Impilo</p>
        <h1 className="mt-2 text-xl font-bold">Account activation letter</h1>
      </header>

      <div className="mt-6 space-y-4 text-sm leading-relaxed">
        <p>Dear {displayName},</p>
        <p>
          You have been onboarded to Impilo, the national Health Operating System.
          {facilityName ? ` Your workplace context is ${facilityName}.` : ""}
        </p>

        {providerId ? (
          <div className="rounded border border-neutral-300 p-4">
            <p className="text-xs uppercase tracking-wide text-neutral-500">Your Provider ID</p>
            <p className="mt-1 font-mono text-lg font-bold" data-testid="letter-provider-id">{providerId}</p>
          </div>
        ) : null}

        <div className="rounded border border-neutral-300 p-4">
          <p className="text-xs uppercase tracking-wide text-neutral-500">Activate your account</p>
          <p className="mt-1">
            Visit <span className="font-mono font-semibold">{activationUrl}</span>
          </p>
          {activationCode ? (
            <p className="mt-2">
              One-time activation code: <span className="font-mono text-lg font-bold" data-testid="letter-activation-code">{activationCode}</span>
            </p>
          ) : null}
          {expiresAt ? <p className="mt-2 text-neutral-600">This invitation expires on {expiresAt}.</p> : null}
        </div>

        <p>
          During activation you will set your own password and confirm your identity.
          Nobody from the Ministry or Impilo will ever ask you for your password.
        </p>
        <p>
          If you need help, contact the helpdesk: <span className="font-semibold">{helpdeskContact}</span>.
        </p>
      </div>

      <footer className="mt-8 border-t border-neutral-300 pt-4 text-xs text-neutral-500">
        This letter contains no medical information. If you received it in error, please return it to the facility
        administrator or destroy it.
      </footer>
    </div>
  );
}
