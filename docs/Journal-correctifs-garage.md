---
projet: Garage
type: journal de correctifs
date: 2026-10-28
tags: [garage, spring-boot, angular, securite, csrf, responsive]
---

# Journal des correctifs : Garage (28/10/2026)

## 1. Déplacements de rendez-vous : un coup sur deux refusé (401)

**Symptôme** : dans le calendrier, le premier glisser-déposer d'un rendez-vous fonctionnait, le deuxième échouait avec « Une erreur est survenue » et l'app renvoyait vers la page de connexion. Le troisième refonctionnait, et ainsi de suite.

**Diagnostic** (reproduit en envoyant 4 `PATCH /api/appointments/{id}/reschedule` de suite) : réponses 200, 401, 200, 401. Après chaque requête réussie, le cookie `XSRF-TOKEN` avait disparu du navigateur. La requête suivante partait donc sans l'en-tête `X-XSRF-TOKEN` et Spring Security la refusait (401). Cette réponse 401 reposait le cookie, d'où l'alternance.

**Cause** : le back-end authentifie chaque requête lui-même avec un filtre JWT (`JwtAuthFilter`), sans session. Avec Spring Security 6, chaque requête authentifiée est traitée comme une nouvelle connexion, et la stratégie CSRF par défaut (`CsrfAuthenticationStrategy`) **supprime le cookie `XSRF-TOKEN`** quand la requête en porte un. Constaté avec `cookieStore` dans le navigateur : un événement `DEL` après chaque requête qui envoyait le cookie, un `SET` après la suivante. Même un simple GET provoquait l'alternance.

**Correction** : dans `SecurityConfig`, `csrf(...).sessionAuthenticationStrategy(new NullAuthenticatedSessionStrategy())`. Le cookie reste en place.

**Première tentative inutile** : déclarer le contexte dans la requête (`RequestAttributeSecurityContextRepository.saveContext`) n'a rien changé, car en mode stateless le filtre de session de Spring utilise un dépôt nul dont `containsContext` est toujours faux. Annulée.

**À retenir** :
- Avec un filtre JWT maison en stateless sous Spring Security 6, neutraliser la stratégie CSRF de session (`NullAuthenticatedSessionStrategy`), sinon le cookie CSRF est effacé à chaque requête.
- Pour diagnostiquer un cookie qui disparaît : écouter `cookieStore.addEventListener('change', ...)` dans la console du navigateur.
- En JWT + cookie CSRF, un 401 sur une requête qui modifie des données peut venir d'un jeton CSRF absent, pas d'une session expirée.
- Tout message « Une erreur est survenue » avec retour à la connexion venait de ce 401 : l'intercepteur front traite tout 401 comme « session expirée ».

Fichier : `backend/src/main/java/com/garage/config/SecurityConfig.java`.

## 2. Menu non adapté au téléphone

**Symptôme** : sur téléphone, le menu latéral passait tout en haut de la page, sans se replier.

**Correction** : remplacement du menu latéral par le composant PrimeNG `p-menubar` (barre sticky en haut). Sous 1100 px, les entrées passent dans un menu hamburger. Entrées selon le rôle, pastille sur la page active, nom de l'utilisateur masqué et déconnexion en icône sur petit écran.

Fichiers : `frontend/src/app/layout/shell.{html,ts,css}`.

## 3. Données de démonstration en masse (Render)

- Générateur : `scripts/seed/generate_seed.py` (600 clients, 849 véhicules, 4 000 rendez-vous, devis et factures). Mot de passe des comptes clients de test : voir le script.
- Insertion sur Render par le back-end (`DemoDataSeeder`, variable `SEED_ENABLED=true`), car la base Render n'est pas joignable depuis l'environnement de Claude.
- **Piège** : la variable s'était d'abord appelée `SEED_ENABLE` (sans D) et ne faisait rien.
- 9 devis avaient un total différent de la somme de leurs lignes d'un centime (arrondi flottant dans Python). Le job de facturation les a refusés (« Montant invalide »). Corrigé par un réalignement des totaux au démarrage et par un arrondi `Decimal` dans le générateur.
- Le job de facturation tourne en synchrone : sur le plan gratuit, il prend plus d'une minute pour environ 1 000 factures.
