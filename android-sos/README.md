# MEDFLASH SOS solidaire — prototype Android (NON PUBLIÉ)

Ce dossier est un **prototype technique**, pas une mise à jour prête pour Google Play. La version Web et les testeurs actuels ne sont pas modifiés.

## Comportement implémenté
- Fenêtre de consentement affichée une fois et choix mémorisé.
- Demandes de permissions système Android.
- Réception BLE **uniquement quand l'activité est ouverte**, notification SOS (anti-doublon 60 s).
- Diffusion BLE quand l'écran « SOS – APPELEZ LES SECOURS » est affiché, arrêt au retour ou en quittant l'application.
- Aucun serveur ni abonnement pour l'échange BLE.

## Limites bloquantes avant diffusion
1. **Pas de réception fiable téléphone verrouillé ou application fermée** : nécessite un service natif, une stratégie de foreground service conforme à Android et des tests multi-constructeurs.
2. **20 m non garantis** : portée radio variable, aucune mesure de distance précise.
3. **Pas d'AAB de mise à jour** : l'identifiant `org.medflash.sos` est provisoire. Il faut l'identifiant réel de l'application Play, la signature originale ou Play App Signing, et les sources/paramètres de packaging existants.
4. **Hors ligne** : ce prototype WebView charge l'URL en ligne; l'expérience PWA hors ligne actuelle n'est pas reproduite. Ne pas substituer ce prototype à la version publiée.
5. **Sécurité** : le pont JavaScript WebView doit être durci avant publication (origine, navigation, contenu tiers). Ajouter anti-abus, limitation des émissions et possibilité explicite de modifier le consentement.
6. **Tests** : non compilé, non testé sur appareils, pas de validation Play Store.

## Construction exploratoire
Ouvrir `android-sos/` dans Android Studio avec JDK 17 et SDK Android 35. Les fichiers Gradle sont fournis, mais pas le wrapper Gradle ni les ressources de l'application Android existante.

## Prochaine étape
Retrouver l'identifiant de package Play et le projet de packaging original; créer un service BLE compatible avec les restrictions Android, conserver le mode hors ligne et faire des tests réels avant toute mise à jour.
