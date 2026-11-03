-- SlotMind demo data. INSERT IGNORE keeps restarts from failing or duplicating rows.
-- Slot times are minutes from the first startup.

INSERT IGNORE INTO providers (id, name, specialty, status) VALUES
  ('PRV-001', 'Dr. Sarah Chen',     'GENERAL',    'BUSY'),
  ('PRV-002', 'Dr. James Okonkwo',  'SPECIALIST', 'AVAILABLE'),
  ('PRV-003', 'Dr. Priya Sharma',   'PEDIATRIC',  'BUSY'),
  ('PRV-004', 'Dr. Rahul Verma',    'GENERAL',    'AVAILABLE'),
  ('PRV-005', 'Dr. Ananya Iyer',    'SPECIALIST', 'BUSY');

INSERT IGNORE INTO slots (id, provider_id, start_time, duration_minutes, status, version) VALUES
  ('SLT-001', 'PRV-001', DATE_ADD(NOW(), INTERVAL 60  MINUTE), 30, 'BOOKED', 0),
  ('SLT-002', 'PRV-001', DATE_ADD(NOW(), INTERVAL 120 MINUTE), 30, 'BOOKED', 0),
  ('SLT-003', 'PRV-003', DATE_ADD(NOW(), INTERVAL 90  MINUTE), 45, 'BOOKED', 0),
  ('SLT-004', 'PRV-002', DATE_ADD(NOW(), INTERVAL 180 MINUTE), 30, 'OPEN',   0),
  ('SLT-005', 'PRV-004', DATE_ADD(NOW(), INTERVAL 150 MINUTE), 30, 'OPEN',   0),
  ('SLT-006', 'PRV-005', DATE_ADD(NOW(), INTERVAL 240 MINUTE), 60, 'BOOKED', 0);

INSERT IGNORE INTO appointments
  (id, patient_name, type, patient_phone, provider_id, slot_id, status, reschedule_count, created_at, modified_at, version) VALUES
  ('APT-001', 'John Martinez', 'ROUTINE',   '+1-555-0101', 'PRV-001', 'SLT-001', 'CONFIRMED', 0, NOW(), NOW(), 0),
  ('APT-002', 'Emily Watson',  'FOLLOW_UP', '+1-555-0102', 'PRV-001', 'SLT-002', 'CONFIRMED', 0, NOW(), NOW(), 0),
  ('APT-003', 'Aisha Patel',   'URGENT',    '+1-555-0103', 'PRV-003', 'SLT-003', 'CONFIRMED', 0, NOW(), NOW(), 0),
  ('APT-004', 'Robert Kim',    'ROUTINE',   '+1-555-0104', 'PRV-005', 'SLT-006', 'CONFIRMED', 0, NOW(), NOW(), 0);
