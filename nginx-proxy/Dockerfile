FROM nginx:stable-alpine

# Удаляем дефолтный конфиг Nginx, чтобы он не конфликтовал с нашим
RUN rm /etc/nginx/conf.d/default.conf

# Копируем ваш файл конфигурации в папку конфигов Nginx
COPY ./nginx/proxy.conf /etc/nginx/conf.d/

# Открываем стандартный порт
EXPOSE 80

# Запускаем Nginx в фоновом режиме
CMD ["nginx", "-g", "daemon off;"]