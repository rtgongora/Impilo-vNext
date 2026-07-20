// RTC / LiveKit media diagnostic — real browser video-call attempt on the estate.
//
// Runs THREE real LiveKit peers against wss://impilo.mohcc.gov.zw (Traefik→livekit):
//   A = provider (HOST)   — publishes camera + mic
//   B = patient  (on-LAN) — subscribes; the estate-local baseline
//   C = patient  (REMOTE) — connects with iceTransportPolicy:'relay', i.e. it will
//       ONLY use a TURN relay candidate. This faithfully simulates a real off-network
//       / firewalled clinic browser: it cannot use the server's private host
//       candidates, and there is no TURN server, so ICE has nothing to select.
//
// For each peer it captures: LiveKit connection state, RTCPeerConnection ICE states,
// the REMOTE ICE candidates the SFU advertised (host/srflx/relay + address), the
// selected candidate pair, inbound-rtp bytes/frames (did media actually flow?),
// plus every console error and pageerror. Writes JSON + a human report + screenshots.
//
// Usage: node scripts/e2e/rtc-media-diagnostic.mjs <scratchdir-with-rtc-prov.json,rtc-tokB.json,rtc-tokC.json>
import { readFileSync, writeFileSync } from "node:fs";
import { createRequire } from "node:module";

const S = process.argv[2] || ".";
const OUT = `${S}/rtc-media-diagnostic`;
const require = createRequire("/opt/impilo/repos/Impilo-vNext/ui/one-ui-shell/");
const { chromium } = require("playwright-core");

const rd = (f) => JSON.parse(readFileSync(`${S}/${f}`, "utf8")).data;
const host = rd("rtc-prov.json");
const patientB = rd("rtc-tokB.json");
const remoteC = rd("rtc-tokC.json");
const roomUrl = host.roomUrl;
const lkUmd = readFileSync("/opt/impilo/repos/Impilo-vNext/ui/node_modules/livekit-client/dist/livekit-client.umd.js", "utf8");

const report = { roomUrl, when: new Date().toISOString(), peers: {} };
const log = (...a) => console.log(...a);

const browser = await chromium.launch({
  args: [
    "--host-resolver-rules=MAP impilo.mohcc.gov.zw 127.0.0.1",
    "--ignore-certificate-errors",
    "--use-fake-device-for-media-stream",
    "--use-fake-ui-for-media-stream",
  ],
});

async function mkPage(label, { forceRelay = false } = {}) {
  const ctx = await browser.newContext({ ignoreHTTPSErrors: true, permissions: ["camera", "microphone"] });
  const page = await ctx.newPage();
  const errors = [];
  page.on("console", (m) => { if (m.type() === "error") errors.push(m.text().slice(0, 300)); if (/\[LK\]/.test(m.text())) log(m.text()); });
  page.on("pageerror", (e) => errors.push("PAGEERROR " + e.message.slice(0, 300)));
  await page.goto("https://impilo.mohcc.gov.zw/auth/login", { waitUntil: "domcontentloaded" }).catch(() => {});
  await page.addScriptTag({ content: lkUmd });
  await page.evaluate((forceRelay) => {
    window.__pcs = [];
    const O = window.RTCPeerConnection;
    // Wrap the constructor. When forceRelay, override iceTransportPolicy='relay' in the config
    // LiveKit passes — the SDK normally forces 'all', so patching HERE is the only way to make the
    // browser behave like a real firewalled off-network client that can only reach media via TURN.
    const patchCfg = (cfg) => (forceRelay ? Object.assign({}, cfg, { iceTransportPolicy: "relay" }) : cfg);
    window.RTCPeerConnection = function (cfg, ...rest) {
      const pc = new O(patchCfg(cfg), ...rest);
      if (forceRelay && pc.setConfiguration) {           // LiveKit re-sets config post-construction → keep forcing relay
        const orig = pc.setConfiguration.bind(pc);
        pc.setConfiguration = (c) => orig(patchCfg(c));
      }
      window.__pcs.push(pc); return pc;
    };
    window.RTCPeerConnection.prototype = O.prototype;
  }, forceRelay);
  return { ctx, page, errors, label };
}

