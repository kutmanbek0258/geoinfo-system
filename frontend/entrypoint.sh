#!/bin/sh
set -e

ROOT_DIR=/var/www/html/wms-client

echo "Replacing environment variables in $ROOT_DIR"

# Найти все JS, CSS и HTML файлы
for file in $(find $ROOT_DIR -type f \( -name '*.js' -o -name '*.css' -o -name '*.html' \));
do
  echo "Processing $file ...";

  # Заменить плейсхолдеры на значения переменных окружения
  sed -i "s|__VITE_API_URL__|${VITE_API_URL}|g" $file
  sed -i "s|__VITE_API_GATEWAY_URL__|${VITE_API_GATEWAY_URL}|g" $file
  sed -i "s|__VITE_AUTH_SERVICE_URL__|${VITE_AUTH_SERVICE_URL}|g" $file
  sed -i "s|__VITE_AUTH_CALLBACK_URL__|${VITE_AUTH_CALLBACK_URL}|g" $file
  sed -i "s|__VITE_ONLYOFFICE_URL__|${VITE_ONLYOFFICE_URL}|g" $file
  sed -i "s|__VITE_GEOSERVER_URL__|${VITE_GEOSERVER_URL}|g" $file
  sed -i "s|__VITE_ONLYOFFICE_API_URL__|${VITE_ONLYOFFICE_API_URL}|g" $file

done

echo "Starting Nginx"
exec "$@"
