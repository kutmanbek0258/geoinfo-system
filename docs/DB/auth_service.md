## **Финальная Схема БД (Auth Service)**

База данных для управления пользователями, ролями, правами доступа и OAuth2 клиентами.

### **1. Таблица users (Пользователи)**

| Колонка | Тип данных | Описание |
| :--- | :--- | :--- |
| user_id | UUID | Уникальный идентификатор пользователя (Primary Key) |
| email | VARCHAR(100) | Email пользователя (Unique Index, Login) |
| password_hash | VARCHAR(500) | Хэш пароля |
| first_name | VARCHAR(100) | Имя |
| last_name | VARCHAR(100) | Фамилия |
| middle_name | VARCHAR(100) | Отчество |
| birthday | DATE | Дата рождения |
| avatar_file_id | UUID | ID файла аватарки в MinIO |
| active | BOOLEAN | Флаг активности аккаунта |
| admin | BOOLEAN | Флаг администратора |
| superuser | BOOLEAN | Флаг суперпользователя (нельзя удалить) |
| created_by | VARCHAR(50) | Кто создал |
| created_date | TIMESTAMP | Дата создания |

### **2. Таблица roles (Роли)**

| Колонка | Тип данных | Описание |
| :--- | :--- | :--- |
| role_id | UUID | Уникальный идентификатор роли (Primary Key) |
| role_code | VARCHAR(50) | Код роли (например, ROLE_ADMIN) |
| role_description | VARCHAR(500) | Описание роли |
| system_code | VARCHAR(50) | Код системы |
| active | BOOLEAN | Флаг активности |

### **3. Таблица authorities (Привилегии/Права)**

| Колонка | Тип данных | Описание |
| :--- | :--- | :--- |
| authority_id | UUID | Уникальный идентификатор (Primary Key) |
| authority_code | VARCHAR(100) | Код права (например, USER_READ) |
| authority_description | VARCHAR(500) | Описание права |
| system_code | VARCHAR(50) | Код системы |

### **4. Таблица role_authorities (Связь Ролей и Прав)**

Связь многие-ко-многим между ролями и правами.

| Колонка | Тип данных | Описание |
| :--- | :--- | :--- |
| role_authority_id | UUID | Primary Key |
| role_id | UUID | ID роли (Foreign Key) |
| authority_id | UUID | ID права (Foreign Key) |

### **5. Таблица user_roles (Связь Пользователей и Ролей)**

Связь многие-ко-многим между пользователями и ролями.

| Колонка | Тип данных | Описание |
| :--- | :--- | :--- |
| user_role_id | UUID | Primary Key |
| user_id | UUID | ID пользователя (Foreign Key) |
| role_id | UUID | ID роли (Foreign Key) |
