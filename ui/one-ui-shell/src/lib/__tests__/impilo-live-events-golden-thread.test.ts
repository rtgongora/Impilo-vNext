import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

describe("impilo-live-events golden thread", () => {
  const repoRoot = resolve(__dirname, "../../../../..");

  it("discover route wires live BFF hooks", () => {
    const page = readFileSync(resolve(repoRoot, "ui/one-ui-shell/src/app/live/discover/page.tsx"), "utf8");
    expect(page).toContain("useLiveDiscover");
    expect(page).toContain("useLiveRegister");
  });

  it("room route wires join and media hooks", () => {
    const room = readFileSync(resolve(repoRoot, "ui/one-ui-shell/src/components/live/LiveRoom.tsx"), "utf8");
    expect(room).toContain("useLiveJoinRoom");
    expect(room).toContain("useLiveRoomToken");
  });

  it("replay route wires replay BFF endpoint", () => {
    const replay = readFileSync(resolve(repoRoot, "ui/one-ui-shell/src/app/live/event/[eventId]/replay/page.tsx"), "utf8");
    expect(replay).toContain("useLiveReplay");
  });

  it("client calls BFF live proxy", () => {
    const client = readFileSync(resolve(repoRoot, "ui/one-ui-shell/src/lib/live.ts"), "utf8");
    expect(client).toContain("/internal/v1/live");
  });

  it("BFF LiveController exposes journey endpoints", () => {
    const controller = readFileSync(
      resolve(repoRoot, "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/LiveController.java"),
      "utf8",
    );
    expect(controller).toContain("/internal/v1/live");
    expect(controller).toContain("/registrations/events/{eventId}");
    expect(controller).toContain("/room/{eventId}/replay");
  });
});
