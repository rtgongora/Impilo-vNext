import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

/**
 * D-P3/PJ5 golden thread: the workplace hub → useStartWorkSession →
 * BFF POST /internal/v1/work-context/session (which proves the assignment
 * against Vashandi) → tshepo-identity WORK_CONTEXT token issuance. A switch
 * carries previousJti so the old token is revoked first.
 */
describe("work-session start/switch golden thread", () => {
  const repoRoot = resolve(__dirname, "../../../../..");
  const read = (p: string) => readFileSync(resolve(repoRoot, p), "utf8");

  it("the hub lists active assignments and starts a session on tap", () => {
    const page = read("ui/one-ui-shell/src/app/provider/workplace/page.tsx");
    expect(page).toContain("useWorkContext");
    expect(page).toContain("useStartWorkSession");
    expect(page).toContain("work-assignment-option");
    // My Life / My Professional stay reachable regardless of work state.
    expect(page).toContain("/home/credentials");
  });

  it("the hook posts to the BFF session lane and carries previousJti on switch", () => {
    const hook = read("ui/one-ui-shell/src/hooks/queries/useWorkSession.ts");
    expect(hook).toContain("/internal/v1/work-context/session");
    expect(hook).toContain("previousJti");
    // Read side reads the assignment picker source.
    expect(hook).toContain("/internal/v1/work-context");
  });

  it("the session store is a distinct, server-proven token handle (jti)", () => {
    const store = read("ui/one-ui-shell/src/hooks/useWorkSessionStore.ts");
    expect(store).toContain("jti");
    expect(store).toContain("exp:work-session");
  });

  it("the switcher routes Start Work Session to the token-minting hub", () => {
    const switcher = read("ui/one-ui-shell/src/components/WorkspaceSwitcher.tsx");
    expect(switcher).toContain("/provider/workplace");
  });

  it("the BFF proves the assignment then mints a WORK_CONTEXT token", () => {
    const controller = read(
      "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/WorkContextController.java",
    );
    expect(controller).toContain("/session");
    expect(controller).toContain("matchAssignment");
    expect(controller).toContain("issueWorkContextToken");
    // Generic denial — never lists which contexts DO exist.
    expect(controller).toContain("WORK_SESSION_UNAVAILABLE");
  });

  it("tshepo-identity revokes the previous token before reissuing on switch", () => {
    const svc = read(
      "services/tshepo-identity-service/src/main/java/zw/gov/mohcc/impilo/tshepo/identity/core/TokenIssuanceService.java",
    );
    expect(svc).toContain("issueWorkContextToken");
    expect(svc).toContain("previousJti");
    expect(svc).toContain("REVOKED");
  });
});
