# Build from repo root with context `ui/`:
#   docker build -f one-ui-shell/Dockerfile -t impilo-one-ui-shell .
FROM node:20-alpine AS base
WORKDIR /workspace

ARG NEXT_PUBLIC_BFF_URL=http://localhost:8160
ARG NEXT_PUBLIC_API_GATEWAY_URL=http://localhost:10000
ENV NEXT_PUBLIC_BFF_URL=$NEXT_PUBLIC_BFF_URL
ENV NEXT_PUBLIC_API_GATEWAY_URL=$NEXT_PUBLIC_API_GATEWAY_URL

COPY shared-ui ./shared-ui

WORKDIR /workspace/one-ui-shell
COPY one-ui-shell/package.json ./package.json
RUN npm install --legacy-peer-deps

COPY one-ui-shell/ ./
RUN npm run build

FROM node:20-alpine AS runner
WORKDIR /app
ENV NODE_ENV=production
ENV PORT=3000
COPY --from=base /workspace/one-ui-shell/.next-build/standalone ./
COPY --from=base /workspace/one-ui-shell/.next-build/static ./.next-build/static
COPY --from=base /workspace/one-ui-shell/public ./public

EXPOSE 3000
CMD ["node", "server.js"]
