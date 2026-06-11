import { NextRequest } from "next/server";
import { describe, expect, it } from "vitest";
import { publicOrigin } from "./middleware";

describe("publicOrigin", () => {
  it("prefers x-forwarded-host over internal nextUrl origin", () => {
    const request = new NextRequest("http://127.0.0.1:3001/", {
      headers: {
        host: "localhost:3000",
        "x-forwarded-host": "localhost:3000",
        "x-forwarded-proto": "http",
      },
    });
    expect(publicOrigin(request)).toBe("http://localhost:3000");
  });

  it("falls back to host header when x-forwarded-host is absent", () => {
    const request = new NextRequest("http://127.0.0.1:3001/", {
      headers: { host: "localhost:3000" },
    });
    expect(publicOrigin(request)).toBe("http://localhost:3000");
  });
});
