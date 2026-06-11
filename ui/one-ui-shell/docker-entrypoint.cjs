/**
 * Production entrypoint: serve Next standalone on 3001 and expose port 3000
 * with WebSocket proxy to the Experience BFF (/ws/telemedicine/*).
 */
const http = require("http");
const { fork } = require("child_process");
const httpProxy = require("http-proxy");

const PUBLIC_PORT = parseInt(process.env.PORT || "3000", 10);
const INTERNAL_PORT = parseInt(process.env.INTERNAL_PORT || "3001", 10);
const BFF_TARGET = process.env.BFF_URL || "http://experience-bff:8160";
const GATEWAY_TARGET =
  process.env.API_GATEWAY_URL || "http://envoy:10000";
const BFF_WS_TARGET = BFF_TARGET.replace(/^http/i, "ws");

const proxy = httpProxy.createProxyServer({ ws: true, changeOrigin: true });

/** Next runs on INTERNAL_PORT; rewrite absolute redirects so clients stay on PUBLIC_PORT. */
function rewritePublicLocation(location, req) {
  if (!location) return location;
  const loc = Array.isArray(location) ? location[0] : location;
  if (typeof loc !== "string" || loc.startsWith("/")) return location;
  const publicHost = req.headers.host || `localhost:${PUBLIC_PORT}`;
  const publicBase = `http://${publicHost}`;
  const rewritten = loc
    .replace(`http://127.0.0.1:${INTERNAL_PORT}`, publicBase)
    .replace(`http://localhost:${INTERNAL_PORT}`, publicBase)
    .replace(`https://127.0.0.1:${INTERNAL_PORT}`, publicBase)
    .replace(`https://localhost:${INTERNAL_PORT}`, publicBase);
  return rewritten === loc ? location : rewritten;
}

proxy.on("proxyRes", (proxyRes, req) => {
  const location = proxyRes.headers.location;
  if (!location) return;
  const rewritten = rewritePublicLocation(location, req);
  if (rewritten !== location) {
    proxyRes.headers.location = rewritten;
  }
});

proxy.on("error", (err, _req, res) => {
  console.error("[ws-proxy]", err.message);
  if (res && !res.headersSent && typeof res.writeHead === "function") {
    res.writeHead(502, { "Content-Type": "text/plain" });
    res.end("Upstream unavailable");
  }
});

const child = fork("server.js", [], {
  cwd: __dirname,
  env: {
    ...process.env,
    PORT: String(INTERNAL_PORT),
    HOSTNAME: "127.0.0.1",
  },
  stdio: "inherit",
});

child.on("exit", (code) => {
  console.error(`[next] server exited with code ${code ?? "unknown"}`);
  process.exit(code ?? 1);
});

function forwardToNext(req, res) {
  const publicHost = req.headers.host || `localhost:${PUBLIC_PORT}`;
  proxy.web(req, res, {
    target: `http://127.0.0.1:${INTERNAL_PORT}`,
    xfwd: true,
    headers: {
      host: publicHost,
      "x-forwarded-host": publicHost,
      "x-forwarded-port": String(PUBLIC_PORT),
      "x-forwarded-proto": req.headers["x-forwarded-proto"] || "http",
    },
  });
}

function forwardToBff(req, res) {
  proxy.web(req, res, { target: BFF_TARGET, changeOrigin: true });
}

function forwardToGateway(req, res) {
  const publicHost = req.headers.host || `localhost:${PUBLIC_PORT}`;
  proxy.web(req, res, {
    target: GATEWAY_TARGET,
    changeOrigin: true,
    xfwd: true,
    headers: {
      host: publicHost,
      "x-forwarded-host": publicHost,
      "x-forwarded-port": String(PUBLIC_PORT),
      "x-forwarded-proto": req.headers["x-forwarded-proto"] || "http",
    },
  });
}

const server = http.createServer((req, res) => {
  const url = req.url || "";
  if (url.startsWith("/ws/")) {
    forwardToBff(req, res);
    return;
  }
  // Experience BFF owns /internal/v1/* — route directly (Envoy ext_authz blocks login + JWT APIs).
  if (url.startsWith("/internal/")) {
    forwardToBff(req, res);
    return;
  }
  if (url.startsWith("/api/")) {
    forwardToGateway(req, res);
    return;
  }
  forwardToNext(req, res);
});

server.on("upgrade", (req, socket, head) => {
  const url = req.url || "";
  if (url.startsWith("/ws/")) {
    proxy.ws(req, socket, head, { target: BFF_WS_TARGET });
    return;
  }
  proxy.ws(req, socket, head, { target: `ws://127.0.0.1:${INTERNAL_PORT}` });
});

server.listen(PUBLIC_PORT, "0.0.0.0", () => {
  console.log(
    `[impilo-ui] listening on 0.0.0.0:${PUBLIC_PORT} (next :${INTERNAL_PORT}, bff ${BFF_TARGET}, gateway ${GATEWAY_TARGET})`,
  );
});
