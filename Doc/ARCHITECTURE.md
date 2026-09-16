# Architecture du backend — Budget Tracker

Ce document explique comment le projet est organisé et ce qui se passe concrètement, étape par étape, entre le moment où une requête HTTP arrive sur le serveur et le moment où une réponse est renvoyée au client.

---

## 1. Vue d'ensemble de la stack

- **Framework** : Spring Boot 4.1.1 (Java 21)
- **Sécurité** : Spring Security + JWT (JJWT)
- **Persistance** : Spring Data JPA + Hibernate + MySQL
- **Validation** : Jakarta Bean Validation
- **Hash des mots de passe** : BCrypt

---

## 2. Architecture en couches

Le projet est organisé en couches empilées, chacune ne parlant qu'à la couche immédiatement voisine :

```
Client (Bruno, navigateur, app mobile...)
        │
        ▼
┌─────────────────────────────────────┐
│         FILTRES (security)           │  ← s'exécutent AVANT tout le reste
│  AuthRateLimitFilter → JwtAuthFilter  │
└─────────────────────────────────────┘
        │
        ▼
┌─────────────────────────────────────┐
│            CONTROLLER                │  ← reçoit la requête HTTP, aucune logique métier
└─────────────────────────────────────┘
        │
        ▼
┌─────────────────────────────────────┐
│             SERVICE                  │  ← toute la logique métier vit ici
└─────────────────────────────────────┘
        │
        ▼
┌─────────────────────────────────────┐
│           REPOSITORY                 │  ← accès aux données (Spring Data JPA)
└─────────────────────────────────────┘
        │
        ▼
┌─────────────────────────────────────┐
│         BASE DE DONNÉES (MySQL)       │
└─────────────────────────────────────┘
```

### Rôle de chaque couche

| Couche | Package | Rôle |
|---|---|---|
| Filtres | `security` | Authentifier la requête (JWT) et limiter les abus (rate limiting), avant même qu'un controller ne soit atteint |
| Controller | `controller` | Exposer une route HTTP, valider le format de la requête (`@Valid`), déléguer au service, renvoyer une réponse |
| Service | `service` | Règles métier, calculs, vérification de propriété des ressources (anti-IDOR) |
| Repository | `repository` | Requêtes vers la base, aucune règle métier |
| Entity | `entity` | Représentation des tables SQL |
| DTO | `dto.request` / `dto.response` | Objets de transfert — jamais les entités exposées directement |
| Config | `config` | Assemblage de la sécurité (`SecurityConfig`), initialisation (`AdminSeeder`) |
| Exception | `exception` | Traduction des erreurs en réponses HTTP propres |

---

## 3. Le trajet complet d'une requête

### 3.1 Ce qui se passe pour TOUTE requête, avant même le controller

Chaque requête HTTP entrante traverse une **chaîne de filtres** Spring Security, dans un ordre précis défini dans `SecurityConfig` :

```
Requête HTTP entrante
        │
        ▼
1. AuthRateLimitFilter
   - Concerne uniquement /auth/login et /auth/register
   - Si > 5 requêtes/minute pour la même IP+route → 429, la requête s'arrête ici
   - Sinon → continue
        │
        ▼
2. JwtAuthFilter
   - Lit l'en-tête Authorization: Bearer <token>
   - Si absent → laisse passer SANS authentifier (la décision d'accès vient après)
   - Si présent et token de type "access" valide → place l'utilisateur authentifié
     dans le SecurityContextHolder (mémoire valable pour cette requête uniquement)
   - Si présent mais invalide/expiré/mauvais type → laisse passer SANS authentifier
        │
        ▼
3. Vérification des droits (SecurityConfig.authorizeHttpRequests)
   - /auth/**      → toujours autorisé (permitAll)
   - /admin/**     → exige le rôle ADMIN, sinon 403
   - tout le reste → exige d'être authentifié, sinon 401
        │
        ▼
   Requête transmise au Controller correspondant
```

### 3.2 Ce qui se passe dans le controller, le service, le repository

