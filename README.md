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

## Comment attaquer et conquérir
1. **Touchez une région française** sur la carte.
2. Ses régions ennemies voisines sont surlignées en jaune-orangé : **touchez l'une d'elles** pour la choisir comme cible.
3. Le panneau indique le nom du territoire visé. Le bouton devient **« ATTAQUER : [région] »** : touchez-le pour lancer le combat.
4. En cas de victoire, la région passe sous contrôle français. Les unités survivantes sont transférées sans créer de soldats supplémentaires.
5. Recrutez des unités ou fortifiez vos positions. Appuyez sur **Terminer le mois** pour recevoir des ressources et laisser les bots agir.

Le bouton Attaquer est désactivé si aucune cible voisine valide n'est choisie, si la région source n'a plus d'unités ou si la partie est terminée. Sur les petites subdivisions, la sélection privilégie le centre géographique le plus proche pour faciliter les commandes au doigt.

## Règles de conquête
- Le joueur commande la France ; les autres pays sont contrôlés automatiquement.
- La victoire stratégique est obtenue quand la France contrôle au moins **50 % des régions jouables**.
- La partie est perdue si la France ne contrôle plus aucune région.
- Les combats des bots sont limités par mois afin que les tours restent rapides même sur la carte très détaillée.

## Récupérer l'APK depuis GitHub Actions
1. Ouvre l'onglet Actions du dépôt.
2. Sélectionne le workflow Build TheWarGame APK.
3. Ouvre la dernière exécution terminée avec succès.
4. Télécharge l'artefact TheWarGame-APK et décompresse le ZIP.
5. Installe app-debug.apk sur ton appareil Android. Selon Android, il faudra autoriser l'installation de cette application de test.

L'APK debug est signé automatiquement avec une clé de débogage Android pour l'installation et les essais. Une publication officielle demandera un APK/AAB de production signé avec une clé de publication privée conservée en sécurité.

## IA Groq
Le prototype actuel fonctionne sans Internet. Groq pourra ensuite enrichir l'IA, mais une clé API ne doit jamais être écrite directement dans le code public ni embarquée dans l'APK.
