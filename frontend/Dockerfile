# build stage
FROM node:16-alpine as build-stage

WORKDIR /app

ARG VITE_API_URL
ARG VITE_API_GATEWAY_URL
ARG VITE_AUTH_SERVICE_URL
ARG VITE_AUTH_CALLBACK_URL
ARG VITE_ONLYOFFICE_URL
ARG VITE_GEOSERVER_URL
ARG VITE_ONLYOFFICE_API_URL

ENV VITE_API_URL=$VITE_API_URL
ENV VITE_API_GATEWAY_URL=$VITE_API_GATEWAY_URL
ENV VITE_AUTH_SERVICE_URL=$VITE_AUTH_SERVICE_URL
ENV VITE_AUTH_CALLBACK_URL=$VITE_AUTH_CALLBACK_URL
ENV VITE_ONLYOFFICE_URL=$VITE_ONLYOFFICE_URL
ENV VITE_GEOSERVER_URL=$VITE_GEOSERVER_URL
ENV VITE_ONLYOFFICE_API_URL=$VITE_ONLYOFFICE_API_URL

COPY . .

RUN npm install
RUN npm run build

FROM nginx:stable-alpine as deploy-stage

ENV TZ=Asia/Bishkek
RUN mkdir -p /var/log/nginx
RUN mkdir -p /var/www/html
RUN chown nginx:nginx /var/www/html

COPY --from=build-stage /app/dist /var/www/html/wms-client
COPY ./nginx/nginx.conf /etc/nginx/nginx.conf
COPY nginx/wms-client.conf /etc/nginx/conf.d/wms-client.conf

COPY ./entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh
RUN sed -i 's/\r$//g' /entrypoint.sh

EXPOSE 8080

ENTRYPOINT ["/entrypoint.sh"]
CMD ["nginx", "-g", "daemon off;"]