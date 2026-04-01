/** @type {import('next').NextConfig} */
const nextConfig = {
  output: 'standalone',
  async rewrites() {
    const envoyBase = process.env.NEXT_PUBLIC_VITO_API_URL || 'http://localhost:10000';
    return [
      {
        source: '/v1/:path*',
        destination: `${envoyBase}/v1/:path*`,
      },
    ];
  },
};

module.exports = nextConfig;
