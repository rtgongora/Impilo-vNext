FROM node:20-alpine AS base
WORKDIR /app

# Build-time env vars (baked into Next.js rewrites / server.js)
ARG NEXT_PUBLIC_BFF_URL=http://localhost:8160
ARG NEXT_PUBLIC_API_GATEWAY_URL=http://localhost:10000
ENV NEXT_PUBLIC_BFF_URL=$NEXT_PUBLIC_BFF_URL
ENV NEXT_PUBLIC_API_GATEWAY_URL=$NEXT_PUBLIC_API_GATEWAY_URL

# Install dependencies
COPY package.json ./
RUN npm install --legacy-peer-deps

# Copy source
COPY . .

# Build
RUN npm run build

# Production
FROM node:20-alpine AS runner
WORKDIR /app
ENV NODE_ENV=production
COPY --from=base /app/.next-build/standalone ./
COPY --from=base /app/.next-build/static ./.next-build/static
COPY --from=base /app/public ./public

EXPOSE 3099
ENV PORT=3099
CMD ["node", "server.js"]
