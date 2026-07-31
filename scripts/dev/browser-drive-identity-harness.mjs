/**
 * Browser-drive identity harness — LOCAL DEVELOPMENT ONLY.
 *
 * ── What it is for ──────────────────────────────────────────────────────────
 * Driving a clinical surface through a real browser needs a signed-in clinician
 * whose professional identity resolves. AuthGuardProvider builds that identity
 * from four reads:
 *
 *   GET /internal/v1/identity/linked-ids
 *   GET /internal/v1/identity/affiliations
 *   GET /internal/v1/workforce-governance/assignments/search
 *   GET /internal/v1/session/experience      (authoritative for route visibility)
 *
 * Those are owned by VITO, the council registry and the workforce-governance
 * service. A local box that is only running the BFF plus the service under test
 * has none of them, and the resolver fails closed — the clinician resolves to
 * citizen-only and every /clinical deep link is bounced to /home before the page
 * under test renders. That is correct production behaviour and must not be
 * changed; this harness supplies the missing identity so the surface can be seen.
 *
 * ── What it is NOT ──────────────────────────────────────────────────────────
 * It is not a mock of the domain under test. It answers those four identity
 * paths and nothing else. Every other path — every emergency, ED and mental
 * health read and write — is proxied unmodified to the real experience-bff, so
 * the surfaces show their genuine states, including their genuine failures.
 *
 * It is never deployed, never imported by application code, and never used by
 * CI. It binds to localhost only.
 *
 * ── Usage ───────────────────────────────────────────────────────────────────
 *   node scripts/dev/browser-drive-identity-harness.mjs
 *   # then point the dev server's gateway rewrite at it:
 *   cd ui/one-ui-shell && API_GATEWAY_URL=http://localhost:8161 npx next dev --port 3007
 *
 * Environment: UPSTREAM (default http://localhost:8160), PORT (default 8161),
 * DRIVE_PROVIDER_ID, DRIVE_FACILITY_ID, DRIVE_HEALTH_ID.
 */

import http from "node:http";

const UPSTREAM = process.env.UPSTREAM || "http://localhost:8160";
const PORT = Number(process.env.PORT || 8161);
const PROVIDER_ID = process.env.DRIVE_PROVIDER_ID || "PRV-DRIVE-001";
const FACILITY_ID = process.env.DRIVE_FACILITY_ID || "11111111-1111-1111-1111-111111111111";
const HEALTH_ID = process.env.DRIVE_HEALTH_ID || "local-drive-provider-001";

const FIXTURES = {
  "/internal/v1/identity/linked-ids": {
    data: {
      id: HEALTH_ID,
      type: "linked-ids",
      attributes: {
        providerId: PROVIDER_ID,
        providerStatus: "ACTIVE",
        licenceValid: true,
        staffId: "STF-DRIVE-001",
        facilityId: FACILITY_ID,
      },
    },
  },
  "/internal/v1/identity/affiliations": {
    data: [
      {
        facilityId: FACILITY_ID,
        facilityName: "Harare Central Hospital",
        status: "ACTIVE",
        role: "CLINICIAN",
      },
    ],
  },
  // The guard waits while this contract loads and treats it as authoritative. Without it the
  // guard falls back to the client-side citizen heuristic, which is briefly true on every full
  // page load — so a deep link into /clinical loses the race and lands on /home.
  "/internal/v1/session/experience": {
    data: {
      id: HEALTH_ID,
      type: "session-experience",
      attributes: {
        authenticated: true,
        loginMethod: "provider_id",
        identity: {
          healthId: HEALTH_ID,
          providerWorkerId: PROVIDER_ID,
          identityType: "provider_worker",
          identityAssuranceLevel: "VERIFIED",
        },
        tabs: {
          personal: { visible: true, label: "My Life", route: "/home" },
          professional: { visible: true, label: "My Professional", route: "/professional" },
          work: { visible: true, default: true, label: "Work", route: "/clinical" },
        },
        providerWorkerStatus: "active",
        activeWorkAssignments: [],
        availableContexts: ["facility_clinical"],
        selectedContext: "facility_clinical",
        visibleWorkspaces: ["ws-ed-001"],
        visibleActions: [],
        blockedActions: [],
        roleTemplates: ["CLINICIAN"],
        policyMetadata: {
          contractVersion: "1.4.0",
          opaPackages: [],
          enforcement: "bff_and_opa",
        },
        nompiloContext: {
          activeTab: "work",
          suggestedPrompts: [],
          allowedActionDomains: ["clinical"],
        },
        resolutionActions: [],
        requiresContextChooser: false,
        facilityModeAvailable: true,
        facilityModeActive: true,
        defaultRoute: "/clinical",
        visibleManagementWorkspaces: [],
        blockedManagementWorkspaces: [],
        visibleVashandiWorkspaces: [],
        blockedVashandiWorkspaces: [],
      },
    },
  },
  "/internal/v1/workforce-governance/assignments/search": {
    data: [
      {
        assignmentId: "WA-DRIVE-001",
        subjectId: PROVIDER_ID,
        subjectType: "provider_worker",
        providerId: PROVIDER_ID,
        organisationId: "ORG-MOHCC",
        facilityId: FACILITY_ID,
        facilityName: "Harare Central Hospital",
        contextType: "facility_clinical",
        workspaceId: "ws-ed-001",
        roleCode: "CLINICIAN",
        roleName: "Emergency Clinician",
        assignmentType: "facility_assignment",
        status: "ACTIVE",
        assumptionOfDutyStatus: "confirmed",
        startupRoute: "/clinical",
      },
    ],
  },
};

function fixtureFor(url) {
  return FIXTURES[url.split("?")[0]] ?? null;
}

const server = http.createServer((req, res) => {
  const fixture = fixtureFor(req.url);
  process.stdout.write(`${req.method} ${req.url} ${fixture ? "[identity-fixture]" : "[proxy]"}\n`);

  if (fixture) {
    const body = JSON.stringify(fixture);
    res.writeHead(200, {
      "content-type": "application/json",
      "content-length": Buffer.byteLength(body),
      "x-impilo-drive-harness": "identity-fixture",
    });
    res.end(body);
    return;
  }

  const upstream = new URL(req.url, UPSTREAM);
  const proxied = http.request(
    {
      hostname: upstream.hostname,
      port: upstream.port,
      path: upstream.pathname + upstream.search,
      method: req.method,
      headers: { ...req.headers, host: upstream.host },
    },
    (upRes) => {
      process.stdout.write(`  <- ${upRes.statusCode} ${req.url}\n`);
      res.writeHead(upRes.statusCode || 502, upRes.headers);
      upRes.pipe(res);
    },
  );
  proxied.on("error", (err) => {
    const body = JSON.stringify({
      error: "upstream_unreachable",
      message: `The experience BFF at ${UPSTREAM} could not be reached: ${err.message}`,
      meta: {},
    });
    res.writeHead(502, { "content-type": "application/json" });
    res.end(body);
  });
  req.pipe(proxied);
});

server.listen(PORT, "127.0.0.1", () => {
  process.stdout.write(`browser-drive identity harness on 127.0.0.1:${PORT} -> ${UPSTREAM}\n`);
});
