-- Reference data required in every environment (roles, permissions, default templates).

INSERT INTO roles (code, name, description) VALUES
 ('OWNER',      'Owner',          'Full access to all hostels of the owner account'),
 ('MANAGER',    'Hostel manager', 'Runs day-to-day operations of assigned hostels'),
 ('ACCOUNTANT', 'Accountant',     'Handles billing, collections and financial reports'),
 ('WARDEN',     'Warden',         'Manages residents, rooms and maintenance on site'),
 ('STAFF',      'Staff',          'Limited access for support staff');

INSERT INTO permissions (code, name, category) VALUES
 ('HOSTEL_VIEW',        'View hostels and occupancy',          'Hostels'),
 ('HOSTEL_MANAGE',      'Create and edit hostels & buildings', 'Hostels'),
 ('ROOM_MANAGE',        'Manage floors, rooms and beds',       'Rooms'),
 ('RESIDENT_VIEW',      'View residents',                      'Residents'),
 ('RESIDENT_MANAGE',    'Admit, transfer and check out',       'Residents'),
 ('ENQUIRY_MANAGE',     'Manage enquiries and bookings',       'Residents'),
 ('PAYMENT_VIEW',       'View invoices and payments',          'Payments'),
 ('PAYMENT_COLLECT',    'Generate invoices, collect payments', 'Payments'),
 ('PAYMENT_ADJUST',     'Discounts, waivers, refunds, reversals', 'Payments'),
 ('FINANCE_REPORTS',    'View financial reports',              'Reports'),
 ('EXPENSE_MANAGE',     'Record expenses',                     'Payments'),
 ('MAINTENANCE_MANAGE', 'Manage maintenance requests',         'Maintenance'),
 ('NOTIFICATION_SEND',  'Send WhatsApp messages & announcements', 'Notifications'),
 ('STAFF_MANAGE',       'Manage staff, users and permissions', 'Administration'),
 ('SETTINGS_MANAGE',    'Change account settings & templates', 'Administration');

-- OWNER receives every permission
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p WHERE r.code = 'OWNER';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p
  ON p.code IN ('HOSTEL_VIEW','ROOM_MANAGE','RESIDENT_VIEW','RESIDENT_MANAGE','ENQUIRY_MANAGE',
                'PAYMENT_VIEW','PAYMENT_COLLECT','FINANCE_REPORTS','EXPENSE_MANAGE',
                'MAINTENANCE_MANAGE','NOTIFICATION_SEND')
WHERE r.code = 'MANAGER';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p
  ON p.code IN ('HOSTEL_VIEW','RESIDENT_VIEW','PAYMENT_VIEW','PAYMENT_COLLECT','PAYMENT_ADJUST',
                'FINANCE_REPORTS','EXPENSE_MANAGE','NOTIFICATION_SEND')
WHERE r.code = 'ACCOUNTANT';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p
  ON p.code IN ('HOSTEL_VIEW','RESIDENT_VIEW','RESIDENT_MANAGE','ENQUIRY_MANAGE','PAYMENT_VIEW',
                'MAINTENANCE_MANAGE','NOTIFICATION_SEND')
WHERE r.code = 'WARDEN';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p
  ON p.code IN ('HOSTEL_VIEW','RESIDENT_VIEW','MAINTENANCE_MANAGE')
WHERE r.code = 'STAFF';

-- System default WhatsApp templates (owner_id NULL). Placeholders use {{name}} syntax.
INSERT INTO notification_templates (owner_id, code, name, channel, body, provider_template, language_code, active, created_at, updated_at) VALUES
 (NULL, 'RENT_REMINDER', 'Rent reminder', 'WHATSAPP',
  'Hi {{residentName}}, this is a reminder that your rent of Rs. {{amount}} for {{hostelName}} (Room {{roomNumber}}) is due on {{dueDate}}. Invoice: {{invoiceNumber}}. Thank you!',
  'rent_reminder', 'en', TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 (NULL, 'OVERDUE_NOTICE', 'Overdue payment notice', 'WHATSAPP',
  'Hi {{residentName}}, your payment of Rs. {{amount}} for {{hostelName}} was due on {{dueDate}} and is now overdue. Please pay at the earliest. Invoice: {{invoiceNumber}}.',
  'overdue_notice', 'en', TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 (NULL, 'PAYMENT_CONFIRMATION', 'Payment confirmation', 'WHATSAPP',
  'Hi {{residentName}}, we have received Rs. {{amount}} on {{paymentDate}} via {{method}}. Receipt no: {{receiptNumber}}. Thank you - {{hostelName}}.',
  'payment_confirmation', 'en', TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 (NULL, 'ADMISSION_CONFIRMATION', 'Admission confirmation', 'WHATSAPP',
  'Welcome to {{hostelName}}, {{residentName}}! Your admission is confirmed from {{admissionDate}}. Room {{roomNumber}}, Bed {{bedLabel}}. Monthly rent: Rs. {{amount}}.',
  'admission_confirmation', 'en', TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 (NULL, 'CHECKOUT_REMINDER', 'Checkout reminder', 'WHATSAPP',
  'Hi {{residentName}}, as per our records your checkout from {{hostelName}} (Room {{roomNumber}}) is scheduled on {{checkoutDate}}. Please contact the office for clearance.',
  'checkout_reminder', 'en', TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 (NULL, 'MAINTENANCE_UPDATE', 'Maintenance update', 'WHATSAPP',
  'Hi {{residentName}}, update on "{{title}}" at {{hostelName}}: status is now {{status}}.',
  'maintenance_update', 'en', TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 (NULL, 'ANNOUNCEMENT', 'Announcement', 'WHATSAPP',
  '{{hostelName}} announcement: {{title}} - {{message}}',
  'announcement', 'en', TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 (NULL, 'ENQUIRY_FOLLOW_UP', 'Enquiry follow-up', 'WHATSAPP',
  'Hi {{residentName}}, thank you for your interest in {{hostelName}}. Beds are available from {{date}}. Reply to this message or call us to book a visit.',
  'enquiry_follow_up', 'en', TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 (NULL, 'CUSTOM', 'Custom message', 'WHATSAPP',
  '{{message}}',
  NULL, 'en', TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));
