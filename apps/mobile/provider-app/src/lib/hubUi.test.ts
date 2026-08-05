import { describe, expect, it } from "vitest";
import { resolveHubSections } from "./hubUi";

type Section = { id: string; title: string; web_path: string };

const FALLBACK: Section[] = [
  { id: "admin", title: "Administration", web_path: "/admin" },
  { id: "registry", title: "Registry", web_path: "/registry" },
];

describe("resolveHubSections", () => {
  it("uses the server's sections when it answered", () => {
    const remote: Section[] = [{ id: "registry", title: "Registry", web_path: "/registry" }];

    expect(resolveHubSections(remote, FALLBACK)).toEqual({
      sections: remote,
      isOfflineLayout: false,
    });
  });

  it("keeps an empty answer empty instead of substituting the local catalogue", () => {
    // The regression this guards. The experience BFF withholds sections whose routes the
    // caller's roles refuse, so [] means "nothing in this hub is open to you". The old
    // `remote.length > 0 ? remote : FALLBACK_SECTIONS` turned that into the FULL unfiltered
    // list — handing every withheld link straight back to the one caller it was withheld from.
    const resolved = resolveHubSections<Section>([], FALLBACK);

    expect(resolved.sections).toEqual([]);
    expect(resolved.isOfflineLayout).toBe(false);
    expect(resolved.sections).not.toContainEqual(
      expect.objectContaining({ web_path: "/admin" }),
    );
  });

  it("falls back only when there was no answer at all", () => {
    expect(resolveHubSections<Section>(undefined, FALLBACK)).toEqual({
      sections: FALLBACK,
      isOfflineLayout: true,
    });
  });

  it("drops malformed sections from a real answer", () => {
    const remote = [
      { id: "ok", title: "Fine", web_path: "/registry" },
      { id: "", title: "No id", web_path: "/x" },
      { id: "no-title", title: "", web_path: "/y" },
    ] as Section[];

    expect(resolveHubSections(remote, FALLBACK).sections).toEqual([
      { id: "ok", title: "Fine", web_path: "/registry" },
    ]);
  });
});
