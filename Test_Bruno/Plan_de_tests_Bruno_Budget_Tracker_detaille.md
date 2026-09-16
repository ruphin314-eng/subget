# PLAN DE TESTS API --- Budget Tracker

**Backend :** Spring Boot\
**Outil :** Bruno\
**Version :** 2.0 --- Plan détaillé\
**Date :** 8 septembre 2026

------------------------------------------------------------------------

## 0. Introduction

### 0.1 Objectif

Ce document constitue une version détaillée du plan de tests de l'API du
backend **Budget Tracker**. Il reprend la suite de tests fonctionnels et
de sécurité prévue pour être exécutée avec **Bruno**, dans l'ordre
recommandé.

L'objectif est de vérifier :

-   l'inscription et la connexion des utilisateurs ;
-   la génération et l'utilisation correcte des access tokens et refresh
    tokens ;
-   la séparation stricte des types de tokens ;
-   la protection des routes nécessitant une authentification ;
-   la séparation des rôles `CLIENT` et `ADMIN` ;
-   la consultation et la modification du profil ;
-   la gestion des dépenses ;
-   la gestion des entrées ;
-   le filtrage par période ;
-   l'isolation des données entre utilisateurs ;
-   les calculs du dashboard ;
-   la validation des données reçues ;
-   le comportement de l'API face aux requêtes invalides ;
-   l'absence de fuite d'informations techniques dans les erreurs ;
-   le rate limiting sur les endpoints d'authentification.

La suite comporte **55 cas de test**.

------------------------------------------------------------------------

# 1. Préparation de l'environnement Bruno

## 1.1 Pré-requis

Avant de commencer les tests :

1.  Démarrer le backend Spring Boot.
2.  Vérifier que l'application écoute sur : `http://localhost:8080`
3.  Vérifier que la base de données est disponible.
4.  Vérifier le profil de configuration utilisé.
5.  Vérifier que le compte ADMIN peut être créé par le mécanisme
    `AdminSeeder`.
6.  Installer et ouvrir Bruno.
7.  Créer ou ouvrir la collection de tests Budget Tracker.
8.  Créer un environnement Bruno nommé `local`.

## 1.2 Variables Bruno

Configurer les variables suivantes :

  Variable             Valeur initiale / rôle
  -------------------- ------------------------------------------
  `baseUrl`            `http://localhost:8080`
  `clientEmail`        Email du client créé avec AUTH-01
  `clientPassword`     Mot de passe du client créé avec AUTH-01
  `accessToken`        Rempli après AUTH-06
  `refreshToken`       Rempli après AUTH-06
  `adminEmail`         Valeur de `admin.seed.email`
  `adminPassword`      Valeur de `admin.seed.password`
  `adminAccessToken`   Rempli après ADM-03

Pour les routes client protégées :

``` http
Authorization: Bearer {{accessToken}}
```

Pour les routes ADMIN :

``` http
Authorization: Bearer {{adminAccessToken}}
```

## 1.3 Gestion des tokens

Après AUTH-06, enregistrer les deux tokens dans les variables Bruno.

Un script post-response Bruno peut être utilisé pour automatiser
l'enregistrement :

``` javascript
bru.setEnvVar("accessToken", res.body.accessToken)
bru.setEnvVar("refreshToken", res.body.refreshToken)
```

L'objectif est d'éviter de copier manuellement les tokens dans chaque
requête.

## 1.4 Ordre d'exécution

L'ordre recommandé est :

1.  Authentification
2.  Sécurité transversale
3.  Profil utilisateur
4.  Administration
5.  Dépenses
6.  Entrées
7.  Dashboard
8.  Robustesse générale

Cet ordre est important car plusieurs tests dépendent des résultats
précédents.

Par exemple :

-   `/users/me` nécessite un access token valide ;
-   les tests ADMIN nécessitent un compte ADMIN ;
-   le dashboard nécessite des dépenses et/ou entrées pour vérifier les
    agrégations ;
-   les tests d'isolation nécessitent au moins deux utilisateurs.

------------------------------------------------------------------------

# 2. Légende des résultats

  Code    Signification
  ------- ------------------------------------
  `2xx`   Succès
  `4xx`   Requête incorrecte ou accès refusé
  `5xx`   Erreur serveur

Dans cette suite, une erreur `500` est considérée comme anormale, sauf
lorsqu'un test vérifie explicitement qu'une erreur de ce type **ne doit
pas apparaître**.

Pour chaque test, relever au minimum :

-   code HTTP ;
-   corps de réponse ;
-   structure JSON ;
-   message d'erreur ;
-   présence ou absence des champs sensibles ;
-   comportement observé ;
-   éventuel impact en base ;
-   éventuelle différence avec le résultat attendu.

------------------------------------------------------------------------

# 3. AUTHENTIFICATION --- `/auth/**`

**Nombre de tests : 16**

Cette section est fondamentale car elle fournit les comptes et tokens
utilisés dans les autres sections.

------------------------------------------------------------------------

## AUTH-01 --- Inscription nominale

### Objectif

Vérifier qu'un nouvel utilisateur peut créer un compte avec des
informations valides.

