---
projet: Garage
tags: [garage, donnees, seed]
---

# Données de démonstration

Retour à [[00 - Garage (accueil)]].

## But
Une app vide ne montre rien : calendrier, graphiques et factures n'ont de sens qu'avec beaucoup de données. On en génère donc des milliers de **fausses**.

## Le générateur
`scripts/seed/generate_seed.py` (Python) produit un fichier `seed.sql` :
- ~600 clients, ~850 véhicules, ~4 000 rendez-vous sur environ un an
- ~3 350 devis, ~1 500 factures déjà émises
- ~1 000 rendez-vous **terminés et acceptés mais non facturés**, pour que le batch ait du travail

Il respecte les règles métier : capacité du garage, pas de chevauchement par véhicule, plaques valides, totaux = somme des lignes. Le **même `--seed`** donne toujours les mêmes données (reproductible). Les e-mails de test se terminent par `@seed.garage.test`, ce qui permet de tout retrouver et supprimer (`cleanup.sql`).

## Comment les insérer sur Render ?
La base Render n'est pas joignable depuis n'importe où. Solution : le **back-end lui-même** insère au démarrage.
- `DemoDataSeeder` lit `seed/seed.sql` (dans les ressources) et l'exécute dans **une transaction**.
- Il ne s'active que si la variable `SEED_ENABLED=true` est définie.
- Il ne fait rien si les données de démo existent déjà.
- Après usage, on supprime la variable.

Alternative : lancer `run_seed.py` depuis son PC avec l'URL externe de la base.
