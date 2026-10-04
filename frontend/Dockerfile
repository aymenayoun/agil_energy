# ============================================
# AGIL Energy — Angular Frontend
# Multi-stage build: Node build → Nginx serve
# ============================================

# ---- Build stage ----
FROM node:20-alpine AS build
WORKDIR /app

# Cache dependencies
COPY package.json package-lock.json ./
RUN npm ci

# Copy source and build
COPY . .
RUN npm run build -- --configuration=production

# ---- Serve stage ----
FROM nginx:alpine

# Copy built Angular app (Angular 19 application builder outputs to dist/frontend/browser)
COPY --from=build /app/dist/frontend/browser /usr/share/nginx/html

# Copy custom nginx config
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
