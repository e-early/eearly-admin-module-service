-- Caretaker: organization and department
--
-- Adds the organization and department of a caretaker (nullable columns, foreign keys with
-- ON DELETE RESTRICT). Caretakers that exist
-- before this migration have neither, so, only when there are such caretakers, one default organization
-- with one default department is created and assigned to them. A database without caretakers gets no
-- rows. The migration can be run again without changing the result.

ALTER TABLE t_caretaker ADD COLUMN IF NOT EXISTS id_organization UUID;
ALTER TABLE t_caretaker ADD COLUMN IF NOT EXISTS id_department UUID;

DO
$$
    DECLARE
        default_organization_id CONSTANT UUID := 'a0000000-0000-4000-8000-000000000001';
        default_department_id   CONSTANT UUID := 'a0000000-0000-4000-8000-000000000002';
    BEGIN
        IF EXISTS (SELECT 1 FROM t_caretaker WHERE id_organization IS NULL OR id_department IS NULL) THEN
            INSERT INTO t_organization (id, name)
            VALUES (default_organization_id, 'Default organization')
            ON CONFLICT (id) DO NOTHING;

            INSERT INTO t_department (id, name, id_organization)
            VALUES (default_department_id, 'Default department', default_organization_id)
            ON CONFLICT (id) DO NOTHING;

            UPDATE t_caretaker
            SET id_organization = COALESCE(id_organization, default_organization_id),
                id_department   = COALESCE(id_department, default_department_id)
            WHERE id_organization IS NULL
               OR id_department IS NULL;
        END IF;

        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 't_caretaker_id_organization_fkey') THEN
            ALTER TABLE t_caretaker
                ADD CONSTRAINT t_caretaker_id_organization_fkey
                    FOREIGN KEY (id_organization) REFERENCES t_organization (id) ON DELETE RESTRICT;
        END IF;

        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 't_caretaker_id_department_fkey') THEN
            ALTER TABLE t_caretaker
                ADD CONSTRAINT t_caretaker_id_department_fkey
                    FOREIGN KEY (id_department) REFERENCES t_department (id) ON DELETE RESTRICT;
        END IF;
    END
$$;
