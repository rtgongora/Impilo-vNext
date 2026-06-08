import type { ReactNode } from "react";
import { render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { describe, expect, it, vi } from "vitest";
import RegistryAdminLandingPage from "./page";

vi.mock("next/navigation", () => ({
  useSearchParams: () => new URLSearchParams(),
}));

vi.mock("@/components/AppLayout", () => ({
  AppLayout: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));

vi.mock("@/components/PageShell", () => ({
  PageShell: ({ children, title }: { children: ReactNode; title: string }) => (
    <div>
      <h1>{title}</h1>
      {children}
    </div>
  ),
}));

const authUser = { roles: ["SYSTEM_ADMIN"], actorType: "OPERATOR" as const };

vi.mock("@/hooks/useAuthStore", () => {
  function useAuthStore(selector: (s: { user: typeof authUser | null }) => unknown) {
    return selector({ user: authUser });
  }
  useAuthStore.getState = () => ({ user: authUser });
  return { useAuthStore };
});

const rehydrate = vi.fn();
vi.mock("@/hooks/useOperationalContextStore", () => {
  function useOperationalContextStore(selector: (s: { rehydrateFromSession: () => void }) => unknown) {
    return selector({ rehydrateFromSession: rehydrate });
  }
  useOperationalContextStore.getState = () => ({
    setOperationalMode: vi.fn(),
    setRegistryAdminSubtype: vi.fn(),
  });
  return { useOperationalContextStore };
});

vi.mock("@/hooks/queries/useRegistry", () => ({
  useProviders: () => ({ data: { data: [] }, isLoading: false, isError: false }),
}));

vi.mock("@/hooks/queries/useVitoIssuance", () => ({
  useIssuanceQueue: () => ({ data: undefined, isLoading: false, isError: false }),
}));

vi.mock("@/hooks/queries/useVitoRegistryAdmin", () => ({
  usePendingProvisionalIds: () => ({ data: undefined, isLoading: false, isError: false }),
  usePendingRegistryDedupCases: () => ({ data: undefined, isLoading: false, isError: false }),
}));

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <RegistryAdminLandingPage />
    </QueryClientProvider>,
  );
}

describe("RegistryAdminLandingPage", () => {
  it("frames registry administration as a high-trust plane with subtype scaffolding", () => {
    renderPage();

    expect(screen.getByRole("heading", { name: "Registry administration" })).toBeInTheDocument();
    expect(screen.getByText(/high-trust plane/i)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Client registry \(MPI\)/i })).toHaveAttribute(
      "href",
      "/registry/clients?from=registry-admin",
    );
    expect(screen.getByRole("link", { name: /Trust, federation & keys/i })).toHaveAttribute(
      "href",
      "/registry/trust?from=registry-admin",
    );
    expect(screen.getByRole("link", { name: /Provider registry/i })).toHaveAttribute(
      "href",
      "/registry/providers?from=registry-admin",
    );
    expect(screen.queryByText(/Temporary navigational target/i)).not.toBeInTheDocument();
  });
});
