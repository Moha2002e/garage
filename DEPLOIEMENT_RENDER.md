# Déployer le back-end Garage sur Render (offre gratuite)

Le back-end est dans `backend/` (Spring Boot 3.3, Java 21, PostgreSQL) et se construit avec `backend/Dockerfile`.

## 0. Avant de commencer

1. Le dépôt Git n'a pas encore de remote : crée un dépôt GitHub et pousse le projet.
2. Commit d'abord tes modifications en cours (`git status`). Le fichier `backend/.env` est ignoré par Git : ne le pousse jamais.
3. Renseigne un **vrai** `JWT_SECRET` (32 caractères minimum, aléatoire) : l'application refuse de démarrer sans lui.

## 1. Créer la base PostgreSQL

Render > New > Postgres > plan **Free**, dans la même région que le back-end (ex. Frankfurt).

- Note le nom d'hôte **interne**, le nom de la base, l'utilisateur et le mot de passe.
- Limite : la base gratuite **expire après 30 jours** (14 jours de grâce avant suppression), 1 Go max, pas de sauvegarde.

## 2. Créer le Web Service

Render > New > Web Service > ton dépôt GitHub.

| Réglage | Valeur |
|---|---|
| Language | Docker |
| Root Directory | `backend` |
| Instance type | Free |
| Région | la même que la base |

## 3. Variables d'environnement

| Variable | Valeur |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` (cookie sécurisé, Swagger désactivé) |
| `DB_URL` | `jdbc:postgresql://<hôte interne>:5432/<nom de la base>` |
| `DB_USER` | utilisateur de la base |
| `DB_PASSWORD` | mot de passe de la base |
| `JWT_SECRET` | chaîne aléatoire d'au moins 32 caractères |
| `ADMIN_EMAIL` | e-mail de l'administrateur créé au premier démarrage |
| `ADMIN_PASSWORD` | mot de passe de cet administrateur |
| `FRONT_URL` | URL du front (ex. `https://garage.vercel.app`) |
| `COOKIE_SECURE` | `true` |
| `TZ` | `Europe/Paris` |

Optionnelles : `GARAGE_COMPANY_*` (en-tête des factures PDF), `INVOICE_VAT_RATE`, `GARAGE_CAPACITY`, `MAIL_FROM`.

Attention : Render fournit l'URL de la base au format `postgres://...`, que Java ne comprend pas. Il faut écrire `DB_URL` en `jdbc:postgresql://...` et mettre l'utilisateur et le mot de passe dans `DB_USER` et `DB_PASSWORD`.

## 4. Premier démarrage

- Liquibase crée les tables tout seul.
- Le démarrage peut prendre 1 à 2 minutes (moins d'1 CPU).
- Le service s'endort après 15 minutes sans requête, et le réveil prend environ 1 minute.

## 5. Cookies entre Vercel et Render : déjà géré dans le front

L'authentification utilise un cookie (`SameSite=Lax`) et un jeton CSRF lu par Angular dans un cookie. Si le front (`xxx.vercel.app`) appelait directement le back (`xxx.onrender.com`), ce seraient deux sites différents : la connexion ne marcherait pas.

Le front est donc adapté : il appelle `/api/...` sur sa propre adresse, et `frontend/vercel.json` fait suivre ces appels vers Render. Pour le navigateur, tout reste sur le même site.

## 6. Limites à connaître

- **E-mails** : Render bloque les ports SMTP 25, 465 et 587 sur l'offre gratuite. Les e-mails (réinitialisation de mot de passe, comptes créés par l'admin) ne partiront pas tant qu'un fournisseur sur un autre port n'est pas configuré (`MAIL_HOST`, `MAIL_PORT`, plus `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD`, `SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH=true` et `SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=true` selon le fournisseur).
- **Fichiers** : le disque est éphémère. Tout doit rester dans PostgreSQL.
- **Mémoire** : 512 Mo. La JVM est limitée dans le Dockerfile (`JAVA_TOOL_OPTIONS`). Si le service redémarre en boucle, surveille les logs.
- **Sécurité** : avant d'exposer l'app publiquement, fais l'audit prévu (limitation de requêtes, anti-bots).

## 7. Déployer le front sur Vercel

1. Pousse le projet sur GitHub, puis dans Vercel : Add New > Project > ton dépôt.
2. **Root Directory** : `frontend`. Le reste (installation, build, dossier de sortie) vient de `frontend/vercel.json`.
3. Dans Settings > Build and Development > **Node.js Version** : 24.x (comme le Dockerfile).
4. Dans `frontend/vercel.json`, remplace `REMPLACE-PAR-TON-SERVICE.onrender.com` par l'adresse réelle de ton service Render, puis recommit.
5. Une fois l'adresse Vercel connue, mets-la dans `FRONT_URL` côté Render.
6. Aucune variable d'environnement n'est nécessaire côté Vercel.

Le premier appel après une mise en veille du back peut échouer (réveil d'environ 1 minute) : recharge la page.

Note : `src/environments/environment.ts` contient une clé de licence PrimeUI. Si ton dépôt GitHub est public, elle sera visible.
