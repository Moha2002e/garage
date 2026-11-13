---
projet: Garage
tags: [garage, frontend, angular, primeng]
---

# Front-end Angular

Retour à [[00 - Garage (accueil)]].

## Technologies
Angular 22 (composants autonomes, signaux), PrimeNG 22 (composants visuels), Chart.js (graphiques), ngx-translate (FR/EN).

## Structure de `frontend/src/app`
| Dossier | Contenu |
|---|---|
| `pages/` | Une page par écran : login, dashboard, clients, client-detail, vehicles, vehicle-detail, appointments, parts, invoices, invoice-errors, users, settings... (pas de page `register` : la création de compte se fait sur la page `users`, réservée ADMIN) |
| `services/` | Un service par ressource (`client.service.ts`...) : ils appellent l'API |
| `layout/` | La **coque** (`shell`) : barre de navigation + zone de page |
| `core/` | Intercepteur HTTP, gardes de route, utilitaires |
| `shared/` | Morceaux réutilisés (historique des rendez-vous...) |
| `models/` | Les types TypeScript qui décrivent les réponses de l'API |

## Les idées importantes
- **Routing + gardes** : `auth.guard.ts` empêche d'ouvrir une page privée sans être connecté ; `unsaved-changes.guard.ts` prévient si on quitte un formulaire modifié.
- **Intercepteur HTTP** (`http.interceptor.ts`) : pour chaque appel `/api`, il active l'envoi des cookies et ajoute l'en-tête `X-XSRF-TOKEN` ([[05 - Sécurité]]). Il gère aussi les erreurs communes : un 401 renvoie vers la connexion, 403 vers l'accueil, une panne affiche un message.
- **Signaux** : l'état de l'écran est dans des `signal()` ; `computed()` recalcule ce qui en dépend.
- **Traductions** : les textes sont dans `public/i18n/*.json`, jamais écrits en dur dans les pages.

## Le calendrier des rendez-vous
Écrit à la main (`appointment-calendar`) : vues jour/semaine/mois, barre de l'heure actuelle, glisser-déposer par **événements pointeur** (souris et tactile).
**Mise à jour optimiste** : au dépôt, le rendez-vous bouge immédiatement ; puis le back valide. S'il refuse (conflit, capacité, statut), l'événement **revient** à sa place avec le message du back.

## Navigation responsive
La coque utilise le composant PrimeNG **`p-menubar`** : barre fixée en haut, qui devient un **menu hamburger** sous 1100 px. Les entrées dépendent du rôle ; la page active a une **pastille**. Sur petit écran le nom est masqué et la déconnexion devient une icône.

## Conventions d'interface demandées au départ
- Détails et formulaires dans une **fenêtre modale centrée** (pas de tiroir latéral)
- Couleurs seulement quand elles ont un sens (actif en vert, inactif en rouge)
- Plusieurs types de graphiques (dont circulaire), exports Excel stylés
