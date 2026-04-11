/** @type {import('next').NextConfig} */
const nextConfig = {
  async rewrites() {
    const bff = process.env.NEXT_PUBLIC_BFF_URL || "http://localhost:8160";
    return [{ source: "/api/bff/:path*", destination: `${bff}/:path*` }];
  },
};

export default nextConfig;