### Requête

``` http
POST {{baseUrl}}/auth/register
Content-Type: application/json
```

### Body

``` json
{
  "nom": "Jean Dupont",
  "email": "jean.dupont@test.com",
  "motDePasse": "Motdepasse1!"
}
```

### Résultat attendu

-   HTTP `201 Created`
-   corps vide ;
-   utilisateur créé en base ;
-   `role = CLIENT` ;
-   `actif = true` ;
-   mot de passe stocké sous forme hachée ;
-   le mot de passe en clair ne doit jamais être stocké.

### Vérifications complémentaires

Vérifier en base que :

``` text
role = CLIENT
actif = true
motDePasse != "Motdepasse1!"
```

### Critère de réussite

Le compte existe et peut ensuite être utilisé pour AUTH-06.

------------------------------------------------------------------------

## AUTH-02 --- Inscription avec email déjà utilisé

### Objectif

Vérifier l'unicité de l'adresse email.

### Requête

``` http
POST {{baseUrl}}/auth/register
Content-Type: application/json
```

Utiliser le même email que AUTH-01.

### Résultat attendu

``` text
409 Conflict
```

Le message doit rester générique, par exemple :

``` text
Cet email est déjà utilisé
```

### Critère de réussite

Aucun deuxième compte ne doit être créé avec la même adresse.

------------------------------------------------------------------------

## AUTH-03 --- Mot de passe trop faible

### Objectif

Vérifier la validation de la complexité du mot de passe.

### Body

``` json
{
  "nom": "Test User",
  "email": "weak@test.com",
  "motDePasse": "azerty"
}
```

### Résultat attendu

``` text
400 Bad Request
```

Le champ `motDePasse` doit être signalé comme invalide.

### Points vérifiés

Le mot de passe doit respecter les règles de complexité prévues :

-   longueur minimale de 8 caractères ;
-   majuscule ;
-   minuscule ;
-   chiffre ;
-   caractère spécial.

------------------------------------------------------------------------

## AUTH-04 --- Champs obligatoires manquants

### Body

``` json
{}
```

### Résultat attendu

``` text
400 Bad Request
```

La réponse doit indiquer les champs invalides ou manquants :

-   `nom`
-   `email`
-   `motDePasse`

### Critère de réussite

La requête ne doit pas provoquer de `500`.

------------------------------------------------------------------------

## AUTH-05 --- Email mal formé

### Body

``` json
{
  "nom": "Test User",
  "email": "pas-un-email",
  "motDePasse": "Motdepasse1!"
}
```

### Résultat attendu

``` text
400 Bad Request
```

Le champ `email` doit être identifié comme invalide.

------------------------------------------------------------------------

## AUTH-06 --- Connexion nominale

### Objectif

Vérifier qu'un client inscrit peut se connecter.

### Requête

``` http
POST {{baseUrl}}/auth/login
Content-Type: application/json
```

### Body

``` json
{
  "email": "jean.dupont@test.com",
  "motDePasse": "Motdepasse1!"
}
```

### Résultat attendu

``` text
200 OK
```

La réponse doit contenir :

``` text
accessToken
refreshToken
tokenType = Bearer
```

### Action Bruno

Sauvegarder :

``` text
accessToken → {{accessToken}}
refreshToken → {{refreshToken}}
```

### Critère de réussite

Les deux tokens sont présents et utilisables selon leur fonction
respective.

------------------------------------------------------------------------

## AUTH-07 --- Mauvais mot de passe

### Objectif

Vérifier le rejet d'un mot de passe incorrect sans révéler
d'information.

### Body

Utiliser un email valide avec un mauvais mot de passe.

### Résultat attendu

``` text
401 Unauthorized
```

Message générique :

``` text
Identifiants invalides
```

Le serveur ne doit pas indiquer que seul le mot de passe est incorrect.

------------------------------------------------------------------------

## AUTH-08 --- Email inexistant

### Body

``` json
{
  "email": "inconnu@test.com",
  "motDePasse": "Motdepasse1!"
}
```

### Résultat attendu

``` text
401 Unauthorized
```

Le message et la structure doivent être identiques à AUTH-07.

### Objectif sécurité

Empêcher l'énumération des comptes.

Il faut comparer AUTH-07 et AUTH-08 :

-   même code HTTP ;
-   même message ;
-   même structure générale de réponse.

------------------------------------------------------------------------

## AUTH-09 --- Compte désactivé

### Pré-requis

Disposer d'un utilisateur dont :

``` text
actif = false
```

### Requête

``` http
POST {{baseUrl}}/auth/login
```

avec ses identifiants corrects.

### Résultat attendu

``` text
401 Unauthorized
```

### Important

La réponse ne doit pas révéler :

-   que l'utilisateur existe ;
-   que son compte est désactivé ;
-   une information interne permettant de distinguer ce cas.

Il ne doit surtout pas y avoir de `500`.

------------------------------------------------------------------------

## AUTH-10 --- Refresh token nominal

### Objectif

Vérifier qu'un refresh token permet d'obtenir un nouveau couple de
tokens.

### Requête

``` http
POST {{baseUrl}}/auth/refresh-token
Content-Type: text/plain
```

