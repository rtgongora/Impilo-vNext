# Build from repo root:
#   docker build -f ui/one-ui-shell/Dockerfile -t impilo-one-ui-shell .
#
# Dev compose only: NODE_TLS_REJECT_UNAUTHORIZED=0 mirrors npm strict-ssl false for
# corp networks that block SWC binary downloads during next build.
FROM node:20-bookworm-slim AS base
WORKDIR /workspace

# BROWSER-facing base URLs: preview/production leave these EMPTY so the browser uses
# same-origin relative paths (Traefik routes /internal/* straight to experience-bff).
# Compose/local dev overrides at build time.
ARG NEXT_PUBLIC_BFF_URL=
ARG NEXT_PUBLIC_API_GATEWAY_URL=
ENV NEXT_PUBLIC_BFF_URL=$NEXT_PUBLIC_BFF_URL
ENV NEXT_PUBLIC_API_GATEWAY_URL=$NEXT_PUBLIC_API_GATEWAY_URL

# SERVER-side proxy targets for next.config rewrites(). These are a DIFFERENT concern
# from the NEXT_PUBLIC_* pair above: rewrites are evaluated at BUILD time and baked into
# routes-manifest.json, so whatever is set here is compiled into the image permanently —
# runtime env cannot change it. They must therefore default to the in-cluster service, not
# to localhost: a localhost target is unreachable from a single-container pod and produced
# ECONNREFUSED 500s on every /api/v1/* route (facility claim, lifecycle, site self-service,
# authorize, speech-to-text) while the deployment and its runtime env both looked correct.
ARG API_GATEWAY_URL=http://experience-bff:8160
ARG BFF_URL=http://experience-bff:8160
ENV API_GATEWAY_URL=$API_GATEWAY_URL
ENV BFF_URL=$BFF_URL
ENV NEXT_TELEMETRY_DISABLED=1
ENV NODE_TLS_REJECT_UNAUTHORIZED=0

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
    && npm install --legacy-peer-deps \
    && npm install @next/swc-linux-x64-gnu@14.2.18 --legacy-peer-deps --no-save

WORKDIR /workspace
# Runtime image truth: bust BuildKit cache for source+build layers when commit/content changes.
# Dependency-install layers above remain cacheable for speed.
ARG SOURCE_COMMIT=unknown
ARG CACHE_BUST=unknown
RUN echo "cachebust=${CACHE_BUST} commit=${SOURCE_COMMIT}" > /dev/null

COPY ui/shared-ui ./ui/shared-ui
COPY ui/one-ui-shell ./ui/one-ui-shell
COPY contracts ./contracts
COPY registry-templates ./registry-templates

WORKDIR /workspace/ui
RUN npm -w one-ui-shell run build

FROM node:20-bookworm-slim AS runner
# Bake build provenance so the running image is self-describing (deploy truth).
ARG SOURCE_COMMIT=unknown
ARG SOURCE_BRANCH=unknown
ARG BUILD_DATE=unknown
ENV NODE_ENV=production
ENV PORT=3000
ENV NEXT_TELEMETRY_DISABLED=1
ENV IMPILO_GIT_COMMIT=${SOURCE_COMMIT} \
    IMPILO_GIT_BRANCH=${SOURCE_BRANCH} \
    IMPILO_BUILD_DATE=${BUILD_DATE}
COPY --from=base /workspace/ui/one-ui-shell/.next-build/standalone /app
COPY --from=base /workspace/ui/one-ui-shell/.next-build/static /app/one-ui-shell/.next-build/static
COPY --from=base /workspace/ui/one-ui-shell/public /app/one-ui-shell/public

WORKDIR /app/one-ui-shell
EXPOSE 3000
CMD ["node", "server.js"]
