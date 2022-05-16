Pour lancer un client il suffit de lancer la commande :

java --enable-preview -jar Client.jar login ipAddress port chemin_absolu_dossier_contenant_fichiers

ex: java --enable-preview -jar Client.jar yassine localhost 7777 .

Pour lancer un serveur il suffit de lancer la commande :

java --enable-preview -jar Server.jar port serverName

ex: java --enable-preview -jar Server.jar 7777 A

Pour envoyer un fichier à quelqu'un, il suffit de lancer la commande:

/login:serverName filename

(Le fichier doit être contenu dans le dossier file, le fichier que recevra le client sera dans le dossier fileReceived)

Pour envoyer un message public, il suffit de lancer la commande:

message

Pour envoyer un message privé, il suffit de lancer la commande:

@login:serverName message