### Body

Utiliser le `refreshToken` obtenu lors de AUTH-06.

### Résultat attendu

``` text
200 OK
```

La réponse doit contenir :

-   un nouvel `accessToken` ;
-   un nouveau `refreshToken`.

Les nouveaux tokens doivent être différents des précédents.

------------------------------------------------------------------------

## AUTH-11 --- Utilisation d'un access token comme refresh token

### Objectif

Empêcher qu'un access token soit accepté par `/auth/refresh-token`.

### Requête

``` http
POST {{baseUrl}}/auth/refresh-token
```

Body :

``` text
<accessToken>
```

### Résultat attendu

``` text
401 Unauthorized
```

### Sécurité vérifiée

Le claim `type` du JWT doit permettre de distinguer :

``` text
access
```

et

``` text
refresh
```

------------------------------------------------------------------------

## AUTH-12 --- Utilisation d'un refresh token sur une route protégée

### Objectif

Vérifier qu'un refresh token ne peut pas être utilisé comme access
token.

### Requête

``` http
GET {{baseUrl}}/users/me
Authorization: Bearer <refreshToken>
```

### Résultat attendu

``` text
401 Unauthorized
```

Le filtre d'authentification doit refuser le token.

------------------------------------------------------------------------

## AUTH-13 --- Refresh token invalide

### Requête

``` http
POST {{baseUrl}}/auth/refresh-token
```

Body :

``` text
token.invalide.abc123
```

### Résultat attendu

``` text
401 Unauthorized
```

Aucun `500` ne doit apparaître.

------------------------------------------------------------------------

## AUTH-14 --- Rate limiting du login

### Objectif

Vérifier la protection contre les tentatives répétées de connexion.

### Action

Envoyer rapidement :

``` text
POST /auth/login
POST /auth/login
POST /auth/login
POST /auth/login
POST /auth/login
POST /auth/login
```

### Résultat attendu

Les cinq premières requêtes doivent recevoir leur réponse normale :

``` text
200 ou 401
```

La sixième doit recevoir :

``` text
429 Too Many Requests
```

### Critère de réussite

Le mécanisme bloque les tentatives excessives depuis la même IP.

------------------------------------------------------------------------

## AUTH-15 --- Rate limiting de l'inscription

### Objectif

Vérifier la protection de `/auth/register`.

### Action

Envoyer rapidement six inscriptions avec des emails différents.

### Résultat attendu

La sixième requête doit retourner :

``` text
429 Too Many Requests
```

------------------------------------------------------------------------

## AUTH-16 --- Déconnexion

### Requête

``` http
POST {{baseUrl}}/auth/logout
Authorization: Bearer {{accessToken}}
```

### Résultat attendu

``` text
204 No Content
```

### Limitation connue

Le backend utilise des JWT stateless. Le logout ne révoque donc pas côté
serveur un access token déjà émis.

Un token déjà valide peut rester utilisable jusqu'à son expiration
naturelle.

------------------------------------------------------------------------

# 4. SÉCURITÉ TRANSVERSALE

**Nombre de tests : 5**

------------------------------------------------------------------------

## SEC-01 --- Route protégée sans token

### Requête

``` http
GET {{baseUrl}}/users/me
```

Sans `Authorization`.

### Résultat attendu

``` text
401 Unauthorized
```

------------------------------------------------------------------------

## SEC-02 --- Token invalide

### Requête

``` http
GET {{baseUrl}}/users/me
Authorization: Bearer token.invalide
```

### Résultat attendu

``` text
401 Unauthorized
```

------------------------------------------------------------------------

## SEC-03 --- Token expiré

### Objectif

Vérifier que l'expiration JWT est réellement appliquée.

### Préparation

Deux possibilités :

1.  attendre l'expiration normale ;
2.  réduire temporairement `jwt.access-token-expiration-ms` à quelques
    secondes.

### Requête

``` http
GET {{baseUrl}}/users/me
Authorization: Bearer <accessToken-expiré>
```

### Résultat attendu

``` text
401 Unauthorized
```

------------------------------------------------------------------------

## SEC-04 --- Route ADMIN avec compte CLIENT

### Requête

``` http
GET {{baseUrl}}/admin/users
Authorization: Bearer {{accessToken}}
```

### Résultat attendu

``` text
403 Forbidden
```

### Objectif

Vérifier le cloisonnement des rôles.

Un utilisateur `CLIENT` authentifié ne doit pas obtenir les droits
`ADMIN`.

------------------------------------------------------------------------

## SEC-05 --- CORS avec origine non autorisée

### Requête

Ajouter :

``` http
Origin: http://site-inconnu.com
```

### Résultat attendu

La réponse ne doit pas contenir :

``` http
Access-Control-Allow-Origin: http://site-inconnu.com
```

Un navigateur doit donc bloquer l'appel provenant de cette origine.

------------------------------------------------------------------------

# 5. PROFIL UTILISATEUR --- `/users/me`

**Nombre de tests : 5**

------------------------------------------------------------------------

## USER-01 --- Consulter son profil

### Requête

