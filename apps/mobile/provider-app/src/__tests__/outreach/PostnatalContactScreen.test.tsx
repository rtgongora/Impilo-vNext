/**
 * PostnatalContactScreen — enforces docs/clinical/rmnp/chw-community-postnatal-mobile-contract.md
 * §4 at the rendering layer: an unscreened contact never shows a green all-clear, a referral
 * cannot submit without its reason, breastfeeding offers NOT_ASSESSED as a real answer, and
 * `contactSetting` starts blank rather than defaulted. The service-layer contract (nulls forwarded
 * unchanged, offline queueing keyed by `clientOfflineId`) is proved in
 * `postnatalContactService.test.ts`; this file proves the screen does not launder it.
 */

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import React from "react";
import { act } from "react";
import { createRoot, type Root } from "react-dom/client";

const mockRecordPostnatalContact = vi.fn();
const mockGetPostnatalContacts = vi.fn();

vi.mock("../../services/postnatalContactService", async (importOriginal) => {
  const actual = await importOriginal<typeof import("../../services/postnatalContactService")>();
  return {
    ...actual,
    recordPostnatalContact: (...a: unknown[]) => mockRecordPostnatalContact(...a),
    getPostnatalContacts: (...a: unknown[]) => mockGetPostnatalContacts(...a),
  };
});

let generatedIds: string[] = [];
vi.mock("@impilo/mobile-trust", () => ({
  generateId: () => {
    const id = `id-${generatedIds.length + 1}`;
    generatedIds.push(id);
    return id;
  },
}));

vi.mock("@impilo/mobile-offline", () => ({
  useSyncEngine: () => ({ pendingCount: 0 }),
}));

vi.mock("@impilo/mobile-design-system", async (importOriginal) => {
  const actual = await importOriginal<Record<string, unknown>>();
  return {
    ...actual,
    Screen: ({ children }: any) => children,
    Header: ({ title }: any) => React.createElement("h1", null, title),
    Card: ({ children }: any) => React.createElement("div", null, children),
    CardBody: ({ children }: any) => React.createElement("div", null, children),
    Badge: ({ children, testID }: any) => React.createElement("span", { "data-testid": testID }, children),
    Button: ({ title, onPress, testID, disabled }: any) =>
      React.createElement(
        "button",
        { "data-testid": testID, onClick: onPress, disabled },
        title,
      ),
    TextField: ({ label, value, onChange, onChangeText, testID, error }: any) =>
      React.createElement(
        "div",
        { "data-testid": testID },
        React.createElement("input", {
          "data-testid": `${testID}-input`,
          value: value ?? "",
          onChange: (e: { target: { value: string } }) => (onChange ?? onChangeText)?.(e.target.value),
        }),
        error ? React.createElement("span", { "data-testid": `${testID}-error` }, error) : null,
      ),
    Select: ({ value, options, onChange, testID, error }: any) =>
      React.createElement(
        "div",
        { "data-testid": testID },
        React.createElement(
          "select",
          {
            "data-testid": `${testID}-input`,
            value: value ?? "",
            onChange: (e: { target: { value: string } }) => onChange?.(e.target.value),
          },
          React.createElement("option", { value: "" }, ""),
          ...(options ?? []).map((o: { value: string; label: string }) =>
            React.createElement("option", { key: o.value, value: o.value }, o.label),
          ),
        ),
        error ? React.createElement("span", { "data-testid": `${testID}-error` }, error) : null,
      ),
    ErrorState: ({ title, message, testID }: any) =>
      React.createElement("div", { "data-testid": testID }, [title, message].filter(Boolean).join(": ")),
  };
});

function renderScreen() {
  const container = document.createElement("div");
  document.body.appendChild(container);
  const root = createRoot(container);
  return { container, root };
}

function byTestId(container: HTMLElement, testId: string) {
  return container.querySelector(`[data-testid="${testId}"]`);
}

async function click(container: HTMLElement, testId: string) {
  const el = byTestId(container, testId) as HTMLElement | null;
  if (!el) throw new Error(`Missing element: ${testId}`);
  await act(async () => {
    el.dispatchEvent(new MouseEvent("click", { bubbles: true }));
  });
}