async function connect(P, token, { publish = false, relayOnly = false } = {}) {
  return await P.page.evaluate(async ({ roomUrl, token, publish, relayOnly, label }) => {
    const LK = window.LivekitClient || window.LiveKitClient || window.livekit;
    const opts = { adaptiveStream: false, dynacast: false };
    if (relayOnly) opts.rtcConfig = { iceTransportPolicy: "relay" };  // force TURN-only (= real remote client)
    const room = new LK.Room(opts);
    window.__room = room;
    const events = [];
    room.on(LK.RoomEvent.ConnectionStateChanged, (s) => events.push("state:" + s));
    room.on(LK.RoomEvent.Disconnected, (r) => events.push("disconnected:" + (r ?? "")));
    room.on(LK.RoomEvent.TrackSubscribed, () => events.push("trackSubscribed"));
    let connectError = null;
    try {
      await Promise.race([
        room.connect(roomUrl, token, { autoSubscribe: true }),
        new Promise((_, rej) => setTimeout(() => rej(new Error("connect() timed out after 20s")), 20000)),
      ]);
      if (publish) { await room.localParticipant.enableCameraAndMicrophone(); events.push("published cam+mic"); }
    } catch (e) { connectError = String(e && e.message || e); }
    return { state: room.state, events, connectError };
  }, { roomUrl, token, publish, relayOnly, label: P.label });
}

async function stats(P) {
  return await P.page.evaluate(async () => {
    const out = { iceStates: [], iceServers: [], iceTransportPolicy: [], remoteCandidates: [], localCandidateTypes: [], selectedPairs: [], inbound: [] };
    const seen = new Set(), locSeen = new Set();
    for (const pc of window.__pcs) {
      out.iceStates.push(pc.iceConnectionState + "/" + pc.connectionState + "/gathering:" + pc.iceGatheringState);
      try {
        const cfg = pc.getConfiguration();
        out.iceTransportPolicy.push(cfg.iceTransportPolicy || "all");
        (cfg.iceServers || []).forEach((s) => { const u = [].concat(s.urls).join(","); if (!out.iceServers.includes(u)) out.iceServers.push(u); });
      } catch (e) { /* ignore */ }
      const rep = await pc.getStats(); const c = {};
      rep.forEach((r) => { if (r.type.endsWith("candidate")) c[r.id] = r; });
      rep.forEach((r) => {
        if (r.type === "local-candidate" && !locSeen.has(r.candidateType)) { locSeen.add(r.candidateType); out.localCandidateTypes.push(r.candidateType); }
        if (r.type === "remote-candidate") { const k = `${r.candidateType} ${r.address || r.ip}:${r.port}/${r.protocol}`; if (!seen.has(k)) { seen.add(k); out.remoteCandidates.push(k); } }
        if (r.type === "candidate-pair" && (r.nominated || r.state === "succeeded")) {
          const l = c[r.localCandidateId] || {}, rem = c[r.remoteCandidateId] || {};
          out.selectedPairs.push({ state: r.state, nominated: !!r.nominated, local: `${l.candidateType} ${l.address || l.ip}:${l.port}/${l.protocol}`, remote: `${rem.candidateType} ${rem.address || rem.ip}:${rem.port}/${rem.protocol}`, bytesRecv: r.bytesReceived });
        }
        if (r.type === "inbound-rtp" && r.kind) out.inbound.push({ kind: r.kind, bytes: r.bytesReceived, frames: r.framesDecoded });
      });
    }
    return out;
  });
}

