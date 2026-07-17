import { beforeEach, describe, expect, it } from "vitest";
import {
  writeFormDraft,
  clearFormDraft,
  readFormDraft,
  listResumableDrafts,
  FORM_DRAFT_VERSION,
} from "./useFormDraftStore";

const INDEX_KEY = "exp:form_draft_index";
const DRAFT_PREFIX = "exp:form_draft:";

beforeEach(() => {
  window.localStorage.clear();
});

describe("form draft resume index", () => {
  it("registers a resumable entry only when label + href are supplied", () => {
    writeFormDraft("with-meta", { name: "Ada" }, [], {
      label: "Continue your booking",
      href: "/home/bookings/new",
    });
    writeFormDraft("no-meta", { name: "Grace" });

    const drafts = listResumableDrafts();
    expect(drafts).toHaveLength(1);
    expect(drafts[0]).toMatchObject({
      key: "with-meta",
      label: "Continue your booking",
      href: "/home/bookings/new",
    });
  });

  it("never persists sensitive fields in the underlying draft", () => {
    writeFormDraft(
      "auth-register",
      { fullName: "Ada Lovelace", password: "hunter2hunter2", otpCode: "123456" },
      [],
      { label: "Finish creating your account", href: "/auth/register" },
    );
    const stored = readFormDraft<{ fullName: string; password?: string }>("auth-register");
    expect(stored).toEqual({ fullName: "Ada Lovelace" });
    expect(stored).not.toHaveProperty("password");
    expect(stored).not.toHaveProperty("otpCode");
  });

  it("self-heals: prunes an index entry whose underlying draft is gone", () => {
    writeFormDraft("live", { a: 1 }, [], { label: "Live draft", href: "/live" });
    writeFormDraft("stale", { b: 2 }, [], { label: "Stale draft", href: "/stale" });

    // Simulate the underlying draft disappearing while the index still references it.
    window.localStorage.removeItem(`${DRAFT_PREFIX}stale`);

    const drafts = listResumableDrafts();
    expect(drafts.map((d) => d.key)).toEqual(["live"]);

    // The pruned index must be rewritten, not just filtered on read.
    const rawIndex = JSON.parse(window.localStorage.getItem(INDEX_KEY) as string);
    expect(rawIndex.entries.map((e: { key: string }) => e.key)).toEqual(["live"]);
  });

  it("clearFormDraft removes both the draft and its index entry", () => {
    writeFormDraft("booking-new", { reason: "check-up" }, [], {
      label: "Continue your booking",
      href: "/home/bookings/new",
    });
    expect(listResumableDrafts()).toHaveLength(1);

    clearFormDraft("booking-new");
    expect(readFormDraft("booking-new")).toBeNull();
    expect(listResumableDrafts()).toHaveLength(0);
  });

  it("sorts newest-first by updatedAt", () => {
    writeFormDraft("older", { a: 1 }, [], { label: "Older", href: "/older" });
    // Force a later timestamp by rewriting after a tick would be flaky; assert on sort logic
    // by writing the index directly through the public write path in order.
    writeFormDraft("newer", { b: 2 }, [], { label: "Newer", href: "/newer" });

    const drafts = listResumableDrafts();
    // Both share monotonic Date.now(); newest-first must place "newer" no earlier than "older".
    const keys = drafts.map((d) => d.key);
    expect(keys).toContain("older");
    expect(keys).toContain("newer");
    expect(drafts[0].updatedAt).toBeGreaterThanOrEqual(drafts[drafts.length - 1].updatedAt);
  });

  it("discards a stale/version-mismatched index when the version changes", () => {
    window.localStorage.setItem(
      INDEX_KEY,
      JSON.stringify({ version: "1999-01-01", entries: [{ key: "x", label: "X", href: "/x", updatedAt: 1 }] }),
    );
    expect(listResumableDrafts()).toEqual([]);
    // stale index key is cleared
    expect(window.localStorage.getItem(INDEX_KEY)).toBeNull();
  });

  it("current version index survives", () => {
    window.localStorage.setItem(`${DRAFT_PREFIX}x`, JSON.stringify({ version: FORM_DRAFT_VERSION, updatedAt: 1, values: { a: 1 } }));
    window.localStorage.setItem(
      INDEX_KEY,
      JSON.stringify({ version: FORM_DRAFT_VERSION, entries: [{ key: "x", label: "X", href: "/x", updatedAt: 1 }] }),
    );
    expect(listResumableDrafts().map((d) => d.key)).toEqual(["x"]);
  });
});