async function typeInto(container: HTMLElement, testId: string, value: string) {
  const input = byTestId(container, `${testId}-input`) as HTMLInputElement | null;
  if (!input) throw new Error(`Missing input: ${testId}`);
  await act(async () => {
    Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, "value")!.set!.call(input, value);
    input.dispatchEvent(new Event("input", { bubbles: true }));
  });
}

async function selectOption(container: HTMLElement, testId: string, value: string) {
  const select = byTestId(container, `${testId}-input`) as HTMLSelectElement | null;
  if (!select) throw new Error(`Missing select: ${testId}`);
  await act(async () => {
    select.value = value;
    select.dispatchEvent(new Event("change", { bubbles: true }));
  });
}

async function flush() {
  for (let i = 0; i < 4; i += 1) {
    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 0));
    });
  }
}

describe("PostnatalContactScreen", () => {
  let mounted: { root: Root; container: HTMLElement } | undefined;

  beforeEach(() => {
    vi.clearAllMocks();
    generatedIds = [];
  });

  afterEach(() => {
    if (mounted) {
      act(() => mounted?.root.unmount());
      mounted = undefined;
    }
  });

  async function fillMinimalContext(container: HTMLElement) {
    await typeInto(container, "postnatal-mother-cpid", "CP-1");
    await selectOption(container, "postnatal-contact-timing", "DAY_3");
    await selectOption(container, "postnatal-contact-setting", "HOME");
  }

  it("renders 'Not screened' before any danger-sign answer, never a reassuring default", async () => {
    const mod = await import("../../screens/outreach/PostnatalContactScreen");
    mounted = renderScreen();
    act(() => mounted?.root.render(React.createElement(mod.PostnatalContactScreen)));
    await flush();

    expect(byTestId(mounted.container, "postnatal-danger-signs-summary")?.textContent).toBe("Not screened");
    // The danger-sign finding gate is disabled until the screen itself is marked complete.
    expect((byTestId(mounted.container, "postnatal-danger-signs-yes") as HTMLButtonElement).disabled).toBe(true);
  });

  it("disables the danger-sign finding until the screen is marked complete, and clears any answer if un-marked", async () => {
    const mod = await import("../../screens/outreach/PostnatalContactScreen");
    mounted = renderScreen();
    act(() => mounted?.root.render(React.createElement(mod.PostnatalContactScreen)));
    await flush();

    await click(mounted.container, "postnatal-screening-complete-yes");
    expect((byTestId(mounted.container, "postnatal-danger-signs-yes") as HTMLButtonElement).disabled).toBe(false);

    await click(mounted.container, "postnatal-danger-signs-yes");
    expect(byTestId(mounted.container, "postnatal-danger-signs-summary")?.textContent).toBe(
      "Danger signs present",
    );

    // Retracting "screen completed" must retract the danger-sign finding too — it cannot outlive
    // the screen it depends on (chk_pnc_screening_gate).
    await click(mounted.container, "postnatal-screening-complete-yes");
    expect(byTestId(mounted.container, "postnatal-danger-signs-summary")?.textContent).toBe("Not screened");
    expect((byTestId(mounted.container, "postnatal-danger-signs-yes") as HTMLButtonElement).disabled).toBe(true);
  });

  it("blocks submission of a referral with no reason, and never calls the service with an incomplete one", async () => {
    const mod = await import("../../screens/outreach/PostnatalContactScreen");
    mounted = renderScreen();
    act(() => mounted?.root.render(React.createElement(mod.PostnatalContactScreen)));
    await flush();

    await fillMinimalContext(mounted.container);
    await click(mounted.container, "postnatal-referral-made-yes");
    await click(mounted.container, "postnatal-submit");
    await flush();

    expect(mockRecordPostnatalContact).not.toHaveBeenCalled();
    expect(byTestId(mounted.container, "postnatal-referral-reason-error")?.textContent).toContain(
      "must carry its reason",
    );
  });

  it("submits exactly what was answered — omitting the danger-sign finding and breastfeeding status as unanswered, and never defaulting contactSetting", async () => {
    mockRecordPostnatalContact.mockResolvedValue({
      queued: false,
      record: { postnatal_contact_id: "pnc-1", replayed: false },
    });
    const mod = await import("../../screens/outreach/PostnatalContactScreen");
    mounted = renderScreen();
    act(() => mounted?.root.render(React.createElement(mod.PostnatalContactScreen)));
    await flush();

    await fillMinimalContext(mounted.container);
    await click(mounted.container, "postnatal-submit");
    await flush();

    expect(mockRecordPostnatalContact).toHaveBeenCalledTimes(1);
    const [payload] = mockRecordPostnatalContact.mock.calls[0];
    expect(payload.motherCpid).toBe("CP-1");
    expect(payload.contactTiming).toBe("DAY_3");
    expect(payload.contactSetting).toBe("HOME");
    expect(payload.screeningComplete).toBeUndefined();
    expect(payload.dangerSignsPresent).toBeUndefined();
    expect(payload.breastfeedingStatus).toBeUndefined();
    expect(payload.referralReason).toBeUndefined();
    expect(typeof payload.clientOfflineId).toBe("string");
    expect(payload.clientOfflineId.length).toBeGreaterThan(0);

    expect(byTestId(mounted.container, "postnatal-outcome-banner")?.textContent).toContain("Contact recorded");
  });

  it("sends breastfeeding NOT_ASSESSED as its own real answer, distinct from omitted and from NONE", async () => {
    mockRecordPostnatalContact.mockResolvedValue({ queued: false, record: { postnatal_contact_id: "pnc-2", replayed: false } });
    const mod = await import("../../screens/outreach/PostnatalContactScreen");
    mounted = renderScreen();
    act(() => mounted?.root.render(React.createElement(mod.PostnatalContactScreen)));
    await flush();

    await fillMinimalContext(mounted.container);
    await selectOption(mounted.container, "postnatal-breastfeeding-status", "NOT_ASSESSED");
    await click(mounted.container, "postnatal-submit");
    await flush();

    const [payload] = mockRecordPostnatalContact.mock.calls[0];
    expect(payload.breastfeedingStatus).toBe("NOT_ASSESSED");
  });

  it("shows the offline-queued outcome distinctly, and never tells the CHW to re-enter the visit", async () => {
    mockRecordPostnatalContact.mockResolvedValue({ queued: true, clientOfflineId: "id-1" });
    const mod = await import("../../screens/outreach/PostnatalContactScreen");
    mounted = renderScreen();
    act(() => mounted?.root.render(React.createElement(mod.PostnatalContactScreen)));
    await flush();

    await fillMinimalContext(mounted.container);
    await click(mounted.container, "postnatal-submit");
    await flush();

    const banner = byTestId(mounted.container, "postnatal-outcome-banner");
    expect(banner?.textContent).toContain("Saved offline");
    // The instruction must be "do not re-enter it", never a prompt to capture the visit again.
    expect(banner?.textContent).toMatch(/do not re-enter/i);
  });

  it("shows a replayed submission as 'already recorded', not as a fresh success", async () => {
    mockRecordPostnatalContact.mockResolvedValue({
      queued: false,
      record: { postnatal_contact_id: "pnc-3", replayed: true },
    });
    const mod = await import("../../screens/outreach/PostnatalContactScreen");
    mounted = renderScreen();
    act(() => mounted?.root.render(React.createElement(mod.PostnatalContactScreen)));
    await flush();

    await fillMinimalContext(mounted.container);
    await click(mounted.container, "postnatal-submit");
    await flush();

    expect(byTestId(mounted.container, "postnatal-outcome-banner")?.textContent).toContain("Already recorded");
  });

  it("shows a 422 field refusal's exact reason, and keeps the same clientOfflineId on retry rather than minting a new one", async () => {
    const { ApiError } = await import("@impilo/mobile-api-client");
    mockRecordPostnatalContact.mockRejectedValueOnce(
      new ApiError({
        code: "PNC_SETTING_REQUIRED",
        message: "A postnatal contact must say where it happened.",
        status: 422,
        correlationId: "corr-1",
      }),
    );
    mockRecordPostnatalContact.mockResolvedValueOnce({
      queued: false,
      record: { postnatal_contact_id: "pnc-4", replayed: false },
    });
    const mod = await import("../../screens/outreach/PostnatalContactScreen");
    mounted = renderScreen();
    act(() => mounted?.root.render(React.createElement(mod.PostnatalContactScreen)));
    await flush();

    await fillMinimalContext(mounted.container);
    await click(mounted.container, "postnatal-submit");
    await flush();

    expect(byTestId(mounted.container, "postnatal-submit-error")?.textContent).toContain(
      "must say where it happened",
    );

    await click(mounted.container, "postnatal-submit");
    await flush();

    expect(mockRecordPostnatalContact).toHaveBeenCalledTimes(2);
    const [firstPayload] = mockRecordPostnatalContact.mock.calls[0];
    const [secondPayload] = mockRecordPostnatalContact.mock.calls[1];
    expect(secondPayload.clientOfflineId).toBe(firstPayload.clientOfflineId);
  });

  it("mints a new clientOfflineId only when the CHW explicitly starts a new visit", async () => {
    mockRecordPostnatalContact.mockResolvedValue({
      queued: false,
      record: { postnatal_contact_id: "pnc-5", replayed: false },
    });
    const mod = await import("../../screens/outreach/PostnatalContactScreen");
    mounted = renderScreen();
    act(() => mounted?.root.render(React.createElement(mod.PostnatalContactScreen)));
    await flush();

    await fillMinimalContext(mounted.container);
    await click(mounted.container, "postnatal-submit");
    await flush();
    const [firstPayload] = mockRecordPostnatalContact.mock.calls[0];

    await click(mounted.container, "postnatal-start-new-visit");
    await fillMinimalContext(mounted.container);
    await click(mounted.container, "postnatal-submit");
    await flush();
    const [secondPayload] = mockRecordPostnatalContact.mock.calls[1];

    expect(secondPayload.clientOfflineId).not.toBe(firstPayload.clientOfflineId);
  });

  it("renders the 'nothing visible' history state as a withholding, never as proof of a first visit", async () => {
    mockGetPostnatalContacts.mockResolvedValue([]);
    const mod = await import("../../screens/outreach/PostnatalContactScreen");
    mounted = renderScreen();
    act(() => mounted?.root.render(React.createElement(mod.PostnatalContactScreen)));
    await flush();

    await typeInto(mounted.container, "postnatal-history-mother-cpid", "CP-9");
    await click(mounted.container, "postnatal-load-history");
    await flush();

    expect(byTestId(mounted.container, "postnatal-history-empty")?.textContent).toContain(
      "not proof",
    );
  });

  it("renders a mother's prior contacts, showing an unscreened one as 'Not screened' rather than negative", async () => {
    mockGetPostnatalContacts.mockResolvedValue([
      {
        postnatal_contact_id: "pnc-hist-1",
        mother_cpid: "CP-9",
        contact_timing: "DAY_3",
        contact_setting: "HOME",
        contacted_at: "2026-07-27T08:00:00Z",
        screening_complete: false,
        danger_signs_present: null,
        breastfeeding_status: "NOT_ASSESSED",
        referral_made: false,
        referral_reason: null,
        client_offline_id: "off-1",
        replayed: false,
      },
    ]);
    const mod = await import("../../screens/outreach/PostnatalContactScreen");
    mounted = renderScreen();
    act(() => mounted?.root.render(React.createElement(mod.PostnatalContactScreen)));
    await flush();

    await typeInto(mounted.container, "postnatal-history-mother-cpid", "CP-9");
    await click(mounted.container, "postnatal-load-history");
    await flush();

    const item = byTestId(mounted.container, "postnatal-history-item-pnc-hist-1");
    expect(item?.textContent).toContain("Not screened");
    expect(item?.textContent).not.toMatch(/no danger signs/i);
    expect(item?.textContent).toContain("Not assessed");
  });
});
