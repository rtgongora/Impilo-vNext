import type { ReactNode } from "react";
import { fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import MsikaGovernancePage from "./page";

vi.mock("next/link", () => ({
  default: ({ children, href, ...props }: { children: ReactNode; href: string }) => (
    <a href={href} {...props}>
      {children}
    </a>
  ),
}));

vi.mock("@/components/AppLayout", () => ({
  AppLayout: ({ children }: { children: ReactNode }) => <div>{children}</div>,
}));

vi.mock("@/components/PageShell", () => ({
  PageShell: ({
    children,
    title,
    subtitle,
  }: {
    children: ReactNode;
    title: string;
    subtitle?: string;
  }) => (
    <div>
      <h1>{title}</h1>
      {subtitle ? <p>{subtitle}</p> : null}
      {children}
    </div>
  ),
}));

const mocks = vi.hoisted(() => ({
  createCatalog: vi.fn(),
  submitReview: vi.fn(),
  approveCatalog: vi.fn(),
  publishCatalog: vi.fn(),
  approveMapping: vi.fn(),
  rejectMapping: vi.fn(),
  importCsv: vi.fn(),
}));

vi.mock("@/hooks/queries/useMsikaGovernance", () => ({
  useMsikaCatalogs: () => ({
    data: {
      items: [
        {
          catalogId: "cat-1",
          name: "EDLIZ 2025",
          scope: "NATIONAL",
          status: "DRAFT",
          version: "2025.1.0",
          itemCount: 342,
        },
      ],
    },
    isLoading: false,
    isError: false,
  }),
  useMsikaPendingMappings: () => ({
    data: {
      items: [
        {
          id: "map-1",
          externalCode: "ABC-100",
          externalName: "Artemether",
          internalItemId: "item-9",
          mapType: "EXACT",
          confidence: 0.98,
        },
      ],
    },
    isLoading: false,
    isError: false,
  }),
  useCreateMsikaCatalog: () => ({ mutate: mocks.createCatalog, isPending: false }),
  useSubmitMsikaCatalogReview: () => ({ mutate: mocks.submitReview, isPending: false }),
  useApproveMsikaCatalog: () => ({ mutate: mocks.approveCatalog, isPending: false }),
  usePublishMsikaCatalog: () => ({ mutate: mocks.publishCatalog, isPending: false }),
  useApproveMsikaMapping: () => ({ mutate: mocks.approveMapping, isPending: false }),
  useRejectMsikaMapping: () => ({ mutate: mocks.rejectMapping, isPending: false }),
  useImportMsikaCatalogCsv: () => ({ mutate: mocks.importCsv, isPending: false, data: null, isError: false }),
}));

describe("MsikaGovernancePage", () => {
  beforeEach(() => {
    Object.values(mocks).forEach((mock) => mock.mockReset());
  });

  it("renders the canonical Experience governance workspace for MSIKA", () => {
    render(<MsikaGovernancePage />);

    expect(screen.getByRole("heading", { level: 1, name: /MSIKA governance/i })).toBeInTheDocument();
    expect(screen.getByText(/Catalog governance, mapping decisions, publishing, and CSV import/i)).toBeInTheDocument();
    expect(screen.getByText("EDLIZ 2025")).toBeInTheDocument();
    expect(screen.getByText("ABC-100")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Commerce & payer stack/i })).toHaveAttribute(
      "href",
      "/finance/commerce-integrations",
    );
  });

  it("posts create, import, and mapping actions through the governance hooks", async () => {
    const user = userEvent.setup();
    render(<MsikaGovernancePage />);

    await user.clear(screen.getByLabelText(/Catalog name/i));
    await user.type(screen.getByLabelText(/Catalog name/i), "National formulary");
    await user.click(screen.getByRole("button", { name: /Create catalog/i }));

    expect(mocks.createCatalog).toHaveBeenCalledWith(
      {
        name: "National formulary",
        scope: "NATIONAL",
        version: "2025.1.0",
      },
      expect.any(Object),
    );

    fireEvent.change(screen.getByLabelText(/CSV file/i), {
      target: {
        files: [new File(["canonical_code,display_name"], "edliz.csv", { type: "text/csv" })],
      },
    });
    await user.type(screen.getByLabelText(/Target catalog id/i), "cat-1");
    await user.click(screen.getByRole("button", { name: /Upload & import/i }));

    expect(mocks.importCsv).toHaveBeenCalledWith({
      catalogId: "cat-1",
      file: expect.any(File),
    });

    await user.click(screen.getByRole("button", { name: /^Approve$/i }));
    expect(mocks.approveMapping).toHaveBeenCalledWith("map-1");
  });
});
