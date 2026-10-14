# syntax=docker/dockerfile:1

# ---- build stage -------------------------------------------------------------------------------
FROM node:20-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npm run build

# ---- runtime stage: unprivileged nginx (non-root, listens on 8080) -----------------------------
FROM nginxinc/nginx-unprivileged:1.27-alpine
# BACKEND_URL is substituted into the template at container start (works for compose and Kubernetes).
ENV BACKEND_URL=http://backend:8080
COPY nginx.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s --retries=5 CMD wget -qO- http://127.0.0.1:8080/healthz || exit 1
