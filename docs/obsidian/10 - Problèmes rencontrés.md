---
projet: Garage
tags: [garage, bugs, apprentissage]
---

# Problèmes rencontrés et solutions

Retour à [[00 - Garage (accueil)]]. Chaque fiche : **symptôme → cause → solution → à retenir**.

## 1. Le back ne démarre pas sur Render : `database "garage" does not exist`
- **Cause** : Render ajoute un suffixe aléatoire au nom de la base (`garage_xxxx`).
- **Solution** : mettre le nom exact dans `DB_URL`, puis « Save and deploy ».
- **À retenir** : lire le nom réel dans le tableau de bord ; l'URL Java commence par `jdbc:postgresql://`, pas `postgres://`.

## 2. Sur Vercel, l'API n'était pas jointe
- **Cause** : le front appelait `http://localhost:8080`.
- **Solution** : un `sed` à la compilation vide cette adresse, et `vercel.json` réécrit `/api/*` vers Render. L'intercepteur ne cible plus que les URL en `/api`.

## 3. Les données de démo ne s'affichaient pas
- **Cause** : la variable s'appelait `SEED_ENABLE` (sans D) alors que le code attend `SEED_ENABLED`.
- **À retenir** : un nom de variable d'environnement doit être **exactement** celui du code.

## 4. 9 factures refusées : « Montant invalide »
- **Cause** : dans le générateur Python, les nombres flottants donnaient un écart d'un centime entre le total du devis et la somme des lignes.
- **Solution** : arrondi avec `Decimal` (HALF_UP) et réalignement des totaux au démarrage.
- **À retenir** : pour l'argent, **jamais de flottants** ; comparer des montants exacts.

## 5. « Une erreur est survenue » et retour à la connexion, un déplacement sur deux
**Symptôme** : glisser un rendez-vous marche, le deuxième échoue, le troisième marche...
**Diagnostic** : en envoyant 4 requêtes de suite : 200, 401, 200, 401. Après chaque requête, le cookie `XSRF-TOKEN` disparaissait ; la requête suivante partait sans jeton et recevait un 401, et l'intercepteur front traitait tout 401 comme « session expirée ».
**Cause** : filtre JWT maison + Spring Security 6 en stateless : chaque requête est vue comme une nouvelle connexion, et la stratégie CSRF par défaut **supprime le cookie** CSRF.
**Solution** : `csrf(...).sessionAuthenticationStrategy(new NullAuthenticatedSessionStrategy())` dans `SecurityConfig`.
**Une fausse piste** : déclarer le contexte de sécurité dans la requête n'a rien changé.
**À retenir** :
- Un 401 sur une action qui modifie peut venir d'un jeton CSRF absent.
- Pour comprendre un cookie qui disparaît : `cookieStore.addEventListener('change', ...)` dans la console du navigateur.
- Reproduire le bug avec une petite boucle de requêtes vaut mieux que deviner.

## 6. Le menu n'était pas adapté au téléphone
- **Symptôme** : le menu latéral passait tout en haut, en grand.
- **Solution** : barre PrimeNG `p-menubar` avec menu hamburger sous 1100 px.

## 7. Vercel « Blocked » : les nouvelles versions n'étaient pas publiées
- **Symptôme** : le téléphone montrait encore l'ancien menu alors que le code était poussé.
- **Cause** : Vercel refuse de déployer si l'e-mail de l'auteur du commit n'est lié à aucun compte GitHub.
- **Solution** : configurer Git avec l'adresse « noreply » GitHub du compte, puis pousser un nouveau commit.
- **À retenir** : `git config user.email` doit correspondre à une adresse **vérifiée** de ton compte GitHub (ou à l'adresse noreply).

## 8. Limites connues du gratuit
Réveil lent de Render (~1 min), SMTP bloqué, base qui expire à 30 jours, mémoire 512 Mo. Voir [[08 - Déploiement]].

## 9. Deux PostgreSQL sur le même volume : base corrompue
**Symptôme** : après avoir lancé `docker compose up` (pile complète) alors que le PostgreSQL « mode IntelliJ » tournait déjà, le back-end a commencé à planter au démarrage avec `column "part_id" of relation "quote_lines" already exists`, puis `pg_dump` a échoué avec `cache lookup failed for relation 32908`.
**Diagnostic** : les deux conteneurs PostgreSQL (`garage-postgres` et `garage-app-postgres`) montaient le **même volume** de données (`backend_garage-pgdata`, comme prévu pour partager les données de test), mais tournaient **en même temps**. Chacun avait son propre fichier verrou (`postmaster.pid`) sur les mêmes fichiers, et l'un a fini par invalider celui de l'autre, qui s'est arrêté brutalement (« *performing immediate shutdown because data directory lock file is invalid* »).
**Cause** : deux serveurs PostgreSQL indépendants ne doivent **jamais** écrire sur le même répertoire de données simultanément. Résultat : des doublons fantômes dans le catalogue système (`pg_constraint` pointant vers des tables qui n'existaient plus côté `pg_class`), alors que les **données elles-mêmes** (lignes des tables) restaient lisibles et intactes.
**Solution** :
1. Sauvegarde ligne par ligne de chaque table avec `\copy <table> TO STDOUT WITH CSV HEADER` (contourne le catalogue abîmé, que `pg_dump` ne peut plus traverser).
2. Arrêt des deux conteneurs, renommage de l'ancien (`garage-postgres-corrupted-backup`, rien supprimé).
3. Nouveau conteneur PostgreSQL, volume neuf et vide, mêmes identifiants, même port.
4. Redémarrage du back-end : Liquibase reconstruit tout le schéma depuis zéro (les 16 changesets).
5. Réimport des données sauvegardées.
**À retenir** :
- Le commentaire du fichier `docker-compose.yml` disait déjà « *Ne lancez pas les deux bases en même temps* » : un avertissement en commentaire ne protège de rien, contrairement à une vraie garde technique.
- `pg_dump` qui échoue ne veut pas dire que les données sont perdues : `\copy ... TO STDOUT` lit les lignes directement, sans passer par les métadonnées de contraintes qui, elles, peuvent être abîmées séparément.
- Toujours vérifier `docker ps` pour les conteneurs PostgreSQL concurrents avant de diagnostiquer une erreur Liquibase qui semble absurde (« colonne déjà existante » alors qu'on vient de créer la base).
