-- Données de test uniquement. À lancer sur la base de développement.
-- 1. Clôture tous les rendez-vous passés (planifiés ou en cours) : statut TERMINÉ (COMPLETED).
-- 2. Crée un devis ACCEPTÉ avec une ligne pour chaque rendez-vous terminé qui n'a pas de devis,
--    sinon le batch de factures n'a rien à facturer (il exige un devis accepté).
-- Les rendez-vous annulés ne sont pas touchés. Relancer le script ne crée pas de doublon.

BEGIN;

UPDATE appointments
   SET status = 'COMPLETED', updated_at = now()
 WHERE appointment_date < now()
   AND status IN ('PLANNED', 'IN_PROGRESS');

WITH new_quotes AS (
    INSERT INTO quotes (id, appointment_id, status, total, created_at, updated_at)
    SELECT gen_random_uuid(), a.id, 'ACCEPTED', 120.00, now(), now()
      FROM appointments a
     WHERE a.status = 'COMPLETED'
       AND NOT EXISTS (SELECT 1 FROM quotes q WHERE q.appointment_id = a.id)
    RETURNING id
)
INSERT INTO quote_lines (id, quote_id, sort_order, description, quantity, unit_price, active, created_at, updated_at)
SELECT gen_random_uuid(), nq.id, 0, 'Main d''oeuvre', 2.00, 35.00, true, now(), now() FROM new_quotes nq
UNION ALL
SELECT gen_random_uuid(), nq.id, 1, 'Pièces et fournitures', 1.00, 50.00, true, now(), now() FROM new_quotes nq;

-- Contrôle : nombre de rendez-vous par statut et nombre de devis acceptés.
SELECT status, count(*) FROM appointments GROUP BY status ORDER BY status;
SELECT status, count(*) FROM quotes GROUP BY status;

COMMIT;
