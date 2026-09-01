CREATE OR REPLACE FUNCTION update_updated_at_column()
    RETURNS trigger
    LANGUAGE plpgsql
AS $function$
begin
    new.updated_at = now();
    return new;
end
$function$;

DROP TRIGGER IF EXISTS triger_tasks_updated_at ON tasks;

CREATE TRIGGER triger_tasks_updated_at
    BEFORE UPDATE ON tasks
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();
