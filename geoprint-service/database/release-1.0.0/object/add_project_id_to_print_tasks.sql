-- Добавление project_id в таблицу задач на печать
ALTER TABLE print.print_tasks ADD COLUMN project_id UUID;
CREATE INDEX idx_print_tasks_project_id ON print.print_tasks(project_id);
CREATE INDEX idx_print_tasks_created_by ON print.print_tasks(created_by);
