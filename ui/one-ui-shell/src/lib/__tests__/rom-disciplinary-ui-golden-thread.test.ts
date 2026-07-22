import { readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";

import { ROUTES } from "@/lib/routes";

/**
 * Golden thread for the ROM disciplinary UI. Two surfaces make the disciplinary journey usable:
 *   - the respondent's "complaints involving me" self-service page, and
 *   - the officer's disciplinary console (look up a provider, open a proceeding, advance a case,
 *     record a determination) with the RECUSAL_REQUIRED own-proceeding guard trapped inline.
 *
 * The respondent page resolves the caller's OWN record from the trust context, so it must never
 * send a providerId query param.
 */
const SRC = join(process.cwd(), "src");
const read = (p: string) => readFileSync(join(SRC, p), "utf8");

describe("ROM disciplinary UI", () => {
  const complaintsPage = read("app/professional/regulatory/complaints/page.tsx");
  const disciplinaryPage = read("app/work/regulators/[regulatorId]/disciplinary/page.tsx");

  it("registers the respondent complaints route", () => {
    const paths = ROUTES.map((r) => r.path);
    expect(paths).toContain("/professional/regulatory/complaints");
  });

  it("reads the caller's own complaints with no providerId query param", () => {
    expect(complaintsPage).toContain("/internal/v1/me/regulatory/complaints");
    expect(complaintsPage).not.toContain("providerId=");
  });

  it("no longer renders the ScopedAdministrationSurface stub", () => {
    expect(disciplinaryPage).not.toContain("ScopedAdministrationSurface");
  });

  it("looks up a provider's disciplinary cases and opens/determines proceedings", () => {
    expect(disciplinaryPage).toContain("/internal/v1/regulatory/console/disciplinary/provider/");
    expect(disciplinaryPage).toContain('apiClient.post<unknown>("/internal/v1/regulatory/console/disciplinary"');
    expect(disciplinaryPage).toContain("/determine");
  });

  it("traps the RECUSAL_REQUIRED own-proceeding guard", () => {
    expect(disciplinaryPage).toContain("RECUSAL_REQUIRED");
  });
});
