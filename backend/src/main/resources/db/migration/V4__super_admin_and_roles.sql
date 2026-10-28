-- Platform administrator role: a SUPER_ADMIN user belongs to no owner account.
ALTER TABLE users MODIFY owner_id BIGINT NULL;

INSERT INTO roles (code, name, description) VALUES
 ('SUPER_ADMIN', 'Super admin', 'Platform administrator: manages owner accounts, never hostel operations');

-- The separate Accountant login role is retired. Existing accountants become managers and keep
-- the (finance-only) permissions they already hold.
UPDATE IGNORE user_roles SET role_id = (SELECT id FROM (SELECT id FROM roles WHERE code = 'MANAGER') m)
 WHERE role_id = (SELECT id FROM (SELECT id FROM roles WHERE code = 'ACCOUNTANT') a);
DELETE FROM user_roles WHERE role_id = (SELECT id FROM (SELECT id FROM roles WHERE code = 'ACCOUNTANT') a);
DELETE FROM role_permissions WHERE role_id = (SELECT id FROM (SELECT id FROM roles WHERE code = 'ACCOUNTANT') a);
DELETE FROM roles WHERE code = 'ACCOUNTANT';

-- Application-level settings managed by the super admin.
CREATE TABLE platform_settings (
    setting_key    VARCHAR(60)  NOT NULL PRIMARY KEY,
    setting_value  VARCHAR(255) NOT NULL,
    updated_at     DATETIME(6)  NOT NULL
) ENGINE = InnoDB;

INSERT INTO platform_settings (setting_key, setting_value, updated_at) VALUES
 ('PLATFORM_NAME', 'HostelHub', UTC_TIMESTAMP(6)),
 ('SUPPORT_EMAIL', 'support@hostelhub.dev', UTC_TIMESTAMP(6));