```
Controller
   │  - Spring désérialise le JSON du corps en objet DTO (@RequestBody)
   │  - @Valid déclenche la validation (annotations sur le DTO) → 400 si invalide
   │  - Le controller appelle UNE méthode du service, rien d'autre
   ▼
Service
   │  - Récupère l'identité de l'utilisateur connecté via CurrentUser (jamais un id
   │    envoyé par le client) — c'est ici que la protection anti-IDOR s'applique
   │  - Applique les règles métier (ex: vérifier la propriété d'une ressource)
   │  - Appelle un ou plusieurs repositories
   ▼
Repository
   │  - Traduit l'appel en requête SQL (générée par Spring Data JPA ou écrite
   │    explicitement en JPQL avec @Query)
   │  - Toutes les requêtes sur Depense/Entree sont scopées par userId
   ▼
Base de données MySQL
   │  - Exécute la requête, renvoie le résultat
   ▼
Le résultat remonte : Repository → Service → Controller
   │  - Le service (ou le controller) convertit l'entité en DTO de réponse
   │    (ex: DepenseResponse.fromEntity(...)) — jamais l'entité brute
   ▼
Réponse HTTP (JSON) renvoyée au client
```

### 3.3 En cas d'erreur, à n'importe quelle étape

```
Une exception est levée (ex: ResourceNotFoundException, BadCredentialsException,
échec de validation @Valid, erreur inattendue...)
        │
        ▼
GlobalExceptionHandler (@RestControllerAdvice) intercepte l'exception
   - Choisit le handler correspondant au type précis de l'exception
   - Construit une réponse JSON propre : { timestamp, status, message }
   - Ne renvoie JAMAIS de stack trace ni de détail technique au client
        │
        ▼
Réponse d'erreur HTTP renvoyée au client
```

---

## 4. Exemple concret : trace complète de `POST /depenses`

Un client authentifié envoie une nouvelle dépense.

1. **AuthRateLimitFilter** : route non concernée (`/depenses` ≠ `/auth/login|register`) → passe.
2. **JwtAuthFilter** : lit `Authorization: Bearer eyJ...`, vérifie la signature, l'expiration, et que le claim `type` vaut `access`. Si tout est bon, recharge l'utilisateur depuis la base (`UserDetailsServiceImpl`) et place son identité dans le `SecurityContextHolder`.
3. **SecurityConfig** : `/depenses` n'est ni `/auth/**` ni `/admin/**` → tombe dans `anyRequest().authenticated()`. L'utilisateur est authentifié → accès autorisé.
4. **DepenseController.create(...)** : Spring désérialise le JSON en `MouvementRequest`, `@Valid` vérifie que `montant > 0`, que `libelle` et `date` sont présents. Si KO → 400 immédiatement, le service n'est jamais appelé.
5. **DepenseService.create(...)** : appelle `currentUser.id()` pour savoir QUI fait la demande (jamais un champ du body). Construit une entité `Depense` liée à cet utilisateur. Appelle `depenseRepository.save(...)`.
6. **DepenseRepository** : Hibernate génère l'`INSERT INTO depenses (...)`. La contrainte `user_id NOT NULL` + la clé étrangère garantissent qu'aucune dépense orpheline n'est possible.
7. Retour : le service (via le controller) convertit l'entité sauvegardée en `DepenseResponse` (jamais l'entité brute, pour ne jamais exposer la relation `user` complète).
8. Le controller renvoie `201 Created` avec le JSON de la dépense créée.

---

## 5. Où trouver quoi

```
com.budget.subget_backend
├── config/
│   ├── SecurityConfig.java      → assemble toute la sécurité, définit les routes publiques/protégées
│   └── AdminSeeder.java         → crée le compte admin au démarrage
├── controller/                  → une classe par ressource exposée en HTTP
├── dto/
│   ├── request/                 → ce que le client envoie
│   └── response/                → ce que l'API renvoie
├── entity/                      → mapping vers les tables SQL
├── exception/                   → exceptions custom + GlobalExceptionHandler
├── repository/                  → accès aux données (Spring Data JPA)
├── security/                    → JWT, filtres, rate limiting, identité de l'utilisateur courant
├── service/                     → toute la logique métier
├── util/
│   └── PeriodeResolver.java     → résout jour/mois/plage explicite pour les filtres de dates
└── SubgetBackendApplication.java → point d'entrée
```

---

## 6. Pour aller plus loin

Ce fichier donne la vue d'ensemble du fonctionnement. Pour le détail ligne par ligne de chaque classe (pourquoi chaque annotation, chaque choix technique, et l'explication approfondie de la sécurité), voir le document **Documentation technique complète** (Word).
