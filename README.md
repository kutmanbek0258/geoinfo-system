# GeoInfo System Microservices

Моно-репозиторий для проекта **GeoInfo System** — интерактивной карты, реализованного на архитектуре микросервисов.

## Структура монорепозитория

- `config-server` — централизованный сервер конфигураций Spring Cloud.
- `discovery-server` — сервис-реестр Eureka для обнаружения микросервисов.
- `api-gateway` — единая точка входа, маршрутизация и фильтрация запросов.
- `auth-service` — сервис аутентификации и авторизации пользователей.
- `geodata-service` — сервис управления геообьектами и слоями дроновых снимков.
- `document-servuce` - сервис управления документов.
- `search-service` - сервис поиска.

---

## Описание микросервисов и TODO

### config-server
**Назначение:** централизованное хранение и раздача конфигураций.
- TODO:
    - [ ] Добавить шифрование чувствительных свойств.
    - [ ] Настроить автоматическое обновление конфигов при изменении в репозитории.

---

### discovery-server
**Назначение:** Eureka Server — сервис-реестр для обнаружения микросервисов.
- TODO:
    - [ ] Настроить мониторинг состояния сервисов.
    - [ ] Включить защиту доступа к реестру.

---

### api-gateway
**Назначение:** входная точка, маршрутизация, фильтрация, CORS, rate-limiting, авторизация.
- TODO:
    - [ ] Добавить централизованную обработку ошибок.
    - [ ] Реализовать троттлинг и лимиты по IP/пользователю.
    - [ ] Подключить tracing/логирование запросов.

---

## Быстрый старт (локально)

```bash
# собрать все
mvn clean package

# собрать отдельный модуль 
mvn clean install (с корневого)
cd module-directory
mvn claen package

# собрать и запустить через docker-compose
docker compose up --build

# Liquibase
mvn liquibase:generateChangeLog -Dliquibase.outputChangeLogFile=database/release-1.1.0/generated-changes.postgresql.sql -Dliquibase.schemas=geodata
mvn liquibase:diff -Dliquibase.diffChangeLogFile=database/release-1.1.0/diff-changes.postgresql.sql -Dliquibase.schemas=geodata
```

---

---

## TODO (глобально)
- [ ] Покрыть сервисы интеграционными тестами.
- [ ] Описать OpenAPI/Swagger для всех сервисов.
- [ ] Подключить централизованное логирование и мониторинг (ELK, Prometheus, Grafana).
- [ ] Настроить Graylog/Sentry для ошибок.
- [ ] Поддержка production-ready окружения (Kubernetes, Helm).