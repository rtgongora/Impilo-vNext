/** @type {import('next').NextConfig} */
const nextConfig = {
  output: 'standalone',
  distDir: '.next-build',
  async rewrites() {
    const gateway = process.env.NEXT_PUBLIC_API_GATEWAY_URL || 'http://localhost:10000';
    const bff = process.env.NEXT_PUBLIC_BFF_URL || 'http://localhost:8160';
    return [
      {
        source: '/internal/:path*',
        destination: `${bff}/internal/:path*`,
      },
      {
        source: '/api/:path*',
        destination: `${gateway}/api/:path*`,
      },
    ];
  },
};

export default nextConfig;
