FROM nginx:stable-alpine

# Удаляем дефолтные конфиги
RUN rm -rf /etc/nginx/conf.d/*

# Копируем наш главный конфиг
COPY ./nginx/proxy.conf /etc/nginx/nginx.conf

# Директории для кеша и логов
RUN mkdir -p /var/cache/nginx \
    && mkdir -p /var/log/nginx \
    && chown -R nginx:nginx /var/cache/nginx /var/log/nginx

EXPOSE 80

STOPSIGNAL SIGTERM

CMD ["nginx", "-g", "daemon off;"]