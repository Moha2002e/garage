# syntax=docker/dockerfile:1
# ---- Build : bundle Angular de production ----
FROM node:24-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci
COPY . .
# URL de l'API vue par le navigateur (8080 est souvent pris par un autre projet)
ARG API_URL=http://garage.localhost:8082
RUN sed -i "s|http://localhost:8080|${API_URL}|" src/environments/environment.ts
RUN npx ng build --configuration production

# ---- Run : fichiers statiques servis par nginx ----
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/frontend/browser /usr/share/nginx/html
EXPOSE 80
