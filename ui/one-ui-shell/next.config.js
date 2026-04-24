/** @type {import('next').NextConfig} */

/**
 * Gateway default matches production-shaped routing (Envoy on :10000).
 * For README-style local dev (TSHEPO on the host, no Envoy), set:
 *   IMPILO_LOCAL_TSHEPO_URL=http://localhost:8079
 * so `/api/v1/authorize` is proxied to TSHEPO's `/v1/authorize` (path strip).
 * Other `/api/*` traffic still uses NEXT_PUBLIC_API_GATEWAY_URL or localhost:10000.
 */
const defaultGateway = "http://localhost:10000";

const localTshepoBase =
  process.env.IMPILO_LOCAL_TSHEPO_URL || "http://localhost:8079";

const nextConfig = {
  reactStrictMode: true,
  transpilePackages: ["shared-ui"],
  output: "standalone",
  async rewrites() {
    const gateway =
      process.env.NEXT_PUBLIC_API_GATEWAY_URL || defaultGateway;
    const tshepo = localTshepoBase.replace(/\/$/, "");

    return [
      {
        source: "/api/v1/authorize",
        destination: `${tshepo}/v1/authorize`,
      },
      // Only proxy /api/v1/* to the gateway. Do NOT use "/api/:path*" here: it
      // would steal Next.js Route Handlers under /api/auth/* (OIDC login/token)
      // and send them to Envoy, producing broken/non-HTML responses in the browser.
      {
        source: "/api/v1/:path*",
        destination: `${gateway}/api/v1/:path*`,
      },
    ];
  },
};

module.exports = nextConfig;
