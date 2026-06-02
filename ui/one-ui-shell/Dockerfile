# Build from repo root (compose sets context: .):
#   docker compose -f docker-compose.runtime.yml build one-ui-shell
FROM node:20-alpine AS base
WORKDIR /workspace/ui

RUN apk add --no-cache libc6-compat
RUN corepack enable

ARG NEXT_PUBLIC_BFF_URL=http://localhost:8160
ARG NEXT_PUBLIC_API_GATEWAY_URL=http://localhost:10000
ENV NEXT_PUBLIC_BFF_URL=$NEXT_PUBLIC_BFF_URL
ENV NEXT_PUBLIC_API_GATEWAY_URL=$NEXT_PUBLIC_API_GATEWAY_URL
ENV NEXT_TELEMETRY_DISABLED=1

COPY ui/package.json ./package.json
COPY ui/package-lock.json ./package-lock.json
COPY ui/shared-ui/package.json ./shared-ui/package.json
COPY ui/one-ui-shell/package.json ./one-ui-shell/package.json

RUN npm ci --legacy-peer-deps --include=optional
RUN npm install --no-save --legacy-peer-deps @next/swc-linux-x64-musl@14.2.18

COPY ui/shared-ui ./shared-ui
COPY ui/one-ui-shell ./one-ui-shell
COPY registry-templates /workspace/registry-templates

RUN npm -w one-ui-shell run build

FROM node:20-alpine AS runner
ENV NODE_ENV=production
ENV PORT=3000
COPY --from=base /workspace/ui/one-ui-shell/.next-build/standalone /app
COPY --from=base /workspace/ui/one-ui-shell/.next-build/static /app/one-ui-shell/.next-build/static
COPY --from=base /workspace/ui/one-ui-shell/public /app/one-ui-shell/public

WORKDIR /app/one-ui-shell
EXPOSE 3000
CMD ["node", "server.js"]