``` http
GET {{baseUrl}}/users/me
Authorization: Bearer {{accessToken}}
```

### Résultat attendu

``` text
200 OK
```

Le corps doit contenir notamment :

-   `id`
-   `nom`
-   `email`
-   `role`
-   `actif`

### Vérification sécurité

Le champ :

``` text
motDePasse
```

ne doit **jamais** apparaître.

------------------------------------------------------------------------

## USER-02 --- Modifier son nom

### Requête

``` http
PUT {{baseUrl}}/users/me
Authorization: Bearer {{accessToken}}
Content-Type: application/json
```

### Body

``` json
{
  "nom": "Jean D. Modifié"
}
```

### Résultat attendu

``` text
200 OK
```

Le nom doit être modifié.

L'email et le mot de passe doivent rester inchangés.

------------------------------------------------------------------------

## USER-03 --- Modifier son mot de passe

### Body

``` json
{
  "motDePasse": "NouveauMdp2!"
}
```

### Vérification

Après modification :

1.  essayer de se connecter avec l'ancien mot de passe ;
2.  essayer de se connecter avec le nouveau.

### Résultat attendu

Ancien mot de passe :

``` text
401 Unauthorized
```

Nouveau mot de passe :

``` text
200 OK
```

Le nouveau mot de passe doit être re-haché.

------------------------------------------------------------------------

## USER-04 --- Modifier son email vers un email existant

### Objectif

Tester l'unicité de l'email pendant la modification du profil.

### Requête

``` http
PUT {{baseUrl}}/users/me
```

avec l'email d'un autre utilisateur existant.

### Résultat attendu attendu

Le comportement actuellement identifié est susceptible de produire :

``` text
500
```

en raison de la contrainte unique en base.

### Anomalie connue

Le code actuel ne vérifie pas explicitement l'unicité de l'email avant
la sauvegarde.

### Correction recommandée

Le comportement attendu à terme devrait être :

``` text
409 Conflict
```

avec un message métier générique.

Cette anomalie doit être enregistrée comme point de correction.

------------------------------------------------------------------------

## USER-05 --- Accès au profil d'un autre utilisateur

### Requête

``` http
GET {{baseUrl}}/users/123
Authorization: Bearer {{accessToken}}
```

### Résultat attendu

``` text
404 Not Found
```

Il n'existe pas de route `/users/{id}` prévue pour consulter le profil
d'un autre utilisateur.

### Objectif sécurité

Réduire structurellement le risque d'IDOR sur les profils.

------------------------------------------------------------------------

# 6. ADMINISTRATION --- `/admin/**`

**Nombre de tests : 4**

------------------------------------------------------------------------

## ADM-01 --- Lister les utilisateurs avec un ADMIN

### Requête

``` http
GET {{baseUrl}}/admin/users
Authorization: Bearer {{adminAccessToken}}
```

### Résultat attendu

``` text
200 OK
```

La réponse contient la liste des utilisateurs.

Aucun mot de passe ne doit être exposé.

Le compte ADMIN lui-même peut apparaître dans la liste, mais sans
données sensibles.

------------------------------------------------------------------------

## ADM-02 --- Lister les utilisateurs avec un CLIENT

### Requête

``` http
GET {{baseUrl}}/admin/users
Authorization: Bearer {{accessToken}}
```

### Résultat attendu

``` text
403 Forbidden
```

------------------------------------------------------------------------

## ADM-03 --- Création automatique du super ADMIN

### Objectif

Vérifier le fonctionnement du `AdminSeeder`.

### Préparation

1.  Démarrer l'application sur une base vide.
2.  Attendre le démarrage complet.
3.  Récupérer `admin.seed.email` et `admin.seed.password` dans le profil
    de développement.

### Connexion

``` http
POST {{baseUrl}}/auth/login
```

avec les identifiants ADMIN.

### Résultat attendu

``` text
200 OK
```

Le rôle doit être :

``` text
ADMIN
```

Le compte doit également être vérifiable via :

``` http
GET {{baseUrl}}/users/me
```

------------------------------------------------------------------------

## ADM-04 --- Idempotence du seed ADMIN

### Objectif

Vérifier que le redémarrage de l'application ne recrée pas ou n'écrase
pas le compte ADMIN.

### Procédure

1.  Se connecter en ADMIN.
2.  Modifier le mot de passe ADMIN.
3.  Arrêter l'application.
4.  Redémarrer l'application.
5.  Se connecter avec le **nouveau** mot de passe.

### Résultat attendu

``` text
200 OK
```

Le nouveau mot de passe fonctionne.

### Critère de réussite

Le seed n'a pas :

-   recréé un second compte ADMIN ;
-   écrasé le nouveau mot de passe.

------------------------------------------------------------------------

# 7. DÉPENSES --- `/depenses`

**Nombre de tests : 9**

------------------------------------------------------------------------

## DEP-01 --- Créer une dépense

### Requête

``` http
POST {{baseUrl}}/depenses
Authorization: Bearer {{accessToken}}
Content-Type: application/json
```

### Body

``` json
{
  "montant": 150.00,
  "libelle": "Alimentation",
  "description": "Test",
  "date": "2026-09-08"
}
```

