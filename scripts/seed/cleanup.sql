-- Supprime uniquement les données de test (e-mails @seed.garage.test) et ce qui en dépend.
BEGIN;
DELETE FROM invoice_lines WHERE invoice_id IN (SELECT i.id FROM invoices i JOIN clients c ON c.id=i.client_id WHERE c.email LIKE '%@seed.garage.test');
DELETE FROM invoices WHERE client_id IN (SELECT id FROM clients WHERE email LIKE '%@seed.garage.test');
DELETE FROM quote_lines WHERE quote_id IN (SELECT q.id FROM quotes q JOIN appointments a ON a.id=q.appointment_id JOIN clients c ON c.id=a.client_id WHERE c.email LIKE '%@seed.garage.test');
DELETE FROM quotes WHERE appointment_id IN (SELECT a.id FROM appointments a JOIN clients c ON c.id=a.client_id WHERE c.email LIKE '%@seed.garage.test');
DELETE FROM appointments WHERE client_id IN (SELECT id FROM clients WHERE email LIKE '%@seed.garage.test');
DELETE FROM vehicles WHERE client_id IN (SELECT id FROM clients WHERE email LIKE '%@seed.garage.test');
DELETE FROM password_reset_tokens WHERE user_id IN (SELECT id FROM users WHERE email LIKE '%@seed.garage.test');
DELETE FROM users WHERE email LIKE '%@seed.garage.test';
DELETE FROM clients WHERE email LIKE '%@seed.garage.test';
COMMIT;
