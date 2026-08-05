import { describe, expect, it } from "vitest";
import { resolveHubSections, type ResolvedHubSection } from "./hubUi";
import { HUB_FALLBACK_SECTIONS } from "./hubCatalogue.generated";

const CLINICIAN = ["CLINICIAN"];
const ADMIN = ["SYSTEM_ADMIN"];

const FALLBACK: ResolvedHubSection[] = [
  { id: "admin", title: "Administration", web_path: "/admin", requiredRole: "ADMIN" },
  { id: "registry", title: "Registry", web_path: "/registry" },
];

describe("resolveHubSections", () => {
  it("uses the server's sections when it answered", () => {
    const remote: ResolvedHubSection[] = [
      { id: "registry", title: "Registry", web_path: "/registry" },
    ];

    expect(resolveHubSections(remote, FALLBACK, CLINICIAN)).toEqual({
      sections: remote,
      isOfflineLayout: false,
    });
  });

  it("keeps an empty answer empty instead of substituting the local catalogue", () => {
    // The BFF withholds sections whose routes the caller's roles refuse, so [] means "nothing in
    // this hub is open to you". The old `remote.length > 0 ? remote : FALLBACK_SECTIONS` turned
    // that into the FULL list — handing every withheld link back to the one caller it was
    // withheld from.
    const resolved = resolveHubSections([], FALLBACK, CLINICIAN);

    expect(resolved.sections).toEqual([]);
    expect(resolved.isOfflineLayout).toBe(false);
  });

  it("does NOT re-filter the server's sections against local roles", () => {
    // The server evaluated the real token. This client's idea of its roles is not authoritative
    // and must not subtract from an answer that has already been filtered properly.
    const remote: ResolvedHubSection[] = [
      { id: "admin", title: "Administration", web_path: "/admin", requiredRole: "ADMIN" },
    ];

    expect(resolveHubSections(remote, FALLBACK, CLINICIAN).sections).toHaveLength(1);
  });

  describe("offline", () => {
    it("falls back only when there was no answer at all", () => {
      const resolved = resolveHubSections(undefined, FALLBACK, ADMIN);

      expect(resolved.isOfflineLayout).toBe(true);
      expect(resolved.sections.map((s) => s.id)).toEqual(["admin", "registry"]);
    });

    it("withholds fallback sections the roles cannot open", () => {
      // The regression this guards: offline there is no BFF to filter, so the bundled layout
      // used to offer Administration to a nurse. Tapping it leaves the app, opens a browser and
      // lands on /home.
      const resolved = resolveHubSections(undefined, FALLBACK, CLINICIAN);

      expect(resolved.isOfflineLayout).toBe(true);
      expect(resolved.sections.map((s) => s.id)).toEqual(["registry"]);
    });

    it("withholds everything when the caller holds no roles", () => {
      const resolved = resolveHubSections(undefined, FALLBACK, []);

      expect(resolved.sections.map((s) => s.id)).toEqual(["registry"]);
    });
  });

  it("drops malformed sections from a real answer", () => {
    const remote = [
      { id: "ok", title: "Fine", web_path: "/registry" },
      { id: "", title: "No id", web_path: "/x" },
      { id: "no-title", title: "", web_path: "/y" },
    ] as ResolvedHubSection[];

    expect(resolveHubSections(remote, FALLBACK, CLINICIAN).sections).toEqual([
      { id: "ok", title: "Fine", web_path: "/registry" },
    ]);
  });
});

describe("the real generated offline catalogue", () => {
  it("hands a clinician only what their roles open", () => {
    // Against the shipped artifact, not a fixture — so this goes red if generation degrades.
    const offline = resolveHubSections(
      undefined,
      HUB_FALLBACK_SECTIONS["admin-registry"],
      CLINICIAN,
    );

    expect(offline.sections.map((s) => s.web_path)).toEqual(["/registry"]);
  });

  it("still hands an admin the whole hub", () => {
    const offline = resolveHubSections(undefined, HUB_FALLBACK_SECTIONS["admin-registry"], ADMIN);

    expect(offline.sections).toHaveLength(HUB_FALLBACK_SECTIONS["admin-registry"].length);
  });

  it("keeps the corrected coverage section reachable for clinicians", () => {
    const offline = resolveHubSections(
      undefined,
      HUB_FALLBACK_SECTIONS["professional-channels"],
      CLINICIAN,
    );

    // /ruvimbo/provider (CLINICAL) survives; /omnichannel (ADMIN) does not.
    expect(offline.sections.map((s) => s.web_path)).toContain("/ruvimbo/provider");
    expect(offline.sections.map((s) => s.web_path)).not.toContain("/omnichannel");
  });

  it("carries a role requirement on the sections that have one", () => {
    // Guards the generation step itself: if requiredRole stopped being resolved, every filter
    // above would admit everything and still pass.
    const guarded = Object.values(HUB_FALLBACK_SECTIONS)
      .flat()
      .filter((s) => s.requiredRole);

    expect(guarded.length).toBeGreaterThan(0);
  });
});