### Résultat attendu

``` text
201 Created
```

### Vérifications

-   la dépense est créée ;
-   elle appartient à l'utilisateur authentifié ;
-   le `userId` est déterminé depuis le token ;
-   le DTO de réponse ne doit pas exposer inutilement `userId`.

------------------------------------------------------------------------

## DEP-02 --- Montant négatif

### Body

``` json
{
  "montant": -10,
  "libelle": "Test",
  "description": "Test",
  "date": "2026-09-08"
}
```

### Résultat attendu

``` text
400 Bad Request
```

La contrainte `@DecimalMin` doit empêcher l'enregistrement.

------------------------------------------------------------------------

## DEP-03 --- Montant égal à zéro

### Body

``` json
{
  "montant": 0,
  "libelle": "Test",
  "description": "Test",
  "date": "2026-09-08"
}
```

### Résultat attendu

``` text
400 Bad Request
```

Le montant doit être strictement positif.

------------------------------------------------------------------------

## DEP-04 --- Champs obligatoires manquants

### Body

``` json
{}
```

### Résultat attendu

``` text
400 Bad Request
```

Les validations concernent notamment :

-   `montant`
-   `libelle`
-   `date`

------------------------------------------------------------------------

## DEP-05 --- Injection d'un `userId`

### Objectif

Vérifier qu'un client ne peut pas choisir le propriétaire d'une dépense.

### Body

``` json
{
  "montant": 150.00,
  "libelle": "Alimentation",
  "description": "Test",
  "date": "2026-09-08",
  "userId": 9999
}
```

### Résultat attendu

``` text
201 Created
```

mais la dépense doit être rattachée à l'utilisateur du token.

Elle ne doit jamais être rattachée à :

``` text
userId = 9999
```

### Objectif sécurité

Protection contre l'IDOR et contre la manipulation du propriétaire via
le body.

------------------------------------------------------------------------

## DEP-06 --- Liste par défaut : jour

### Requête

``` http
GET {{baseUrl}}/depenses
Authorization: Bearer {{accessToken}}
```

### Résultat attendu

``` text
200 OK
```

La période par défaut est :

``` text
jour
```

La liste doit contenir uniquement les dépenses dont la date correspond
au jour courant.

------------------------------------------------------------------------

## DEP-07 --- Liste par mois

### Requête

``` http
GET {{baseUrl}}/depenses?periode=mois
Authorization: Bearer {{accessToken}}
```

### Résultat attendu

``` text
200 OK
```

La plage couvre le mois calendaire courant :

``` text
premier jour → dernier jour du mois
```

------------------------------------------------------------------------

## DEP-08 --- Plage `from/to`

### Requête

``` http
GET {{baseUrl}}/depenses?from=2026-01-01&to=2026-12-31
Authorization: Bearer {{accessToken}}
```

### Résultat attendu

``` text
200 OK
```

La réponse contient les dépenses de l'utilisateur dans la période
demandée.

Les bornes explicites `from/to` sont prioritaires sur `periode`.

------------------------------------------------------------------------

## DEP-09 --- Isolation entre utilisateurs

### Objectif

Vérifier qu'un utilisateur ne voit jamais les dépenses d'un autre.

### Procédure

1.  Se connecter avec le compte A.
2.  Créer une dépense.
3.  Se déconnecter ou utiliser le compte B.
4.  Obtenir un access token pour B.
5.  Appeler :

``` http
GET {{baseUrl}}/depenses
Authorization: Bearer <token-B>
```

### Résultat attendu

``` text
200 OK
```

Mais aucune dépense appartenant à A ne doit apparaître.

------------------------------------------------------------------------

# 8. ENTRÉES --- `/entrees`

**Nombre de tests : 9**

Les tests des entrées suivent la même logique fonctionnelle et de
sécurité que les dépenses.

------------------------------------------------------------------------

## ENT-01 --- Créer une entrée

### Requête

``` http
POST {{baseUrl}}/entrees
Authorization: Bearer {{accessToken}}
Content-Type: application/json
```

### Body

``` json
{
  "montant": 150.00,
  "libelle": "Salaire",
  "description": "Test",
  "date": "2026-09-08"
}
```

### Résultat attendu

``` text
201 Created
```

L'entrée doit être liée à l'utilisateur du token.

Le `userId` ne doit pas être fourni par le client pour choisir le
propriétaire.

------------------------------------------------------------------------

## ENT-02 --- Montant négatif

### Body

``` json
{
  "montant": -10,
  "libelle": "Salaire",
  "description": "Test",
  "date": "2026-09-08"
}
```

### Résultat attendu

``` text
400 Bad Request
```

------------------------------------------------------------------------

## ENT-03 --- Montant zéro

### Body

``` json
{
  "montant": 0,
  "libelle": "Salaire",
  "description": "Test",
  "date": "2026-09-08"
}
```

### Résultat attendu

``` text
400 Bad Request
```

Le montant doit être strictement positif.

------------------------------------------------------------------------

## ENT-04 --- Champs obligatoires manquants

### Body

``` json
{}
```

### Résultat attendu

``` text
400 Bad Request
```

