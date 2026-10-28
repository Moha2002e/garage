# Cahier des charges des corrections du projet garage

Contexte : application de gestion d'un garage (Spring Boot + Angular/PrimeNG, rôles ADMIN, EMPLOYEE, CLIENT). L'application existe déjà et fonctionne. Ce document décrit les améliorations à faire. Le back-end et le front-end sont développés en parallèle par deux agents : le contrat d'API ci-dessous est la référence commune, à respecter exactement (noms de champs et chemins compris).

Conventions existantes à garder : préfixe `/api`, identifiants UUID, pages en `PageResponse` (`content`, `page`, `size`, `totalElements`, `totalPages`), dates en ISO 8601 (UTC), fuseau métier Europe/Paris, messages d'erreur en anglais, suppression jamais définitive.

## 1. Véhicules : page dédiée et désactivation

Règles
- Un véhicule a un état actif ou non. Rien n'est supprimé physiquement.
- Désactiver (DELETE) est réservé à l'ADMIN. Refusé avec un message clair si le véhicule a un rendez-vous PLANNED ou IN_PROGRESS.
- Réactiver est réservé à l'ADMIN.
- Les listes de véhicules d'un client et les choix de véhicule d'un rendez-vous ne montrent que les véhicules actifs.
- La plaque reste une donnée personnelle : jamais dans les logs ni dans les messages d'erreur.

Contrat
- `VehicleResponse` : `{ id, clientId, clientFirstName, clientLastName, plate, brand, model, active }`
- `GET /api/vehicles?q=&active=&page=&size=` (ADMIN, EMPLOYEE) : `PageResponse<VehicleResponse>`. `q` cherche dans plaque, marque, modèle, nom et prénom du client. `active` est `true` ou `false`, absent = tous. Tri par marque puis modèle. Taille par défaut 10, maximum 25.
- `GET /api/clients/{clientId}/vehicles` (ADMIN, EMPLOYEE) : liste de `VehicleResponse` actifs seulement (existant, champs ajoutés).
- `POST /api/vehicles` et `PUT /api/vehicles/{id}` : corps inchangé `{ clientId, plate, brand, model }`.
- `DELETE /api/vehicles/{id}` (ADMIN) : désactive, 204.
- `PATCH /api/vehicles/{id}/reactivate` (ADMIN) : réactive, renvoie `VehicleResponse`.

## 2. Rendez-vous plus complets

