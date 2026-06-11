FROM node:20-alpine AS build
WORKDIR /workspace/frontend/scf-web

COPY frontend/scf-web/package*.json ./
RUN npm ci

COPY frontend/scf-web ./
RUN npm run build

FROM nginx:1.27-alpine
COPY deploy/quasi-staging/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/frontend/scf-web/dist /usr/share/nginx/html

EXPOSE 80
