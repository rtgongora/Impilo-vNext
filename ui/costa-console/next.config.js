/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
  transpilePackages: ["shared-ui"],
  output: "standalone",
  async rewrites() {
    return [
      {
        source: "/api/:path*",
        destination: `${process.env.NEXT_PUBLIC_API_GATEWAY_URL || "http://localhost:10000"}/api/:path*`,
      },
      {
        source: "/costa/:path*",
        destination: `${process.env.NEXT_PUBLIC_API_GATEWAY_URL || "http://localhost:10000"}/costa/:path*`,
      },
    ];
  },
};

module.exports = nextConfig;
