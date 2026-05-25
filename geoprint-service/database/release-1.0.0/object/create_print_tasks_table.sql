-- Таблица для отслеживания задач на печать
CREATE TABLE IF NOT EXISTS print.print_tasks (
    id UUID PRIMARY KEY,
    status VARCHAR(32) NOT NULL,
    layout VARCHAR(64) NOT NULL,
    s3_url VARCHAR(512),
    error_message TEXT,
    attributes JSONB DEFAULT '{}',
    created_by              VARCHAR(255),
    created_date            TIMESTAMP,
    last_modified_by        VARCHAR(255),
    last_modified_date      TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_print_tasks_status ON print.print_tasks(status);