Règles
- Nouveau champ `durationMinutes` (entier, de 15 à 480, par pas de 15). Valeur 60 pour les rendez-vous existants (migration).
- Nouveau statut `IN_PROGRESS`. Transitions autorisées : PLANNED vers IN_PROGRESS, COMPLETED ou CANCELLED ; IN_PROGRESS vers COMPLETED ou CANCELLED. COMPLETED et CANCELLED sont finaux.
- Les dates passées sont acceptées à la création et à la modification (pour saisir l'historique). Plus d'obligation de date future.
- Modification (PUT) permise seulement pour un rendez-vous PLANNED, comme aujourd'hui.
- Conflits d'horaire. Un rendez-vous occupe l'intervalle `[date, date + durationMinutes)`. Refus avec une erreur 409 (message clair en anglais) si, parmi les rendez-vous PLANNED ou IN_PROGRESS (hors le rendez-vous modifié lui-même) : le même véhicule a déjà un rendez-vous qui chevauche l'intervalle, ou le nombre de rendez-vous simultanés atteint la capacité du garage. La capacité vient de la configuration `app.garage.capacity` (valeur par défaut 3).
- Un client ne voit toujours que ses propres rendez-vous, en lecture seule.

Contrat
- `AppointmentRequest` : `{ clientId, vehicleId, date, durationMinutes, reason }`
- `AppointmentResponse` : champs existants plus `durationMinutes` (entier), `quoteStatus` (`DRAFT`, `ACCEPTED`, `REFUSED` ou `null`) et `quoteTotal` (nombre à 2 décimales ou `null`).
- `PATCH /api/appointments/{id}/status` : corps inchangé `{ status }`, accepte maintenant `IN_PROGRESS`.
- Statistiques : `MonthStat` devient `{ month, planned, inProgress, completed, cancelled }`.

## 3. Devis

Règles
- Un rendez-vous a au plus un devis, composé de lignes. Montants en euros, `BigDecimal` à 2 décimales. Le total est calculé par le back-end (somme de quantité fois prix unitaire).
- Statuts du devis : `DRAFT` (modifiable), `ACCEPTED`, `REFUSED` (finals).
- Créer ou remplacer les lignes : seulement tant que le devis est `DRAFT` (ou n'existe pas), et seulement si le rendez-vous n'est pas CANCELLED. Au moins une ligne, au plus 50.
- Accepter ou refuser : depuis `DRAFT` seulement.
- ADMIN et EMPLOYEE écrivent. Un CLIENT peut lire le devis de ses propres rendez-vous (contrôle dans la couche service, 404 pour les autres).

Contrat
- `QuoteResponse` : `{ id, appointmentId, status, lines: [{ id, description, quantity, unitPrice, lineTotal }], total, updatedAt }`
- `GET /api/appointments/{id}/quote` : `QuoteResponse`, ou 204 sans corps s'il n'y a pas encore de devis.
- `PUT /api/appointments/{id}/quote` (ADMIN, EMPLOYEE) : corps `{ lines: [{ description, quantity, unitPrice }] }`. Description non vide (255 maximum), quantité supérieure à 0 (jusqu'à 2 décimales), prix unitaire supérieur ou égal à 0. Crée ou remplace, renvoie `QuoteResponse`.
- `PATCH /api/appointments/{id}/quote/status` (ADMIN, EMPLOYEE) : corps `{ status }` avec `ACCEPTED` ou `REFUSED`, renvoie `QuoteResponse`.

## 4. Accueil et tableau de bord

Règles
- À la connexion, ADMIN et EMPLOYEE arrivent sur un tableau de bord. Le CLIENT arrive toujours sur ses rendez-vous.
- Le tableau de bord montre les rendez-vous du jour (jour calendaire Europe/Paris, statuts PLANNED et IN_PROGRESS, triés par heure) et des chiffres clés.

Contrat
- `GET /api/dashboard` (ADMIN, EMPLOYEE) renvoie :
  `{ today: AppointmentResponse[], plannedNext7Days, inProgress, activeClients, activeVehicles, monthCompleted, monthRevenue }`
  `plannedNext7Days` : nombre de rendez-vous PLANNED dans les 7 prochains jours. `inProgress` : nombre de rendez-vous IN_PROGRESS. `monthCompleted` : rendez-vous COMPLETED du mois en cours (Europe/Paris). `monthRevenue` : somme des totaux des devis `ACCEPTED` des rendez-vous COMPLETED du mois en cours.

- Champs ajoutés avec les factures (`todayAppointments`, `inProgressNow`, `upcoming`, `unpaidInvoicesCount`, `unpaidInvoicesAmount`, `pendingInvoiceCount`) : voir la section 9.4. Les sept champs ci dessus ne changent pas.

## 5. Statistiques

- Le graphique mensuel existant gagne une quatrième courbe « en cours » (`inProgress`).

## 5 bis. Calendrier, statistiques complémentaires et exports stylés

Calendrier
- `GET /api/appointments/calendar?from=ISO&to=ISO` (ADMIN, EMPLOYEE, et CLIENT limité à ses propres rendez-vous) renvoie, sans pagination, `CalendarEvent[]` : `{ id, start, end, title, status, clientName, vehicleLabel }`.
- `from` et `to` sont des instants ISO 8601 (ex. `2026-10-01T00:00:00Z`; un décalage comme `+02:00` doit être encodé en `%2B02:00`). La plage est `[from, to)`, `to` doit être après `from`, et elle ne dépasse pas 100 jours (sinon 400).
- Chaque événement porte aussi `durationMinutes` (entier) et `progressPercent` (entier de 0 à 100, calculé par le serveur) : `{ id, start, end, title, status, clientName, vehicleLabel, durationMinutes, progressPercent }`. Pour un rendez-vous IN_PROGRESS, `progressPercent` est la part du temps écoulé entre `start` et `end` à l'instant présent, bornée de 0 à 100 ; COMPLETED vaut 100 ; PLANNED et CANCELLED valent 0. La réponse est plafonnée à 2000 événements (les plus anciens par début).
- Un événement est renvoyé s'il chevauche la plage. `end` = `start` + `durationMinutes`, `title` = motif du rendez-vous, `clientName` = « Prénom Nom », `vehicleLabel` = « Marque Modèle » (sans plaque). Tri par début croissant. Un compte CLIENT sans fiche client liée reçoit une liste vide.

Déplacement par glisser déposer
- `PATCH /api/appointments/{id}/reschedule` (ADMIN et EMPLOYEE, 403 pour un CLIENT), corps `{ "date": "<instant ISO 8601>" }`. La durée, le client et le véhicule ne changent pas. Renvoie `AppointmentResponse`.
- Seul un rendez-vous PLANNED peut être déplacé (sinon 400, comme la modification). La date doit tomber entre les années 2000 et 2100 en Europe/Paris (sinon 400). Rendez-vous inconnu : 404.
- Mêmes règles de conflit que la création et la modification : le même véhicule ne peut pas être réservé deux fois en même temps, et le nombre de rendez-vous simultanés ne dépasse pas la capacité du garage (409 sinon). Le rendez-vous déplacé est exclu du calcul de chevauchement.

Statistiques (ADMIN, EMPLOYEE; `year` facultatif, année courante par défaut, entre 2000 et 2100)
- `GET /api/statistics/status-distribution?year=` renvoie `[{ status, count }]` pour les 4 statuts de rendez-vous (toujours présents, même à 0), mois comptés en Europe/Paris.
- `GET /api/statistics/revenue-by-month?year=` renvoie toujours 12 éléments `[{ month (1 à 12), revenue }]`. Le chiffre d'affaires est la somme des totaux des devis `ACCEPTED` (le seul statut de `QuoteStatus` qui engage le client : `DRAFT` n'est pas encore accepté, `REFUSED` est perdu) des rendez-vous `COMPLETED` (travail réalisé), au mois du rendez-vous (Europe/Paris). C'est la même règle que `monthRevenue` du tableau de bord. Mois sans chiffre : `0.00`.
- `GET /api/statistics/top-brands?limit=5` renvoie `[{ brand, count }]`, marques les plus fréquentes parmi les véhicules actifs (regroupement sans tenir compte de la casse), `limit` de 1 à 20 (défaut 5, sinon 400).

Exports Excel (`/api/exports/clients.xls`, `/api/exports/appointments.xls`, ADMIN) : mêmes URL et même format SpreadsheetML (.xls) qu'avant, mais stylés : ligne de titre « Garage », sous-titre avec le nom de la feuille, en-têtes en gras blanc sur fond teal, lignes alternées en gris très clair, largeurs de colonnes fixées. Statut client : Active en vert, Inactive en rouge. Statut de rendez-vous : PLANNED bleu, IN_PROGRESS orange, COMPLETED vert, CANCELLED rouge. Pas de logo image (le format SpreadsheetML ne sait pas embarquer d'image).

## 6. Front-end : écrans attendus

- Menu (ADMIN, EMPLOYEE) : Accueil, Clients, Véhicules, Rendez-vous, Statistiques, Utilisateurs, Paramètres. Le CLIENT garde Rendez-vous et Paramètres.
- Accueil (`/dashboard`) : cartes des chiffres clés en grille responsive, puis table des rendez-vous du jour. Une ligne ouvre la page des rendez-vous.
- Véhicules (`/vehicles`, ADMIN et EMPLOYEE) : `p-table` paginée côté back-end, barre de recherche unique, filtre actif ou inactif, ligne cliquable avec un seul bouton Détail, fenêtre de détail centrée (p-dialog) en lecture seule avec Modifier et Supprimer (la suppression est une désactivation, visible pour l'ADMIN seulement), bouton Réactiver pour l'ADMIN sur un véhicule inactif, bouton « Nouveau véhicule » avec choix du client par autocomplétion.
- Rendez-vous : champ durée dans le formulaire (choix par pas de 15 minutes), plus de date minimale, colonne ou texte indiquant l'heure de fin, tag du statut « En cours », actions de la fenêtre « Démarrer », « Terminer », « Annuler » selon le statut. Les erreurs 409 de conflit affichent le message du back-end.
- Devis dans la fenêtre d'un rendez-vous : s'il n'y a pas de devis, bouton « Créer un devis » (ADMIN, EMPLOYEE). Sinon la liste des lignes (description, quantité, prix unitaire, total de ligne) et le total général, avec le statut. En mode édition (devis DRAFT), ajouter et retirer une ligne (le retrait d'une ligne dans cet éditeur est une exception à la règle « un seul bouton par ligne », qui vise les tables de listes), puis Enregistrer. Boutons Accepter et Refuser pour un devis DRAFT. Le CLIENT voit le devis en lecture seule.
- Statistiques : quatrième courbe « en cours ».
- Toutes les nouvelles chaînes sont dans `public/i18n/fr.json` et `en.json` (mêmes clés dans les deux).

## 7. Consignes communes de travail

- Mode cahier des charges fourni : pas de QCM. Les points non précisés se décident par la réponse la plus raisonnable, listée sous « Hypothèses ».
- Aucune opération git (ni branche, ni commit, ni stash) : le shell de la machine ne peut pas supprimer les fichiers de verrou git, ce qui casserait le dépôt. L'utilisateur fera le commit.
- Ne touche pas aux fichiers `.env`, `node_modules` et `target` du projet.
- Mets à jour les tests existants que tes changements cassent, et ajoute des tests pour le nouveau comportement.

## Règles d'interface (ajout)

- Aucun tiroir latéral : détails, formulaires et confirmations dans une fenêtre modale centrée (`p-dialog`).
- Rendez-vous affichés dans un calendrier (mois et semaine), événements colorés par statut.
- Contenus centrés (Paramètres dans une carte de largeur limitée), menu sans changement de taille au clic, pastille sur la page active.
- Statistiques : courbe, anneau de répartition par statut, barres par mois, top marques.
- Couleurs de statut utiles seulement (actif vert, inactif rouge, etc.), logo dans le menu et la connexion.

## 8. Fiches client et véhicule, gestion des comptes (contrat de l'API)

Toutes les routes exigent une session authentifiée. Les erreurs ont la forme habituelle (`timestamp`, `status`, `message`, et `errors` pour une validation). Un accès à l'objet d'un autre client renvoie 404 (jamais 403), pour ne pas révéler son existence.

### 8.1 Clients

| Route | Rôles | Réponse |
|---|---|---|
| `GET /api/clients/{id}` | ADMIN, EMPLOYEE | `ClientResponse` |
| `GET /api/clients/{id}/vehicles` | ADMIN, EMPLOYEE | `VehicleResponse[]`, véhicules actifs et inactifs, triés par plaque. Paramètre optionnel `active=true` : actifs seulement |
| `GET /api/clients/{id}/appointments?page=0&size=10` | ADMIN, EMPLOYEE | `PageResponse<AppointmentResponse>`, date décroissante |

`ClientResponse` : `{ id, lastName, firstName, email, phone, active, createdAt }`. Le modèle client n'a pas d'adresse : aucun champ `address` n'est renvoyé. `createdAt` est un instant ISO 8601 (UTC). `phone` peut être null.

### 8.2 Véhicules

| Route | Rôles | Réponse |
|---|---|---|
| `GET /api/vehicles/{id}` | ADMIN, EMPLOYEE, CLIENT (ses propres véhicules) | `VehicleResponse` |
| `GET /api/vehicles/{id}/appointments?page=0&size=10` | ADMIN, EMPLOYEE, CLIENT (ses propres véhicules) | `PageResponse<AppointmentResponse>`, date décroissante |

`VehicleResponse` : `{ id, clientId, clientFirstName, clientLastName, plate, brand, model, active }`.

### 8.3 Historique des rendez-vous (clients et véhicules)

- Tri : date décroissante (puis identifiant, pour un ordre stable).
- `size` : de 1 à 50 (plafonné à 50, valeur invalide ramenée à 10) ; `page` commence à 0 (négatif ramené à 0). Ces deux routes ne suivent donc pas la règle 10 ou 25 des autres listes.
- `PageResponse` : `{ content, page, size, totalElements, totalPages }`.
- `AppointmentResponse` : `{ id, date, reason, status, clientId, clientFirstName, clientLastName, vehicleId, plate, brand, model, durationMinutes, quoteStatus, quoteTotal }` (`quoteStatus` et `quoteTotal` null sans devis).

### 8.4 Comptes utilisateurs

| Route | Rôles | Corps / réponse |
|---|---|---|
| `GET /api/users?q=&role=&page=0&size=10` | ADMIN, EMPLOYEE | `PageResponse<UserResponse>` (taille 10 ou 25), tri nom puis prénom |
| `POST /api/users` | ADMIN | corps `CreateUserRequest`, réponse 201 `UserResponse` |
| `PUT /api/users/{id}` | ADMIN | corps `UpdateUserRequest`, réponse 200 `UserResponse` |
| `PUT /api/users/{id}/role` | ADMIN | existant, inchangé |
| `PUT /api/users/{id}/client` | ADMIN, EMPLOYEE | existant, inchangé |

- `UserResponse` : `{ id, email, firstName, lastName, role, language, enabled, clientId }` (jamais de mot de passe).
- `CreateUserRequest` : `{ firstName, lastName, email, password, role }`. Prénom et nom non vides (100 caractères au plus), email valide et unique sans tenir compte de la casse (enregistré en minuscules), `role` parmi ADMIN, EMPLOYEE, CLIENT. Mot de passe de 12 à 72 caractères avec au moins une majuscule, une minuscule et un chiffre. Email déjà pris : 409. Validation en échec : 400 avec `errors` par champ. Le compte est créé actif, en français.
- `UpdateUserRequest` : `{ firstName, lastName, role, enabled }`, tous obligatoires. L'email et le mot de passe ne changent pas ici. Passer à un rôle autre que CLIENT supprime le lien avec la fiche client.
- Désactivation logique : `PUT /api/users/{id}` avec `enabled: false`. Un compte désactivé ne peut plus se connecter ni utiliser une session existante. Aucune suppression physique.
- Garde fous : un administrateur ne peut ni se désactiver, ni retirer son propre rôle ADMIN (400).

### 8.5 Contrôle d'accès par objet

- Un CLIENT ne voit que les rendez-vous et véhicules de la fiche client liée à son compte. Un compte sans fiche liée ne voit rien (404 sur les véhicules).
- Le contrôle est fait dans les services, pas seulement par les annotations de rôle.
- Un CLIENT reçoit 403 sur toutes les routes `/api/clients/**` et sur la gestion des comptes.

### 8.6 Hypothèses

- L'ancienne route `GET /api/clients/{id}/vehicles` ne renvoyait que les véhicules actifs ; elle renvoie désormais tous les véhicules. Les écrans qui ont besoin des seuls véhicules actifs (choix du véhicule d'un rendez-vous) passent `?active=true`.
- Pas de route `DELETE /api/users/{id}` : la désactivation passe par `PUT /api/users/{id}`.

