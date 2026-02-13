# nginx-proxy

## Описание

`nginx-proxy` является центральным компонентом системы GeoInfoSystem, действующим как обратный прокси-сервер, балансировщик нагрузки и уровень кэширования. Он отвечает за маршрутизацию внешних запросов к соответствующим внутренним микросервисам, обеспечивая при этом высокую производительность и эффективное использование ресурсов.

## Архитектура

Этот сервис построен на базе Nginx и разработан для работы в среде Docker Compose. Он перехватывает все входящие HTTP-запросы и, основываясь на правилах маршрутизации, перенаправляет их к соответствующим бэкенд-сервисам. `nginx-proxy` также предоставляет возможности кэширования для статического контента, геоданных и HLS-потоков, что значительно снижает нагрузку на бэкенд и ускоряет доставку контента пользователям.

## Конфигурация

Конфигурация `nginx-proxy` определяется файлом `proxy.conf`, который копируется в `/etc/nginx/nginx.conf` внутри контейнера. Ключевые аспекты конфигурации включают:

*   **Высокая производительность**: Настройки оптимизированы для большого количества одновременных подключений (`worker_connections 65536`).
*   **Логирование**: Используется пользовательский формат JSON для журналов доступа (`/var/log/nginx/access.log`), что упрощает мониторинг и анализ.
*   **Сжатие Gzip**: Включено для уменьшения размера передаваемых данных и ускорения загрузки.
*   **Буферизация и тайм-ауты**: Настроены для оптимальной работы с различными типами трафика.
*   **Кэширование**: Определены три зоны кэширования:
    *   `hls_cache`: Для потоков HLS (.m3u8, .ts).
    *   `geocache`: Для геоданных, вероятно, тайлов GeoServer.
    *   `static_cache`: Для общего статического контента.

## Upstreams

`nginx-proxy` настроен для взаимодействия со следующими внутренними сервисами:

*   `frontend_backend`: Фронтенд-приложение (Vue.js)
*   `api_gateway`: API Gateway (Spring Cloud Gateway)
*   `auth_service`: Сервис аутентификации
*   `geodata_service`: Сервис для работы с векторными геоданными.
*   `document_service`: Сервис для управления документами и файлами.
*   `search_service`: Сервис для полнотекстового и геопространственного поиска.
*   `stream_service`: Сервис для управления потоковыми трансляциями (например, с камер Hikvision).
*   `auth_service`: Сервис аутентификации
*   `geodata_service`: Сервис для работы с векторными геоданными.
*   `document_service`: Сервис для управления документами и файлами.
*   `search_service`: Сервис для полнотекстового и геопространственного поиска.
*   `stream_service`: Сервис для управления потоковыми трансляциями (например, с камер Hikvision).
*   `geoserver_backend`: GeoServer
*   `onlyoffice_backend`: OnlyOffice Document Server
*   `hls_backend`: Сервис для потоковой передачи HLS (на базе `mediamtx`)

## Маршрутизация

Маршрутизация запросов осуществляется на основе URL-путей и, в некоторых случаях, доменов:

*   **`sso.localhost`**: Запросы к этому домену перенаправляются на `auth_service` для обработки аутентификации.
*   **`/`**: Корневой путь направляется на `frontend_backend`.
*   **`/api/`**: Запросы к API перенаправляются на `api_gateway`.
*   **`/geoserver/`**: Запросы к GeoServer проксируются на `geoserver_backend` с активным кэшированием.
*   **`/onlyoffice/`**: Запросы к OnlyOffice перенаправляются на `onlyoffice_backend` с удалением префикса `/onlyoffice/`.
*   **`*.m3u8` и `*.ts`**: Файлы потокового видео (HLS) направляются на `hls_backend` с агрессивным кэшированием.
*   **`/static/`**: Статические файлы проксируются через `api_gateway` с использованием кэширования.

Этот компонент играет ключевую роль в обеспечении доступности и производительности GeoInfoSystem.

## Настройка HTTPS с Let's Encrypt и Certbot

Для обеспечения безопасного соединения с вашим GeoInfoSystem через HTTPS вы можете использовать бесплатные SSL/TLS сертификаты от Let's Encrypt, полученные с помощью инструмента Certbot.

### 1. Предварительные требования

Перед началом убедитесь, что у вас есть:

*   Сервер Ubuntu (или другой дистрибутив Linux, поддерживаемый Certbot).
*   Доменное имя (например, `geoinfosystem.com`) и поддомены (например, `sso.geoinfosystem.com`), для которых вы хотите получить сертификаты.
*   Настроенные DNS-записи `A` или `AAAA` для вашего домена и всех поддоменов, указывающие на IP-адрес вашего сервера.
*   Открытые порты `80` (HTTP) и `443` (HTTPS) на вашем сервере.

### 2. Установка Certbot на Ubuntu

```bash
sudo snap install core; sudo snap refresh core
sudo snap install --classic certbot
sudo ln -s /snap/bin/certbot /usr/bin/certbot
```

### 3. Получение SSL/TLS сертификатов

Мы будем использовать метод `webroot`, который позволяет Certbot проверять владение доменом, размещая временные файлы в директории, доступной через веб-сервер. Для этого ваш `nginx-proxy` должен быть запущен и корректно проксировать HTTP-трафик для доменов.

