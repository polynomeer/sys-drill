# docs/COMMERCIALIZATION.md — matches backend/Dockerfile's approach: a plain
# container image, no cloud-specific IaC.
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM node:22-alpine
WORKDIR /app
RUN addgroup -S sysdrill && adduser -S sysdrill -G sysdrill
COPY --from=build /app/.next/standalone ./
COPY --from=build /app/.next/static ./.next/static
COPY --from=build /app/public ./public
USER sysdrill
EXPOSE 3000
ENV PORT=3000
ENTRYPOINT ["node", "server.js"]
