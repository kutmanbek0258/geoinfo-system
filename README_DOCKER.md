# Docker Compose: Разделение Production и Development

## Файлы

- **docker-compose.yml** — контексты всех сервисов.
- **docker-compose.prod.yml** — сервисы production, все микросервисы в Docker, наружу проброшен только gateway.
- **docker-compose.dev.yml** — dev-режим, микросервисы запускаются вручную/в IDE.

## Production запуск

```sh
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d
```
- Внутренние сервисы доступны только по internal сети.
- Наружу открыт только gateway (порт 80), minio (9000 — при необходимости).

## Development запуск

```sh
docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d
```
- БД, Redis запускаются в Docker и доступны на localhost через проброшенные порты.
- Микросервисы запускаются из IDE локально, подключаются к сервисам через `localhost:порт`.

### Пример переменных окружения для dev

```properties
# для auth-service (в application-dev.properties)
spring.datasource.url=jdbc:postgresql://localhost:5433/auth_db
spring.datasource.username=postgres
spring.datasource.password=password

# для user-service
spring.datasource.url=jdbc:postgresql://localhost:5434/user_db
...
```

### Пример запуска отдельных сервисов в dev

```sh
docker compose -f docker-compose.yml -f docker-compose.dev.yml up postgres-auth pgadmin
```
или несколько:
```sh
docker compose -f docker-compose.yml -f docker-compose.dev.yml up postgres-auth redis-auth minio
```
(можно запускать только нужные сервисы)

---

## Важно

- В production не пробрасывайте лишние порты.
- Не храните секреты в явном виде — вынесите их в `.env` или CI/CD secrets.
- В dev-профиле не используйте internal-сети, чтобы сервисы были доступны с localhost.