const A = await mkPage("A-provider-HOST");
const B = await mkPage("B-patient-onLAN");
const C = await mkPage("C-patient-REMOTE-relayOnly", { forceRelay: true });
try {
  log("=== A (provider/HOST) connect + publish ===");
  report.peers.A = { role: "provider HOST (publish)", connect: await connect(A, host.accessToken, { publish: true }) };
  await A.page.waitForTimeout(1500);

  log("=== B (patient, on-LAN, normal ICE) connect + subscribe ===");
  report.peers.B = { role: "patient on-LAN (baseline)", connect: await connect(B, patientB.accessToken, {}) };

  log("=== C (patient, REMOTE, relay-only ICE = firewalled off-network client) connect ===");
  report.peers.C = { role: "patient REMOTE relay-only (simulates off-network)", connect: await connect(C, remoteC.accessToken, { relayOnly: true }) };

  await B.page.waitForTimeout(9000);   // let ICE settle + media flow

  report.peers.A.stats = await stats(A); report.peers.A.errors = A.errors;
  report.peers.B.stats = await stats(B); report.peers.B.errors = B.errors;
  report.peers.C.stats = await stats(C); report.peers.C.errors = C.errors;

  for (const p of ["A", "B", "C"]) { await ({ A, B, C })[p].page.screenshot({ path: `${OUT}-${p}.png` }).catch(() => {}); }
} catch (e) {
  report.fatal = String(e && e.message || e);
} finally {
  await browser.close();
}

writeFileSync(`${OUT}.json`, JSON.stringify(report, null, 2));

// ---- human report ----
const L = [];
L.push("RTC / LiveKit media diagnostic — " + report.when);
L.push("room: " + roomUrl + "  (Traefik → livekit:7880, TLS)\n");
for (const [k, p] of Object.entries(report.peers)) {
  L.push(`### ${k}: ${p.role}`);
  L.push(`  livekit state:     ${p.connect.state}`);
  if (p.connect.connectError) L.push(`  CONNECT ERROR:     ${p.connect.connectError}`);
  L.push(`  room events:       ${p.connect.events.join(", ") || "(none)"}`);
  L.push(`  ICE states:        ${(p.stats?.iceStates || []).join(" | ") || "(no PC)"}`);
  L.push(`  ICE servers given:  ${(p.stats?.iceServers || []).join(" ; ") || "(none)"}`);
  L.push(`  iceTransportPolicy: ${[...new Set(p.stats?.iceTransportPolicy || [])].join(",") || "?"}`);
  const hasTurn = (p.stats?.iceServers || []).some((s) => /turns?:/i.test(s));
  const hasRelay = (p.stats?.remoteCandidates || []).some((c) => /^relay /.test(c)) || (p.stats?.localCandidateTypes || []).includes("relay");
  L.push(`  TURN configured:    ${hasTurn ? "yes" : "NO"}   relay candidate present: ${hasRelay ? "yes" : "NO"}`);
  L.push(`  remote candidates the SFU advertised:`);
  (p.stats?.remoteCandidates || []).forEach((c) => L.push(`     - ${c}`));
  if (!(p.stats?.remoteCandidates || []).length) L.push("     (none)");
  const sel = (p.stats?.selectedPairs || []).filter((x) => x.nominated);
  L.push(`  selected pair:     ${sel.length ? JSON.stringify(sel[0]) : "(NONE — no working path)"}`);
  const vid = (p.stats?.inbound || []).find((i) => i.kind === "video");
  const aud = (p.stats?.inbound || []).find((i) => i.kind === "audio");
  L.push(`  media received:    video=${vid ? vid.bytes + "B/" + vid.frames + "frames" : "0"}  audio=${aud ? aud.bytes + "B" : "0"}`);
  if ((p.errors || []).length) { L.push("  console/page errors:"); p.errors.slice(0, 6).forEach((e) => L.push("     ! " + e)); }
  L.push("");
}
const txt = L.join("\n");
writeFileSync(`${OUT}.txt`, txt);
log("\n" + txt);
log(`\nWrote ${OUT}.json / .txt and per-peer screenshots ${OUT}-A/B/C.png`);
