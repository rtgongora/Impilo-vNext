import type { ReactNode } from "react";

/** Public fundraiser share target — standalone card layout, no authenticated shell. */
export default function ShareFundLayout({ children }: { children: ReactNode }) {
  return (
    <div className="min-h-screen bg-background flex items-start justify-center py-12 px-4">
      <div className="w-full max-w-lg">
        <div className="mb-6 text-center">
          <h1 className="text-2xl font-semibold text-foreground">Fundraiser</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            Impilo Health OS — facility-verified fundraising
          </p>
        </div>
        {children}
      </div>
    </div>
  );
}
