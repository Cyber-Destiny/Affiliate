/** @type {import('next').NextConfig} */
const nextConfig = {
  // A plain static site: build once, host anywhere (Vercel, Netlify, or `npm start`).
  output: 'export',
  trailingSlash: true,
  images: { unoptimized: true },
  eslint: { ignoreDuringBuilds: true },
}

export default nextConfig
