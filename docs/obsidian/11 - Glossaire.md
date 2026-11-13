---
projet: Garage
tags: [garage, glossaire]
---

# Glossaire (simple)

Retour à [[00 - Garage (accueil)]].

| Mot | Explication |
|---|---|
| **API REST** | Des adresses (`/api/clients`) que le front appelle pour lire ou modifier des données |
| **JSON** | Format texte des données échangées : `{"nom": "Dupont"}` |
| **Spring Boot** | Framework Java pour créer un serveur rapidement |
| **JPA / Hibernate** | Outil qui relie des objets Java à des tables SQL |
| **Entité** | Classe Java qui représente une table (ex. `Appointment`) |
| **DTO** | Objet simple envoyé au front, pour ne pas exposer l'entité |
| **Repository** | Classe qui lit et écrit en base |
| **Liquibase** | Outil qui applique les changements de structure de la base, version par version |
| **Spring Batch** | Cadre pour traiter beaucoup d'éléments d'un coup avec un journal d'erreurs |
| **JWT** | Jeton signé qui prouve qui tu es |
| **Cookie HttpOnly** | Cookie que le JavaScript ne peut pas lire (protège le JWT) |
| **CSRF** | Attaque où un autre site fait agir ton navigateur à ton insu |
| **Stateless** | Le serveur ne garde pas de session : chaque requête se suffit à elle-même |
| **BCrypt** | Façon de hacher un mot de passe (on ne peut pas le retrouver) |
| **CORS** | Règle du navigateur sur quels sites peuvent appeler quelle API |
| **Idempotent** | Relancer l'opération donne le même résultat (pas de doublon) |
| **Docker** | Empaquette l'app avec tout ce qu'il lui faut pour tourner partout |
| **Variable d'environnement** | Réglage (souvent secret) donné au serveur sans l'écrire dans le code |
| **Proxy / rewrite** | Vercel transmet `/api/...` au serveur Render pour le navigateur |
| **Monorepo** | Un seul dépôt Git pour plusieurs applications |
| **Signal (Angular)** | Valeur réactive : l'écran se met à jour quand elle change |
| **Intercepteur** | Code qui s'exécute sur chaque requête HTTP du front |
| **Responsive** | Page qui s'adapte à la taille de l'écran |
| **Testcontainers** | Lance une vraie base dans Docker pendant les tests |
| **Rate limiting** | Limiter le nombre de tentatives (ex. de connexion) dans un temps donné, pour bloquer un script qui essaie en boucle |
| **Force brute / dictionnaire** | Deviner un mot de passe en essayant une liste de mots courants (dictionnaire) ou toutes les combinaisons possibles (force brute pure) |
| **Révocation de session** | Rendre une session (ici un JWT) invalide avant son expiration normale, par exemple après un changement de mot de passe |
| **Attaque par le temps (timing attack)** | Deviner une information (un e-mail existe ou non) à partir du temps que prend le serveur à répondre |
| **Verrou optimiste (`@Version`)** | Détecte que deux personnes ont modifié la même ligne en même temps, sans verrouiller la base en permanence |
