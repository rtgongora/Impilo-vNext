# Build from repo root with context `ui/`:
#   docker build -f one-ui-shell/Dockerfile -t impilo-one-ui-shell .
FROM node:20-alpine AS base
WORKDIR /workspace

ARG NEXT_PUBLIC_BFF_URL=http://localhost:8160
ARG NEXT_PUBLIC_API_GATEWAY_URL=http://localhost:10000
ENV NEXT_PUBLIC_BFF_URL=$NEXT_PUBLIC_BFF_URL
ENV NEXT_PUBLIC_API_GATEWAY_URL=$NEXT_PUBLIC_API_GATEWAY_URL

# Use the UI workspace root so dependencies hoist to /workspace/node_modules.
COPY package.json ./package.json
COPY package-lock.json ./package-lock.json

# Copy workspace package.json files first for better caching.
COPY shared-ui/package.json ./shared-ui/package.json
COPY one-ui-shell/package.json ./one-ui-shell/package.json

RUN npm ci --legacy-peer-deps

COPY shared-ui ./shared-ui
COPY one-ui-shell ./one-ui-shell

RUN npm -w one-ui-shell run build

FROM node:20-alpine AS runner
WORKDIR /app
ENV NODE_ENV=production
ENV PORT=3000
COPY --from=base /workspace/one-ui-shell/.next-build/standalone ./
COPY --from=base /workspace/one-ui-shell/.next-build/static ./.next-build/static
COPY --from=base /workspace/one-ui-shell/public ./public

EXPOSE 3000
CMD ["node", "server.js"]
