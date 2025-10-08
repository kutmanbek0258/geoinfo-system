# Базовый официальный образ GeoServer
FROM docker.osgeo.org/geoserver:2.28.x

# Копируем плагин OAuth2
COPY geoserver-2.28-SNAPSHOT-sec-oauth2-openid-connect-plugin.zip /tmp/oauth2.zip

# Создаём папку для дополнительных библиотек и распаковываем плагин
RUN mkdir -p /opt/additional_libs \
    && unzip -q /tmp/oauth2.zip -d /opt/additional_libs \
    && rm /tmp/oauth2.zip

# GeoServer слушает 8080 внутри контейнера
EXPOSE 8080