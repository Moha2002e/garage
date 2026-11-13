---
projet: Garage
tags: [garage, architecture]
---

# Architecture

Retour à [[00 - Garage (accueil)]].

## Les trois grandes pièces
```mermaid
flowchart LR
  N[Navigateur<br/>de l'utilisateur] -->|HTTPS| F[Front Angular<br/>hébergé sur Vercel]
  F -->|/api/... réécrit par Vercel| B[Back Spring Boot<br/>hébergé sur Render]
  B -->|JDBC| DB[(PostgreSQL<br/>sur Render)]
```

- **Front-end** (ce que l'on voit) : Angular + PrimeNG. Il tourne dans le navigateur.
- **Back-end** (le cerveau) : Spring Boot (Java 21). Il applique les règles, vérifie les droits et parle à la base.
- **Base de données** : PostgreSQL. Elle garde les données de façon durable.

Le front et le back communiquent par une **API REST** : le front envoie des requêtes HTTP (`GET /api/clients`, `POST /api/appointments`, ...) et reçoit du JSON.

## Organisation du dépôt (monorepo)
```
Garage/
├── backend/    # Spring Boot (Maven)
├── frontend/   # Angular (npm)
├── scripts/    # générateur de données de démo
└── docs/       # ces notes
```
Un seul dépôt Git contient les deux applications : plus simple à versionner et à déployer ensemble.

## Pourquoi une seule adresse pour le front et l'API ?
Sur Vercel, le fichier `vercel.json` **réécrit** (`rewrites`) toutes les adresses `/api/...` vers le serveur Render. Pour le navigateur, tout vient du même site. Avantage : les **cookies** (connexion et jeton CSRF) fonctionnent sans les problèmes d'origines différentes. Détail dans [[08 - Déploiement]].

## Environnements
| | Local | En ligne |
|---|---|---|
| Front | `localhost:4200` | Vercel |
| Back | `localhost:8080` | Render (Docker) |
| Base | PostgreSQL locale | PostgreSQL Render |
| Profil Spring | par défaut | `prod` (cookies sécurisés, Swagger coupé) |
