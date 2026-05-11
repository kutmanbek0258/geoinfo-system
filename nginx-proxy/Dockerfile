FROM nginx:stable-alpine

# Удаляем дефолтные конфиги
RUN rm -rf /etc/nginx/conf.d/*

# Копируем наш главный конфиг как шаблон
COPY ./nginx/proxy.conf /etc/nginx/nginx.conf.template

# Директории для кеша и логов
RUN mkdir -p /var/cache/nginx \
    && mkdir -p /var/log/nginx \
    && chown -R nginx:nginx /var/cache/nginx /var/log/nginx

EXPOSE 80

STOPSIGNAL SIGTERM

# При запуске подставляем переменные окружения и запускаем nginx
# Используем envsubst для замены ${MAX_UPLOAD_SIZE} в конфиге
CMD ["/bin/sh", "-c", "envsubst '${MAX_UPLOAD_SIZE}' < /etc/nginx/nginx.conf.template > /etc/nginx/nginx.conf && exec nginx -g 'daemon off;'"]