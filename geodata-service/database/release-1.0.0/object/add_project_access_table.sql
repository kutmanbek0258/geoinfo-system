-- liquibase formatted sql

-- changeset ebt32945:1728352800000-1
CREATE TABLE geodata.project_access (
    project_id UUID NOT NULL,
    user_email VARCHAR(255) NOT NULL,
    permission_level VARCHAR(50) NOT NULL,
    PRIMARY KEY (project_id, user_email),
    CONSTRAINT fk_project_access_to_projects FOREIGN KEY (project_id) REFERENCES geodata.projects(id) ON DELETE CASCADE
);

COMMENT ON TABLE geodata.project_access IS 'Таблица для хранения прав доступа пользователей к проектам';
COMMENT ON COLUMN geodata.project_access.project_id IS 'Идентификатор проекта';
COMMENT ON COLUMN geodata.project_access.user_email IS 'Email пользователя, которому предоставлен доступ';
COMMENT ON COLUMN geodata.project_access.permission_level IS 'Уровень доступа (например, READ_ONLY)';
