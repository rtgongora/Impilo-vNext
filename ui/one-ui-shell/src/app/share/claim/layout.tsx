import type { ReactNode } from "react";

/** Public patient-share claim flow — no auth, standalone card layout. */
export default function ShareClaimLayout({ children }: { children: ReactNode }) {
  return (
    <div className="min-h-screen bg-gray-50 flex items-start justify-center py-12 px-4">
      <div className="w-full max-w-lg">
        <div className="mb-6 text-center">
          <h1 className="text-2xl font-semibold text-gray-900">Claim Shared Health Record</h1>
          <p className="mt-1 text-sm text-gray-500">Impilo Health OS — secure patient share flow</p>
        </div>
        {children}
      </div>
    </div>
  );
}