Les champs concernés sont notamment :

-   `montant`
-   `libelle`
-   `date`

------------------------------------------------------------------------

## ENT-05 --- Injection d'un `userId`

### Body

``` json
{
  "montant": 150.00,
  "libelle": "Salaire",
  "description": "Test",
  "date": "2026-09-08",
  "userId": 9999
}
```

### Résultat attendu

``` text
201 Created
```

L'entrée doit rester liée à l'utilisateur authentifié et jamais à
`9999`.

------------------------------------------------------------------------

## ENT-06 --- Liste par défaut : jour

### Requête

``` http
GET {{baseUrl}}/entrees
Authorization: Bearer {{accessToken}}
```

### Résultat attendu

``` text
200 OK
```

Seules les entrées du jour courant sont retournées.

------------------------------------------------------------------------

## ENT-07 --- Liste par mois

### Requête

``` http
GET {{baseUrl}}/entrees?periode=mois
Authorization: Bearer {{accessToken}}
```

### Résultat attendu

``` text
200 OK
```

La réponse couvre le mois calendaire courant.

------------------------------------------------------------------------

## ENT-08 --- Plage `from/to`

### Requête

``` http
GET {{baseUrl}}/entrees?from=2026-01-01&to=2026-12-31
Authorization: Bearer {{accessToken}}
```

### Résultat attendu

``` text
200 OK
```

Toutes les entrées de l'utilisateur situées dans la période sont
retournées.

------------------------------------------------------------------------

## ENT-09 --- Isolation entre utilisateurs

### Procédure

1.  Compte A crée une entrée.
2.  Compte B se connecte.
3.  B appelle :

``` http
GET {{baseUrl}}/entrees
Authorization: Bearer <token-B>
```

### Résultat attendu

La réponse de B ne contient aucune entrée de A.

------------------------------------------------------------------------

# 9. DASHBOARD --- `/dashboard`

**Nombre de tests : 4**

------------------------------------------------------------------------

## DASH-01 --- Dashboard du jour

### Préparation

Créer au minimum :

-   une dépense datée du jour ;
-   une entrée datée du jour.

### Requête

``` http
GET {{baseUrl}}/dashboard?periode=jour
Authorization: Bearer {{accessToken}}
```

### Résultat attendu

``` text
200 OK
```

Vérifier :

``` text
nombreDepenses
totalDepenses
nombreEntrees
totalEntrees
solde
```

Le calcul du solde doit respecter :

``` text
solde = totalEntrees - totalDepenses
```

### Exemple

Si :

``` text
totalEntrees = 150
totalDepenses = 50
```

alors :

``` text
solde = 100
```

------------------------------------------------------------------------

## DASH-02 --- Dashboard mensuel

### Requête

``` http
GET {{baseUrl}}/dashboard?periode=mois
Authorization: Bearer {{accessToken}}
```

### Résultat attendu

``` text
200 OK
```

Les totaux doivent correspondre aux mouvements du mois calendaire
courant.

------------------------------------------------------------------------

## DASH-03 --- Dashboard sans données

### Préparation

Utiliser un utilisateur fraîchement inscrit, sans dépense ni entrée.

### Requête

``` http
GET {{baseUrl}}/dashboard?periode=jour
Authorization: Bearer {{accessToken}}
```

### Résultat attendu

``` json
{
  "nombreDepenses": 0,
  "totalDepenses": 0,
  "nombreEntrees": 0,
  "totalEntrees": 0,
  "solde": 0
}
```

Il ne doit y avoir :

-   aucun `null` inattendu ;
-   aucune division problématique ;
-   aucune erreur `500`.

------------------------------------------------------------------------

## DASH-04 --- Isolation du dashboard

### Préparation

-   Compte A possède plusieurs mouvements.
-   Compte B ne possède aucun mouvement.

### Requête avec le token de B

``` http
GET {{baseUrl}}/dashboard
Authorization: Bearer <token-B>
```

### Résultat attendu

Tous les totaux de B doivent être à zéro.

L'activité de A ne doit avoir aucune influence sur les résultats de B.

------------------------------------------------------------------------

# 10. ROBUSTESSE GÉNÉRALE

**Nombre de tests : 3**

------------------------------------------------------------------------

## ERR-01 --- JSON malformé

### Objectif

Vérifier qu'un JSON incorrect est traité comme une erreur client.

### Requête

``` http
POST {{baseUrl}}/depenses
Authorization: Bearer {{accessToken}}
Content-Type: application/json
```

### Body volontairement incorrect

``` json
{
  "montant": 100,
  "libelle":
}
```

### Résultat attendu

``` text
400 Bad Request
```

### Résultat interdit

``` text
500 Internal Server Error
```

Le handler de `HttpMessageNotReadableException` doit traiter
correctement ce cas.

------------------------------------------------------------------------

## ERR-02 --- Route inexistante

### Requête

``` http
GET {{baseUrl}}/route-qui-nexiste-pas
```

### Résultat attendu

``` text
404 Not Found
```

Aucune erreur serveur ne doit être générée.

------------------------------------------------------------------------

## ERR-03 --- Absence de stack trace

### Objectif

