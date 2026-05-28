# Build from repo root:
#   docker build -f ui/one-ui-shell/Dockerfile -t impilo-one-ui-shell .
FROM node:20-alpine AS base
WORKDIR /workspace

ARG NEXT_PUBLIC_BFF_URL=http://localhost:8160
ARG NEXT_PUBLIC_API_GATEWAY_URL=http://localhost:10000
ENV NEXT_PUBLIC_BFF_URL=$NEXT_PUBLIC_BFF_URL
ENV NEXT_PUBLIC_API_GATEWAY_URL=$NEXT_PUBLIC_API_GATEWAY_URL

# Use the UI workspace root so dependencies hoist to /workspace/ui/node_modules.
COPY ui/package.json ./ui/package.json
COPY ui/package-lock.json ./ui/package-lock.json

# Copy workspace package.json files first for better caching.
COPY ui/shared-ui/package.json ./ui/shared-ui/package.json
COPY ui/one-ui-shell/package.json ./ui/one-ui-shell/package.json

WORKDIR /workspace/ui
RUN npm config set fetch-retries 5 \
    && npm config set fetch-retry-mintimeout 20000 \
    && npm config set fetch-retry-maxtimeout 120000 \
    && npm config set fetch-timeout 300000 \
    && npm config set strict-ssl false \
    && npm ci --legacy-peer-deps

WORKDIR /workspace
COPY ui/shared-ui ./ui/shared-ui
COPY ui/one-ui-shell ./ui/one-ui-shell
COPY contracts ./contracts

WORKDIR /workspace/ui
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