Предположим, ваш основной домен — `geoinfosystem.com`, а для аутентификации используется `sso.geoinfosystem.com`.

**Шаг 3.1: Получение сертификатов для основного домена**

Выполните команду Certbot, указав корневую директорию вашего веб-сервера для проверки. В контексте `nginx-proxy`, Certbot будет обращаться к `nginx-proxy`, который должен проксировать запросы `/` на `frontend_backend`.

```bash
sudo certbot certonly --webroot -w /var/www/certbot -d geoinfosystem.com -d www.geoinfosystem.com
```
*   `--webroot`: Указывает использовать метод webroot.
*   `-w /var/www/certbot`: Это директория, которую Certbot будет использовать для создания временных файлов валидации. Вам нужно будет настроить Nginx для обслуживания этой директории для запросов `/.well-known/acme-challenge/`.
*   `-d yourdomain.com -d www.yourdomain.com`: Указывает домены, для которых вы запрашиваете сертификат.

**Шаг 3.2: Получение сертификатов для поддомена SSO**

Аналогично, для поддомена SSO:

```bash
sudo certbot certonly --webroot -w /var/www/certbot -d sso.geoinfosystem.com
```

Certbot сохранит ваши сертификаты в `/etc/letsencrypt/live/yourdomain.com/` (для основного домена) и `/etc/letsencrypt/live/sso.geoinfosystem.com/` (для поддомена SSO). Важные файлы: `fullchain.pem` (сертификат) и `privkey.pem` (приватный ключ).

### 4. Настройка `nginx-proxy` для использования HTTPS

#### 4.1. Создание директории для сертификатов

В корневой директории вашего проекта GeoInfoSystem создайте директорию, где будут храниться симлинки на сертификаты Let's Encrypt. Это упростит монтирование в Docker.

```bash
mkdir -p ./nginx-proxy/certs
```

#### 4.2. Создание симлинков на сертификаты

Создайте символические ссылки из директории Let's Encrypt на только что созданную директорию.

```bash
# Для основного домена
sudo ln -sf /etc/letsencrypt/live/geoinfosystem.com/fullchain.pem ./nginx-proxy/certs/localhost.crt
sudo ln -sf /etc/letsencrypt/live/geoinfosystem.com/privkey.pem ./nginx-proxy/certs/localhost.key

# Для поддомена SSO
sudo ln -sf /etc/letsencrypt/live/sso.geoinfosystem.com/fullchain.pem ./nginx-proxy/certs/sso.localhost.crt
sudo ln -sf /etc/letsencrypt/live/sso.geoinfosystem.com/privkey.pem ./nginx-proxy/certs/sso.localhost.key
```
**Важно:** Убедитесь, что имена симлинков (`localhost.crt`, `localhost.key`, `sso.localhost.crt`, `sso.localhost.key`) соответствуют именам файлов, указанным в `proxy-https.conf`.
**Примечание:** Для `localhost.crt`/`localhost.key` я использую `geoinfosystem.com`, так как это будет сертификат для основного домена, который по умолчанию будет использоваться для `localhost` в Docker-конфигурации.

#### 4.3. Обновление Docker Compose файла

В вашем `docker-compose.yml` (или `docker-compose.prod.yml`) для сервиса `nginx-proxy` вам нужно будет:

1.  **Изменить путь к файлу конфигурации Nginx** с `proxy.conf` на `proxy-https.conf`.
2.  **Смонтировать директорию с сертификатами** как volume.

Пример фрагмента `docker-compose.yml`:

```yaml
services:
  nginx-proxy:
    # ... другие настройки
    volumes:
      - ./nginx-proxy/nginx/proxy-https.conf:/etc/nginx/nginx.conf:ro
      - ./nginx-proxy/certs:/etc/nginx/certs:ro # Монтируем директорию с сертификатами
    ports:
      - "80:80"
      - "443:443"
    # ...
```

#### 4.4. Запуск `nginx-proxy` с HTTPS

Перезапустите ваш стек Docker Compose, чтобы применить изменения:

```bash
docker compose --env-file .env.secrets --env-file .env.example -f docker-compose.yml -f docker-compose.prod.yml up -d --build
```

Теперь ваш `nginx-proxy` должен обслуживать трафик по HTTPS.

### 5. Автоматическое продление сертификатов

Certbot автоматически создает задание `cron` или `systemd timer` для продления сертификатов. Вам нужно убедиться, что Nginx может перезагружаться после продления.

1.  **Проверка автопродления:**
    ```bash
    sudo certbot renew --dry-run
    ```
2.  **Настройка перезагрузки Nginx (если необходимо):**
    После успешного продления Certbot необходимо перезагрузить Nginx, чтобы он начал использовать новые сертификаты.
    Если ваш `nginx-proxy` запущен через Docker Compose, вы можете добавить команду перезагрузки в скрипт, который запускается после продления Certbot.
    Например, в cronjob Certbot можно добавить:
    ```bash
    # После строки certbot renew
    docker compose -f /path/to/your/docker-compose.yml -f /path/to/your/docker-compose.prod.yml restart nginx-proxy
    ```
    Замените `/path/to/your/docker-compose.yml` на фактический путь к вашему файлу Docker Compose.

Следуя этим шагам, вы сможете настроить HTTPS для вашей GeoInfoSystem.