## 9. Factures et traitement batch (Spring Batch)

### 9.1 Règles

- Un rendez-vous est facturable quand son statut est `COMPLETED`, que son devis est `ACCEPTED` et qu'il n'a pas encore de facture. Le même critère donne `pendingInvoiceCount` du tableau de bord.
- Une facture est créée uniquement par le job `invoiceJob`. Il n'y a ni création, ni modification de contenu, ni suppression de facture par l'API : seul le paiement change une facture.
- Un rendez-vous a au plus une facture : `appointment_id` est `UNIQUE` et non nul dans la table `invoices`. Un rendez-vous déjà facturé n'est jamais recréé : relancer le job ne produit rien de plus (idempotence), même si deux lancements se croisent (la contrainte refuse le doublon et l'erreur `DUPLICATE_INVOICE` est enregistrée).
- Numéro : `FAC-AAAA-000001`. `AAAA` est l'année d'émission en Europe/Paris, le compteur vient de la séquence PostgreSQL `invoice_number_seq`. Le compteur est global : il ne repart pas à 1 chaque année. Une séquence n'est pas transactionnelle : si une facture prend un numéro puis n'est finalement pas écrite, ce numéro est perdu, la numérotation peut donc avoir des trous. Le numéro est pris en dernier, après toutes les vérifications, pour que les rendez-vous invalides n'en consomment pas.
- Statuts : `ISSUED` (émise, à la création) puis `PAID`. `issuedAt` est l'instant de création, `paidAt` est vide tant que la facture n'est pas payée.
- Les lignes sont une copie figée des lignes actives du devis (description, quantité, prix unitaire, total de ligne = quantité fois prix unitaire arrondi à 2 décimales, demi supérieur). Elles gardent l'ordre du devis (`sort_order`, colonne ajoutée à la liste demandée). Le total de la facture est la somme des totaux de ligne.
- Le job est lancé uniquement par `POST /api/invoices/batch/run`, jamais au démarrage (`spring.batch.job.enabled: false`). Il reçoit un paramètre unique (le timestamp en millisecondes), donc chaque appel est une nouvelle exécution. L'exécution est synchrone : la réponse arrive à la fin du job. Une seule exécution à la fois par instance du serveur : un second appel pendant une exécution reçoit 409.
- Le step lit un instantané des identifiants des rendez-vous facturables au démarrage du step, traite par lots de 20 rendez-vous (une transaction par lot) et écrit les factures.
- Une erreur sur un rendez-vous ne bloque pas les autres : le rendez-vous est ignoré (skip) et une ligne est écrite dans `batch_errors`, dans sa propre transaction (elle survit au retour arrière du lot). Un rendez-vous ignoré reste facturable : il sera retraité, et donc signalé de nouveau, à chaque lancement tant que son devis n'est pas corrigé.
- Le run échoue (`FAILED`) seulement si la base est injoignable, en cas d'erreur JVM, ou au delà de 1000 rendez-vous ignorés dans un même run.
- Les tables de métadonnées de Spring Batch (`BATCH_JOB_INSTANCE`, `BATCH_JOB_EXECUTION`, `BATCH_STEP_EXECUTION`, etc.) sont créées par Spring Boot (`spring.batch.jdbc.initialize-schema: always`), pas par Liquibase. Hibernate (`ddl-auto: validate`) les ignore car ce ne sont pas des entités. Le script est rejoué à chaque démarrage et les erreurs « already exists » sont ignorées par Spring Boot (elles apparaissent seulement dans les logs).
- Les messages de `batch_errors` ne contiennent aucune donnée personnelle (ni nom, ni plaque, ni texte d'erreur SQL).

### 9.2 Codes d'erreur de `batch_errors`

Les codes sont stables : le front peut s'appuyer dessus.

| Code | Sens |
|---|---|
| `NO_ACCEPTED_QUOTE` | Le rendez-vous n'a pas de devis `ACCEPTED` au moment du traitement. Défensif : la sélection ne retient que des rendez-vous avec devis accepté, et un devis accepté est final. |
| `EMPTY_QUOTE` | Le devis accepté n'a aucune ligne active. |
| `INVALID_AMOUNT` | Une quantité n'est pas supérieure à 0, un prix unitaire est négatif, le total n'est pas supérieur à 0, le total dépasse 9 999 999 999,99, ou le total du devis diffère de la somme de ses lignes. |
| `DUPLICATE_INVOICE` | Le rendez-vous a déjà une facture (détecté avant l'écriture, ou refusé par la contrainte `uq_invoices_appointment`). |
| `UNEXPECTED` | Toute autre erreur (rendez-vous introuvable ou non terminé, erreur technique). Le message ne donne que le type de l'exception. |

### 9.3 Contrat de l'API

Rôles : ADMIN et EMPLOYEE sur toutes les routes de cette section, contrôlés sur chaque route. Un CLIENT reçoit 403, un visiteur anonyme 401. Les listes suivent la règle habituelle : `page` à partir de 0, `size` 10 ou 25 (toute autre valeur est ramenée à 10). Les écritures exigent le jeton CSRF. Les montants sont en euros, à 2 décimales.

| Route | Réponse |
|---|---|
| `POST /api/invoices/batch/run` | 200 `BatchRunResponse` (exécution synchrone), 409 si un run est en cours |
| `GET /api/invoices/batch/runs?page&size` | `PageResponse<BatchRunResponse>`, exécutions les plus récentes d'abord |
| `GET /api/invoices/batch/errors?page&size&runId` | `PageResponse<BatchErrorResponse>`, erreurs les plus récentes d'abord. `runId` est facultatif (toutes les exécutions sans lui) |
| `GET /api/invoices?page&size&status&q` | `PageResponse<InvoiceResponse>`, émission la plus récente d'abord. `status` = `ISSUED` ou `PAID` (autre valeur : 400). `q` : 100 caractères au plus (sinon 400), recherche dans le numéro, le nom et prénom du client, la marque, le modèle et la plaque du véhicule |
| `GET /api/invoices/{id}` | `InvoiceDetailResponse`, 404 si inconnue |
| `PUT /api/invoices/{id}/pay` | 200 `InvoiceDetailResponse` avec `status` `PAID` et `paidAt` renseigné ; 404 si inconnue ; 409 si déjà payée |
| `GET /api/exports/invoices.xls?status&q` | Export Excel, ADMIN seulement (403 pour EMPLOYEE), plafonné à 5000 lignes |

`BatchRunResponse` :

```json
{
  "id": 12,
  "status": "COMPLETED",
  "readCount": 10,
  "writeCount": 8,
  "skipCount": 2,
  "errorCount": 2,
  "startTime": "2026-10-25T09:00:00Z",
  "endTime": "2026-10-25T09:00:04Z"
}
```

`id` est l'identifiant d'exécution Spring Batch (entier long). `status` est le `BatchStatus` (`COMPLETED`, `FAILED`, `STARTED`, `STOPPED`, etc.). `readCount` : rendez-vous lus. `writeCount` : factures écrites. `skipCount` : rendez-vous ignorés. `errorCount` : lignes de `batch_errors` de ce run. `endTime` est null tant que le run n'est pas fini.

`BatchErrorResponse` :

```json
{
  "id": "0b6c1d52-6a39-4d2e-9d7a-0d9f6d1b9c11",
  "runId": 12,
  "appointmentId": "7f3c2a10-2c4b-4c43-8a55-3a9a4f0e5d21",
  "appointmentLabel": "20/10/2026 14:30, Jean Dupont, Renault Clio",
  "code": "EMPTY_QUOTE",
  "message": "Accepted quote has no line",
  "occurredAt": "2026-10-25T09:00:02Z"
}
```

`appointmentLabel` = « jj/mm/aaaa hh:mm, Prénom Nom, Marque Modèle » (Europe/Paris), ou null si l'erreur n'est pas liée à un rendez-vous ou si le rendez-vous est introuvable. `appointmentId` peut être null.

`InvoiceResponse` :

```json
{
  "id": "5d1c9e0a-9d37-4a39-8d0c-7c7b0c1f2a10",
  "number": "FAC-2026-000001",
  "status": "ISSUED",
  "appointmentId": "7f3c2a10-2c4b-4c43-8a55-3a9a4f0e5d21",
  "clientId": "c2a8f2f4-3e0b-4f6e-9a14-9b6d1d2f7e33",
  "clientName": "Jean Dupont",
  "vehicleLabel": "Renault Clio",
  "appointmentDate": "2026-10-20T12:30:00Z",
  "total": 181.00,
  "issuedAt": "2026-10-25T09:00:03Z",
  "paidAt": null
}
```

`InvoiceDetailResponse` : les mêmes champs, plus `lines`, dans l'ordre du devis :

```json
{
  "id": "5d1c9e0a-9d37-4a39-8d0c-7c7b0c1f2a10",
  "number": "FAC-2026-000001",
  "status": "PAID",
  "appointmentId": "7f3c2a10-2c4b-4c43-8a55-3a9a4f0e5d21",
  "clientId": "c2a8f2f4-3e0b-4f6e-9a14-9b6d1d2f7e33",
  "clientName": "Jean Dupont",
  "vehicleLabel": "Renault Clio",
  "appointmentDate": "2026-10-20T12:30:00Z",
  "total": 181.00,
  "issuedAt": "2026-10-25T09:00:03Z",
  "paidAt": "2026-10-26T14:10:00Z",
  "lines": [
    { "description": "Brake pads", "quantity": 2.00, "unitPrice": 45.50, "total": 91.00 },
    { "description": "Labour", "quantity": 1.50, "unitPrice": 60.00, "total": 90.00 }
  ]
}
```

`clientName` = « Prénom Nom », `vehicleLabel` = « Marque Modèle » (sans plaque), `appointmentDate` = début du rendez-vous facturé.

### 9.4 Tableau de bord

`GET /api/dashboard` garde ses 7 champs (`today`, `plannedNext7Days`, `inProgress`, `activeClients`, `activeVehicles`, `monthCompleted`, `monthRevenue`, avec le même sens qu'à la section 4, y compris le chiffre d'affaires du mois) et gagne :

```json
{
  "todayAppointments": [
    { "id": "…", "start": "2026-10-25T08:00:00Z", "end": "2026-10-25T09:30:00Z", "status": "COMPLETED", "clientName": "Jean Dupont", "vehicleLabel": "Renault Clio" }
  ],
  "inProgressNow": [ { "id": "…", "start": "…", "end": "…", "status": "IN_PROGRESS", "clientName": "…", "vehicleLabel": "…" } ],
  "upcoming": [ { "id": "…", "start": "…", "end": "…", "status": "PLANNED", "clientName": "…", "vehicleLabel": "…" } ],
  "unpaidInvoicesCount": 3,
  "unpaidInvoicesAmount": 420.50,
  "pendingInvoiceCount": 2
}
```

- `todayAppointments` : rendez-vous du jour calendaire Europe/Paris, statuts `PLANNED`, `IN_PROGRESS` et `COMPLETED` (pas les annulés), par heure de début, 20 au plus.
- `inProgressNow` : rendez-vous `IN_PROGRESS` (même ensemble que le compteur `inProgress`, quelle que soit leur date), par début, 20 au plus.
- `upcoming` : les 5 prochains rendez-vous `PLANNED` dont le début est à partir de maintenant.
- `end` = `start` + durée. `clientName` et `vehicleLabel` comme pour les factures.
- `unpaidInvoicesCount` et `unpaidInvoicesAmount` : nombre et somme des totaux des factures `ISSUED` (`0.00` s'il n'y en a pas).
- `pendingInvoiceCount` : rendez-vous `COMPLETED` avec devis `ACCEPTED` sans facture. Supérieur à 0, le front peut proposer de lancer le batch.

### 9.5 Export Excel

`GET /api/exports/invoices.xls` (ADMIN) : même format SpreadsheetML et même style que les autres exports. Colonnes : Number, Status, Issued, Paid, Client, Vehicle, Appointment, Total (EUR). Statut `ISSUED` en orange, `PAID` en vert. Les paramètres facultatifs `status` et `q` sont ceux de la liste.

### 9.6 Hypothèses

- Les messages de `batch_errors` sont en anglais, comme les autres messages de l'API ; le front les traduit à partir du `code`.
- L'heure de début et de fin d'un run vient de Spring Batch, qui l'enregistre dans le fuseau du serveur ; l'API la renvoie en instant UTC.
- Le total des factures dans l'export est écrit comme du texte (« 181.00 »), comme les autres cellules du modèle d'export actuel.
- Aucun cumul de numéros par année : voir 9.1.

### 9.7 PDF d'une facture

`GET /api/invoices/{id}/pdf` (ADMIN et EMPLOYEE, `CLIENT` reçoit 403, sans session 401) : télécharge la facture au format PDF A4.

- Réponse 200 : `Content-Type: application/pdf`, `Content-Disposition: attachment; filename="FAC-2026-000001.pdf"` (le nom du fichier est uniquement le numéro de la facture, tout caractère inattendu est remplacé), `Cache-Control: no-store`. Le corps est le PDF (il commence par `%PDF`).
- 404 si la facture n'existe pas. Un échec de rendu renvoie 500 avec le message neutre « The PDF could not be generated », sans trace ni donnée personnelle (seul le type de l'exception est journalisé).
- Génération : `InvoicePdfService` rend le modèle FreeMarker `templates/invoices/invoice.ftl` en XHTML bien formé (échappement automatique XML de toutes les valeurs) puis le convertit avec `openhtmltopdf-pdfbox` 1.0.10. Polices standard (Helvetica), aucun fichier de police externe. Les caractères que Helvetica ne sait pas dessiner (autres alphabets, émojis) sont remplacés par `?`.
- Contenu : en-tête avec logo (`templates/invoices/logo.png`, intégré en base64) et coordonnées du garage ; titre « FACTURE » et numéro ; date d'émission, échéance (émission plus 30 jours), statut, date du rendez-vous ; blocs Client (nom) et Véhicule (marque, modèle, plaque) ; tableau des lignes (description, quantité, prix unitaire, total de ligne, lignes alternées) ; totaux ; paiement ; pied de page avec numéro de TVA et coordonnées.
- Totaux : Total HT = `invoice.total` (les montants des devis sont hors TVA), TVA = HT × taux ÷ 100, Total TTC = HT + TVA, chaque montant arrondi à 2 décimales (HALF_UP).
- Paiement : statut `PAID` affiche « PAYÉE le JJ/MM/AAAA » en vert ; sinon « À payer avant le JJ/MM/AAAA » avec IBAN, BIC et communication (le numéro de la facture).
- Formats : montants « 1 234,56 € » (espace insécable), dates JJ/MM/AAAA, fuseau Europe/Paris, locale `fr`.
- Configuration (`application.yml`, surchargeable par variables d'environnement, voir `.env.example`) : `app.garage.company` (name, address, postalCode, city, country, vatNumber, iban, bic, email, phone) et `app.invoice.vatRate` (défaut 21). Les valeurs par défaut sont des exemples fictifs (`BE0000.000.000`, IBAN factice) : renseigner les vraies valeurs dans `.env`.
- Hypothèses : la TVA est calculée sur le total de la facture et non ligne par ligne ; aucune donnée du client autre que son nom n'est imprimée ; la facture n'est jamais enregistrée en tant que fichier, elle est générée à chaque appel.
