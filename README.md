# TheWarGame — stratégie européenne sur Android

TheWarGame est un prototype Android en mode paysage de stratégie historique au tour par tour. Tu joues la France ; tous les autres pays sont commandés automatiquement par l'IA.

## Fonctionnalités
- Carte stratégique stylisée de l'Europe avec provinces, frontières, capitales et couleurs distinctes par pays.
- Bots nationaux qui évaluent les frontières et peuvent attaquer/conquérir des provinces à chaque changement de mois.
- Campagne commençant en septembre 1939 ; les mois et années avancent au fil des tours.
- Infanterie, blindés, artillerie à roquettes, généraux et fortifications.
- Résolution des batailles selon les effectifs, les unités, les généraux et les défenses.
- Recrutement avec ravitaillement et effectifs, sauvegarde locale automatique.
- Fonctionne hors ligne. Aucune clé API n'est nécessaire pour jouer.

La carte regroupe des provinces stratégiques à travers l'Europe. C'est une carte de jeu dessinée spécialement pour ce prototype, pas encore un relevé géographique exact de toutes les subdivisions administratives officielles. Les graphismes sont en 2D tactique et pourront être approfondis.

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
