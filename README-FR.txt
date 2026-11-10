ATLANTYKAWAGONS 1.1.0
Plugin Paper 1.21.8 - Java 21

FONCTIONS
- Wagons décorés avec des BlockDisplay utilisant des blocs vanilla.
- Deux places passagers invisibles (ArmorStands servant de points de montage).
- Pose du wagon avec clic droit directement sur un rail.
- Sons configurables à la pose, à l'installation d'un joueur et pendant le déplacement.
- Les wagons suivent les rails grâce à un Minecart vanilla.
- Commandes avec tabulation.

COMMANDES
/wagon give <joueur> wagon_bois
/wagon give <joueur> wagon_rouge
/wagon give <joueur> wagon_bleu
/wagon give <joueur> wagon_horreur
/wagon give <joueur> wagon_or
/wagon list
/wagon remove
/wagon help

PERMISSION
atlantykawagons.admin (op par défaut)
Avec LuckPerms : /lp group admin permission set atlantykawagons.admin true

PERSONNALISER
Édite plugins/AtlantykaWagons/config.yml puis redémarre.
Pour chaque modèle : base (plancher), seat (assise), back (dossier), fence (barrières), bumper (détails), place-sound et board-sound.

COMPILATION
Java 21 + Maven : mvn clean package
JAR : target/AtlantykaWagons-1.1.0.jar

INSTALLATION
Copie le JAR dans plugins/ puis redémarre Paper.
Cette version est un prototype à tester sur une copie du serveur. Les wagons ne sont pas encore reliés en convois.
