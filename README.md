# TheWarGame — stratégie européenne sur Android

TheWarGame est un prototype Android en mode paysage de stratégie historique au tour par tour. Tu joues la France ; tous les autres pays sont commandés automatiquement par l'IA.

## Fonctionnalités
- Carte géographique réelle de l'Europe avec les polygones des régions/provinces administratives de premier niveau.
- Frontières et connexions entre régions calculées à partir de leurs véritables limites géographiques, pas de simples points reliés à la main.
- Couleurs distinctes par pays, sélection des régions et affichage de villes capitales.
- Bots nationaux, progression mensuelle et sauvegarde locale.
- Fonctionne hors ligne après installation. Aucune clé API n'est nécessaire pour jouer.

### Données cartographiques
Les limites proviennent du jeu de données **Natural Earth 1:10m Admin-1 States/Provinces**, avec des contours nationaux en complément pour quelques micro-États. La préparation est exécutée automatiquement par GitHub Actions et intègre les polygones dans l'APK. Les pays n'ont pas tous le même niveau de subdivision administrative, donc la granularité varie d'un pays à l'autre. Source : https://www.naturalearthdata.com/downloads/10m-cultural-vectors/10m-admin-1-states-provinces/.

## Commandes
1. Sélectionne une région française.
2. Touche une région ennemie adjacente pour choisir ta cible.
3. Choisis Attaquer pour lancer une bataille.
4. Recrute de l'infanterie ou des blindés, ajoute des roquettes, fortifie une province ou affecte un général.
5. Appuie sur Terminer le mois : les ressources augmentent et les pays bots jouent leur tour.

## Récupérer l'APK depuis GitHub Actions
1. Ouvre l'onglet Actions du dépôt.
2. Sélectionne le workflow Build TheWarGame APK.
3. Ouvre la dernière exécution terminée avec succès.
4. Télécharge l'artefact TheWarGame-APK et décompresse le ZIP.
5. Installe app-debug.apk sur ton appareil Android. Selon Android, il faudra autoriser l'installation de cette application de test.

L'APK debug est signé automatiquement avec une clé de débogage Android pour l'installation et les essais. Une publication officielle demandera un APK/AAB de production signé avec une clé de publication privée conservée en sécurité.

## IA Groq
Le prototype actuel fonctionne sans Internet. Groq pourra ensuite enrichir l'IA, mais une clé API ne doit jamais être écrite directement dans le code public ni embarquée dans l'APK.
