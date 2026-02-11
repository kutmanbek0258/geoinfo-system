# build stage
FROM node:16-alpine as build-stage

WORKDIR /app

COPY . .

RUN npm install
RUN npm run build-prod

FROM nginx:stable-alpine as deploy-stage

ENV TZ=Asia/Bishkek
RUN mkdir -p /var/log/nginx
RUN mkdir -p /var/www/html
RUN chown nginx:nginx /var/www/html

COPY --from=build-stage /app/dist /var/www/html/sso
COPY nginx/auth-client.conf /etc/nginx/conf.d/auth-client.conf

EXPOSE 8181

CMD ["nginx", "-g", "daemon off;"]