Réaliser un audit transversal de toutes les réponses d'erreur obtenues
pendant la suite.

### Vérifier l'absence de :

``` text
trace
```

noms de classes Java, chemins de fichiers, numéros de ligne ou détails
internes.

### Une réponse d'erreur doit rester limitée aux informations utiles, par exemple :

``` json
{
  "timestamp": "...",
  "status": 400,
  "message": "Requête invalide"
}
```

ou, pour une validation :

``` json
{
  "timestamp": "...",
  "status": 400,
  "errors": {
    "email": "..."
  }
}
```

------------------------------------------------------------------------

# 11. Matrice récapitulative

  Section            Tests                 Nombre
  ------------------ ------------------- --------
  Authentification   AUTH-01 → AUTH-16         16
  Sécurité           SEC-01 → SEC-05            5
  Profil             USER-01 → USER-05          5
  Administration     ADM-01 → ADM-04            4
  Dépenses           DEP-01 → DEP-09            9
  Entrées            ENT-01 → ENT-09            9
  Dashboard          DASH-01 → DASH-04          4
  Robustesse         ERR-01 → ERR-03            3
  **TOTAL**                                **55**

------------------------------------------------------------------------

# 12. Checklist d'exécution Bruno

## Authentification

-   [ ] AUTH-01 --- Inscription nominale
-   [ ] AUTH-02 --- Email déjà utilisé
-   [ ] AUTH-03 --- Mot de passe faible
-   [ ] AUTH-04 --- Champs manquants
-   [ ] AUTH-05 --- Email invalide
-   [ ] AUTH-06 --- Login nominal
-   [ ] AUTH-07 --- Mauvais mot de passe
-   [ ] AUTH-08 --- Email inexistant
-   [ ] AUTH-09 --- Compte désactivé
-   [ ] AUTH-10 --- Refresh nominal
-   [ ] AUTH-11 --- Access token utilisé comme refresh
-   [ ] AUTH-12 --- Refresh token utilisé comme access
-   [ ] AUTH-13 --- Refresh token invalide
-   [ ] AUTH-14 --- Rate limiting login
-   [ ] AUTH-15 --- Rate limiting register
-   [ ] AUTH-16 --- Logout

## Sécurité

-   [ ] SEC-01 --- Sans token
-   [ ] SEC-02 --- Token invalide
-   [ ] SEC-03 --- Token expiré
-   [ ] SEC-04 --- CLIENT vers route ADMIN
-   [ ] SEC-05 --- CORS

## Profil

-   [ ] USER-01 --- Consultation
-   [ ] USER-02 --- Modification du nom
-   [ ] USER-03 --- Modification du mot de passe
-   [ ] USER-04 --- Email déjà utilisé
-   [ ] USER-05 --- Accès à `/users/{id}`

## Administration

-   [ ] ADM-01 --- Liste avec ADMIN
-   [ ] ADM-02 --- Liste avec CLIENT
-   [ ] ADM-03 --- Seed ADMIN
-   [ ] ADM-04 --- Idempotence du seed

## Dépenses

-   [ ] DEP-01 --- Création
-   [ ] DEP-02 --- Montant négatif
-   [ ] DEP-03 --- Montant zéro
-   [ ] DEP-04 --- Champs manquants
-   [ ] DEP-05 --- Injection userId
-   [ ] DEP-06 --- Filtre jour
-   [ ] DEP-07 --- Filtre mois
-   [ ] DEP-08 --- Filtre from/to
-   [ ] DEP-09 --- Isolation

## Entrées

-   [ ] ENT-01 --- Création
-   [ ] ENT-02 --- Montant négatif
-   [ ] ENT-03 --- Montant zéro
-   [ ] ENT-04 --- Champs manquants
-   [ ] ENT-05 --- Injection userId
-   [ ] ENT-06 --- Filtre jour
-   [ ] ENT-07 --- Filtre mois
-   [ ] ENT-08 --- Filtre from/to
-   [ ] ENT-09 --- Isolation

## Dashboard

-   [ ] DASH-01 --- Jour
-   [ ] DASH-02 --- Mois
-   [ ] DASH-03 --- Aucune donnée
-   [ ] DASH-04 --- Isolation

## Robustesse

-   [ ] ERR-01 --- JSON malformé
-   [ ] ERR-02 --- Route inexistante
-   [ ] ERR-03 --- Pas de stack trace

------------------------------------------------------------------------

# 13. Gestion des anomalies

Pour chaque test échoué, documenter :

``` text
ID du test :
Date :
Endpoint :
Méthode HTTP :
Résultat attendu :
Résultat obtenu :
Code HTTP :
Message :
Étapes pour reproduire :
Impact :
Gravité :
Capture Bruno :
Correction proposée :
Statut :
```

Exemple :

``` text
ID : USER-04
Endpoint : PUT /users/me
Attendu : 409 Conflict
Obtenu : 500 Internal Server Error
Problème : l'unicité de l'email n'est pas contrôlée avant sauvegarde.
Impact : erreur serveur exposée à l'utilisateur.
Correction : vérifier l'existence de l'email avant update et retourner une erreur métier 409.
Statut : À corriger
```

