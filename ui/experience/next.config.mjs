/** @type {import('next').NextConfig} */
const nextConfig = {
  output: 'standalone',
  async rewrites() {
    const gateway = process.env.NEXT_PUBLIC_API_GATEWAY_URL || 'http://localhost:10000';
    return [
      {
        source: '/api/:path*',
        destination: `${gateway}/api/:path*`,
      },
    ];
  },
};

export default nextConfig;
