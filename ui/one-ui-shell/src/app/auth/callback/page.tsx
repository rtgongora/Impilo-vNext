import { Suspense } from "react";

import { CallbackClient } from "./CallbackClient";

export default function AuthCallbackPage() {
  return (
    <Suspense
      fallback={
        <div className="mx-auto max-w-lg px-4 py-16 text-center text-sm text-neutral-600">
          Loading…
        </div>
      }
    >
      <CallbackClient />
    </Suspense>
  );
}