------------------------------------------------------------------------

# 14. Anomalies et limitations connues

## 14.1 USER-04 --- Unicité de l'email

Le code actuel ne vérifie pas explicitement l'unicité de l'email lors de
la modification du profil.

Conséquence possible :

``` text
500 Internal Server Error
```

au lieu d'un :

``` text
409 Conflict
```

Cette anomalie doit être corrigée.

------------------------------------------------------------------------

## 14.2 Rate limiting en mémoire

Le rate limiting des tests AUTH-14 et AUTH-15 fonctionne en mémoire
locale de l'instance.

Sur un déploiement comportant plusieurs instances :

``` text
Instance A → limite indépendante
Instance B → limite indépendante
```

La limite n'est donc pas nécessairement globale au système.

Une architecture distribuée nécessiterait un mécanisme partagé si cette
exigence devient nécessaire.

------------------------------------------------------------------------

## 14.3 Logout JWT stateless

Le logout retourne :

``` text
204 No Content
```

mais ne révoque pas serveur les access tokens déjà émis.

Un access token déjà valide reste utilisable jusqu'à son expiration
naturelle.

Cette situation doit être documentée comme une **limitation connue** du
fonctionnement JWT stateless et non comme un bug du endpoint logout.

------------------------------------------------------------------------

# 15. Hors périmètre

Cette suite ne couvre pas :

### Tests de charge

Non inclus :

-   forte volumétrie ;
-   tests de concurrence ;
-   tests de montée en charge ;
-   endurance ;
-   stress test.

### Tests d'intrusion approfondis

Non inclus :

-   fuzzing avancé ;
-   campagnes d'injection complexes ;
-   tests d'intrusion complets ;
-   scénarios d'attaque spécialisés.

L'utilisation de l'ORM apporte déjà une mitigation naturelle contre
certaines injections SQL, mais cela ne remplace pas une campagne de
sécurité dédiée.

------------------------------------------------------------------------

# 16. Critères de validation finale

La suite peut être considérée comme validée lorsque :

-   les 55 tests ont été exécutés ;
-   tous les résultats attendus sont obtenus ;
-   aucune erreur `500` injustifiée n'est observée ;
-   aucun mot de passe n'est exposé dans les réponses ;
-   les access tokens et refresh tokens sont correctement séparés ;
-   les routes ADMIN sont inaccessibles aux CLIENT ;
-   les données des utilisateurs sont correctement isolées ;
-   les calculs du dashboard sont cohérents ;
-   les erreurs ne contiennent pas de stack trace ;
-   les anomalies connues sont documentées ;
-   les limitations connues sont clairement identifiées.

------------------------------------------------------------------------

# 17. Résultat final à renseigner

  ID                  Résultat        Observations
  ------------------- --------------- --------------
  AUTH-01             ☐ PASS ☐ FAIL   
  AUTH-02             ☐ PASS ☐ FAIL   
  AUTH-03             ☐ PASS ☐ FAIL   
  AUTH-04             ☐ PASS ☐ FAIL   
  AUTH-05             ☐ PASS ☐ FAIL   
  AUTH-06             ☐ PASS ☐ FAIL   
  AUTH-07             ☐ PASS ☐ FAIL   
  AUTH-08             ☐ PASS ☐ FAIL   
  AUTH-09             ☐ PASS ☐ FAIL   
  AUTH-10             ☐ PASS ☐ FAIL   
  AUTH-11             ☐ PASS ☐ FAIL   
  AUTH-12             ☐ PASS ☐ FAIL   
  AUTH-13             ☐ PASS ☐ FAIL   
  AUTH-14             ☐ PASS ☐ FAIL   
  AUTH-15             ☐ PASS ☐ FAIL   
  AUTH-16             ☐ PASS ☐ FAIL   
  SEC-01 à SEC-05     ☐ PASS ☐ FAIL   
  USER-01 à USER-05   ☐ PASS ☐ FAIL   
  ADM-01 à ADM-04     ☐ PASS ☐ FAIL   
  DEP-01 à DEP-09     ☐ PASS ☐ FAIL   
  ENT-01 à ENT-09     ☐ PASS ☐ FAIL   
  DASH-01 à DASH-04   ☐ PASS ☐ FAIL   
  ERR-01 à ERR-03     ☐ PASS ☐ FAIL   

------------------------------------------------------------------------

## Conclusion

Cette suite de **55 tests** permet de vérifier de manière structurée les
principales fonctionnalités et garanties de sécurité du backend Budget
Tracker.

Elle doit être exécutée dans l'ordre afin de limiter les faux échecs
liés aux dépendances entre tests.

Les trois points actuellement identifiés comme particulièrement
importants à surveiller sont :

1.  **USER-04** : gestion de l'unicité de l'email lors d'une
    modification ;
2.  **AUTH-14 / AUTH-15** : portée du rate limiting en environnement
    multi-instance ;
3.  **AUTH-16** : absence de révocation serveur des JWT après logout.

Les résultats réels obtenus avec Bruno devront être reportés dans la
matrice finale afin de transformer ce document de plan de tests en
véritable rapport d'exécution.
