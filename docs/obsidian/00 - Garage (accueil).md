---
projet: Garage
type: hub
tags: [garage, accueil]
---

# Garage : application de gestion d'un garage automobile

Ce coffre explique le projet **de zéro**, comme à un élève qui découvre : à quoi il sert, comment il est construit, comment on l'a mis en ligne et quels problèmes on a rencontrés.

> Aucune donnée personnelle ni secret (mots de passe, clés, adresses de base de données) n'est écrit ici. Les secrets vivent dans des variables d'environnement, jamais dans le code ni dans ces notes.

## Plan de lecture
1. [[01 - Vue d'ensemble]] : à quoi sert l'app, qui l'utilise
2. [[02 - Architecture]] : les grandes pièces et comment elles se parlent
3. [[03 - Base de données]] : les tables et les règles
4. [[04 - Back-end Spring Boot]] : l'API
5. [[05 - Sécurité]] : connexion, JWT, CSRF, rôles
6. [[06 - Front-end Angular]] : les écrans
7. [[07 - Facturation et Spring Batch]] : les factures automatiques
8. [[08 - Déploiement]] : Render + Vercel, pas à pas
9. [[09 - Données de démonstration]] : remplir la base en masse
10. [[10 - Problèmes rencontrés]] : les bugs, leur cause, la solution
11. [[11 - Glossaire]] : les mots techniques expliqués simplement
12. [[12 - Pièces et stock]] : le catalogue, les réservations par un devis

## En deux phrases
Un garage veut gérer ses **clients**, leurs **véhicules**, ses **rendez-vous** (dans un calendrier), ses **devis**, son **stock de pièces** et ses **factures**. Un site web (Angular) parle à un serveur (Spring Boot) qui range tout dans une base de données (PostgreSQL). C'est un outil **interne** à l'usage du garage (administrateur et employés) : pas de portail client.
