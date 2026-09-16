# Documentation technique — Subget Backend

Ce document explique **chaque classe** du projet `subget_backend`, **pourquoi** elle existe, **dans quelles conditions** elle intervient, ses **avantages/inconvénients**, ainsi que les fichiers `application.yml`. Une section dédiée détaille les mécanismes de sécurité clés (anti brute-force, anti-IDOR, gestion des tokens).

---

## 1. Vue d'ensemble de l'architecture

Le projet suit une architecture Spring Boot en couches classique :

```
Controller  →  Service  →  Repository  →  Base de données
   ↑              ↑
   DTO         Security (CurrentUser, JwtUtils, filtres)
```

- **Pourquoi cette séparation ?** Chaque couche a une seule responsabilité : le `Controller` reçoit/renvoie du HTTP, le `Service` porte la logique métier, le `Repository` parle à la base. Ça facilite les tests, la maintenance, et empêche la logique métier de fuiter dans les contrôleurs.
- **Domaine métier** : une application de gestion de budget personnel — un utilisateur enregistre des `Depense` (dépenses) et des `Entree` (entrées d'argent), et consulte un `Dashboard` résumant sa situation financière sur une période.

---

## 2. Fichiers `application.yml`

### 2.1 `application.yml` (commun, tous profils)

```yaml
spring:
  application:
    name: subget_backend
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:dev}
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true
server:
  port: ${PORT:8080}
  error:
    include-stacktrace: never
    include-message: never
jwt:
  access-token-expiration-ms: 900000      # 15 minutes
  refresh-token-expiration-ms: 604800000  # 7 jours
```

**Pourquoi ces choix :**
- `profiles.active: ${SPRING_PROFILES_ACTIVE:dev}` : utilise la variable d'environnement si elle existe, sinon retombe sur `dev`. Avantage : en local, zéro configuration nécessaire ; en prod, on force explicitement `prod` via une variable d'environnement — pas de risque d'oublier de changer un fichier.
- `error.include-stacktrace: never` / `include-message: never` : **condition de sécurité** — n'expose jamais à un client les détails internes d'une erreur (stack trace, message SQL, etc.), qui pourraient révéler la structure du code ou de la base à un attaquant.
- `jwt.access-token-expiration-ms` / `refresh-token-expiration-ms` communs aux deux profils : ce ne sont **pas des secrets**, juste des durées, donc pas besoin de les dupliquer par profil.

### 2.2 `application-dev.yml`

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/subget_backend?createDatabaseIfNotExist=true...
    username: root
    password: root
jwt:
  secret: dev-only-secret-change-me-in-production-min-32-chars
admin:
  seed:
    email: admin@subget.local
    password: ChangeMe123!
    nom: Super Admin
```

**Pourquoi :** profil de confort pour développer en local sans rien configurer — base MySQL locale avec identifiants par défaut, secret JWT et admin de test codés en dur. **Condition explicite** : ce fichier ne doit **jamais** être utilisé en production (voir commentaire dans le YAML original), car le secret JWT y est connu de tous ceux qui ont accès au dépôt.

### 2.3 `application-prod.yml`

```yaml
spring:
  datasource:
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
jwt:
  secret: ${JWT_SECRET}
admin:
  seed:
    email: ${ADMIN_SEED_EMAIL}
    password: ${ADMIN_SEED_PASSWORD}
    nom: ${ADMIN_SEED_NOM:Super Admin}
```

**Pourquoi ces choix, et l'avantage de ne mettre AUCUNE valeur par défaut :**
- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `ADMIN_SEED_EMAIL`, `ADMIN_SEED_PASSWORD` n'ont **pas de valeur de repli** (`:defaut`). Si l'une de ces variables d'environnement manque au démarrage, Spring lève une erreur et **l'application refuse de démarrer**.
- **Avantage** : un défaut de configuration en prod (variable oubliée) casse le déploiement de façon visible et immédiate, plutôt que de démarrer silencieusement avec le secret JWT de dev ou un mot de passe admin faible connu de tous. C'est un principe de sécurité *fail-closed* (échouer de façon sûre) plutôt que *fail-open*.
- `ddl-auto: validate` (au lieu de `update` en dev) : Hibernate **vérifie** que le schéma correspond aux entités mais ne le modifie jamais tout seul. Condition : le schéma réel doit être géré par un outil de migration explicite (Flyway/Liquibase, mentionné en commentaire comme piste). Avantage : élimine le risque qu'Hibernate modifie accidentellement le schéma de prod.
- `show-sql: false` : évite de logguer les requêtes SQL (potentiellement avec des données sensibles) dans les logs de prod.
- `ADMIN_SEED_NOM:Super Admin` garde un défaut car ce n'est pas sensible (juste un nom d'affichage).

---

## 3. Package `config`

### 3.1 `AdminSeeder`

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class AdminSeeder implements CommandLineRunner { ... }
```

**Rôle :** crée automatiquement un compte super-admin au démarrage de l'application, s'il n'existe pas déjà.

**Pourquoi `CommandLineRunner` :** c'est une interface Spring Boot dont la méthode `run()` est exécutée automatiquement **une fois, juste après le démarrage complet du contexte Spring**. C'est l'outil standard pour exécuter du code d'initialisation (seed de données) sans avoir à le déclencher manuellement.

**Condition d'idempotence :**
```java
if (userRepository.existsByEmail(adminEmail)) {
    log.info("Compte admin déjà présent, seed ignoré.");
    return;
}
```
Avantage : on peut redémarrer l'application autant de fois qu'on veut (redéploiements, restarts) sans jamais dupliquer le compte admin ni écraser son mot de passe.

**Pourquoi `@Value("${admin.seed.email}")` etc. :** injecte les valeurs directement depuis `application.yml` (donc différentes entre `dev` et `prod`), au lieu de les coder en dur dans la classe Java. Avantage : la même classe fonctionne dans tous les environnements.

**Point de sécurité :** le mot de passe est **hashé avant insertion** (`passwordEncoder.encode(adminPassword)`) et **jamais loggé**, même sous forme hashée (`log.info` n'affiche que l'email).

### 3.2 `SecurityConfig`

C'est la classe centrale qui configure Spring Security. Annotations :

- `@Configuration` : indique à Spring que cette classe fournit des `@Bean`.
- `@EnableMethodSecurity` : active l'utilisation de `@PreAuthorize` sur les contrôleurs/services (utilisé ensuite dans `AdminController`). Sans cette annotation, `@PreAuthorize` serait ignoré silencieusement.

**`passwordEncoder()` — pourquoi `BCryptPasswordEncoder(12)` :**
```java
return new BCryptPasswordEncoder(12); // coût 12 : bon compromis sécurité/performance
```
Le "coût" (`strength`) contrôle le nombre d'itérations de l'algorithme BCrypt. Plus il est élevé, plus le hash est lent à calculer — ce qui est **volontaire** : ça rend les attaques par force brute sur des mots de passe volés beaucoup plus coûteuses. 12 est une valeur standard qui équilibre sécurité (assez lent pour freiner un attaquant) et performance (pas trop lent pour l'utilisateur légitime qui se connecte).

**`filterChain()` — condition par condition :**
```java
.csrf(csrf -> csrf.disable())
```
Pourquoi désactiver CSRF ? La protection CSRF sert à protéger les sessions basées sur des **cookies**. Ici, l'API est **stateless** (sans session), consommée par un frontend séparé qui envoie un token JWT dans le header `Authorization`. Il n'y a donc pas de cookie de session à protéger contre le CSRF — la désactiver est cohérente avec ce choix d'architecture, pas un oubli.

```java
.sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
```
Condition : Spring ne crée ni ne maintient de session HTTP. Chaque requête doit se réauthentifier via son JWT. Avantage : scalabilité (pas d'état côté serveur à synchroniser entre plusieurs instances).

```java
.authorizeHttpRequests(auth -> auth
        .requestMatchers("/auth/**").permitAll()
        .requestMatchers("/admin/**").hasRole("ADMIN")
        .anyRequest().authenticated()
)
```
Règles d'autorisation, évaluées dans l'ordre :
1. `/auth/**` (login, register, refresh, logout) accessible sans authentification — logique, puisque c'est justement là qu'on obtient un token.
2. `/admin/**` réservé au rôle `ADMIN`.
3. Tout le reste nécessite d'être authentifié.

```java
.addFilterBefore(authRateLimitFilter, JwtAuthFilter.class)
.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
```
Ordre des filtres dans la chaîne : `AuthRateLimitFilter` s'exécute **avant** `JwtAuthFilter`, qui s'exécute avant le filtre standard de Spring Security. Pourquoi cet ordre précis : on veut bloquer une tentative de brute-force **avant** même de perdre du temps à essayer de décoder un JWT.

**`corsConfigurationSource()` :** autorise uniquement `http://localhost:3000` (le frontend en dev) à appeler l'API depuis un navigateur, avec les méthodes HTTP et headers nécessaires. Le commentaire précise qu'il faudra **restreindre au(x) vrai(s) domaine(s) du frontend en production** — actuellement configuré uniquement pour le développement.

---

## 4. Package `controller`

Les contrôleurs sont volontairement **fins** (peu de logique) : ils valident l'entrée (`@Valid`), délèguent au service, et formatent la réponse HTTP.

### 4.1 `AuthController` (`/auth`)
- `POST /auth/register` → `AuthService.register()`, renvoie `201 Created`.
- `POST /auth/login` → `AuthService.login()`, renvoie les tokens.
- `POST /auth/refresh-token` → renouvelle les tokens.
- `POST /auth/logout` → renvoie `204 No Content`. **Pourquoi rien de plus ?** Le commentaire explique : en JWT stateless, la déconnexion se fait **côté client** (suppression du token). Pour une vraie invalidation côté serveur (ex: si un token est compromis), il faudrait une blocklist des tokens (Redis suggéré), non implémentée ici — c'est une limite assumée du modèle stateless.

### 4.2 `AdminController` (`/admin`)
```java
@PreAuthorize("hasRole('ADMIN')")
@GetMapping("/users")
public ResponseEntity<List<UserResponse>> listUsers() { ... }
```
**Pourquoi `@PreAuthorize` alors que `SecurityConfig` protège déjà `/admin/**` ?** C'est de la **défense en profondeur** (defense in depth) : deux couches de vérification indépendantes. Si un jour la règle globale dans `SecurityConfig` est modifiée ou mal réécrite, cette vérification locale reste un filet de sécurité.

### 4.3 `DepenseController` / `EntreeController`
Suivent le même schéma : `POST` pour créer un mouvement, `GET` pour lister sur une période. Les deux délèguent le calcul de la période à `PeriodeResolver` (voir section utilitaires) plutôt que de dupliquer cette logique dans chaque contrôleur.

### 4.4 `DashboardController` (`/dashboard`)
Un seul `GET`, qui résout la période demandée puis appelle `DashboardService`.

### 4.5 `UserController` (`/users`)
`GET /users/me` et `PUT /users/me` : toujours **"me"**, jamais un ID passé en paramètre — la cible est toujours l'utilisateur authentifié (voir `CurrentUser` plus bas), ce qui empêche un utilisateur de consulter/modifier le profil d'un autre.

---

## 5. Package `dto`

### 5.1 Requêtes (`dto.request`)

**`LoginRequest`** : `email` (`@NotBlank @Email`) + `motDePasse` (`@NotBlank`). Simple, car le login n'a besoin que de vérifier une identité existante.

**`RegisterRequest`** :
```java
@Pattern(
    regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@#$%^&+=!?_-]).{8,}$",
    message = "Le mot de passe doit contenir au moins 8 caractères..."
)
private String motDePasse;
```
**Pourquoi un message générique ?** Le commentaire du code l'explique : le message ne précise **pas** quelle règle exacte a échoué (longueur ? majuscule manquante ? etc.), pour ne pas donner d'indices précis à un attaquant essayant de deviner la politique de mot de passe, tout en restant compréhensible pour un utilisateur légitime.

**Pourquoi pas de champ `role` dans `RegisterRequest` :** c'est **volontaire**. L'inscription est un endpoint public (`permitAll()`), donc si le client pouvait envoyer `"role": "ADMIN"` dans le corps de la requête, n'importe qui pourrait devenir administrateur. En ne mettant simplement pas ce champ dans le DTO, il est physiquement impossible à un client de l'influencer — le service assigne `Role.CLIENT` en dur (voir `AuthService`).

**`MouvementRequest`** : DTO **partagé** entre `Depense` et `Entree`. Le champ `libelle` sert de `categorie` pour une dépense et de `source` pour une entrée. **Avantage** : évite la duplication de deux DTOs quasi identiques. **Inconvénient assumé** (noté en commentaire) : si les règles métier de dépense et d'entrée divergent un jour (validations différentes, champs supplémentaires propres à l'un des deux), il faudra les séparer.

**`UpdateUserRequest`** : tous les champs sont optionnels (pas de `@NotBlank`), car c'est une mise à jour partielle — l'utilisateur peut ne changer que son nom, par exemple.

### 5.2 Réponses (`dto.response`)

**`UserResponse`** :
```java
// Ne mappe jamais motDePasse : ce DTO est la seule vue exposée au client.
```
Point de sécurité central : même hashé, le mot de passe **ne quitte jamais le backend**. C'est le DTO, pas l'entité `User`, qui est renvoyé au client — donc il est structurellement impossible d'exposer le hash par erreur (contrairement à si on renvoyait l'entité JPA directement).

**`AuthResponse`** : porte `accessToken`, `refreshToken`, `tokenType = "Bearer"` (constante, toujours la même valeur — c'est la convention du standard OAuth2/JWT pour indiquer au client comment utiliser le token dans le header `Authorization: Bearer <token>`).

**`DepenseResponse` / `EntreeResponse` / `DashboardResponse`** : DTOs de sortie simples, construits via `fromEntity()` (méthode statique de fabrique) pour centraliser la conversion entité → DTO à un seul endroit.

---

## 6. Package `entity`

### 6.1 `User`
```java
@Column(nullable = false, unique = true)
private String email;

// Toujours stocké hashé (BCrypt) - jamais en clair.
private String motDePasse;

@OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
private List<Depense> depenses = new ArrayList<>();
```
- `unique = true` sur `email` : contrainte **au niveau base de données**, en plus de la vérification applicative (`existsByEmail`) — double garantie contre les doublons, même en cas de race condition entre deux inscriptions simultanées.
- `cascade = CascadeType.ALL, orphanRemoval = true` : si un `User` est supprimé, toutes ses `Depense`/`Entree` le sont aussi automatiquement. Avantage : pas de données orphelines en base. Condition d'usage : adapté ici car une dépense n'a de sens que rattachée à un utilisateur.
- `@PrePersist` sur `onCreate()` : fixe `dateCreation` automatiquement à l'insertion, côté serveur — pas moyen pour le client de la falsifier.

### 6.2 `Depense` / `Entree`
```java
// Ne JAMAIS renseigner cette relation depuis une valeur envoyée par le
// client : toujours déduite de l'utilisateur authentifié (JWT).
@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "user_id", nullable = false)
private User user;
```
- `fetch = FetchType.LAZY` : la relation `User` n'est chargée depuis la base **que si on y accède explicitement**, pas automatiquement à chaque chargement d'une `Depense`. Avantage : évite de charger des données inutiles, meilleure performance.
- Le commentaire renforce une règle déjà appliquée dans les services (`DepenseService.create()`) : le propriétaire d'un mouvement financier n'est **jamais** pris depuis le corps de la requête, toujours depuis le token JWT via `CurrentUser`.

### 6.3 `Role`
```java
public enum Role {
    CLIENT,
    ADMIN
}
```
Un `enum` plutôt qu'une simple chaîne de caractères : garantit à la compilation qu'aucune valeur invalide (faute de frappe, casse différente) ne peut être assignée à un rôle.

---

## 7. Package `exception`

### 7.1 `DuplicateResourceException` / `ResourceNotFoundException`
Deux exceptions métier simples (`extends RuntimeException`), utilisées pour signaler des cas métier précis (email déjà pris, ressource introuvable), interceptées ensuite par `GlobalExceptionHandler` pour être traduites en réponses HTTP appropriées (`409`, `404`).

### 7.2 `GlobalExceptionHandler`
```java
@RestControllerAdvice
```
Centralise la gestion des erreurs pour **tous** les contrôleurs, évitant de répéter des blocs `try/catch` partout.

Points clés, chacun avec sa raison d'être :

| Exception | Statut | Pourquoi ce traitement précis |
|---|---|---|
| `ResourceNotFoundException` | 404 | Ressource inexistante ou n'appartenant pas à l'utilisateur (voir IDOR plus bas) |
| `DuplicateResourceException` | 409 | Email déjà utilisé |
| `BadCredentialsException` | 401, message **générique** ("Identifiants invalides") | Ne jamais préciser si c'est l'email ou le mot de passe qui est faux, pour empêcher l'énumération de comptes (un attaquant ne doit pas pouvoir tester des emails un par un et déduire lesquels existent selon la réponse) |
| `AuthenticationException` (catch-all) | 401, message générique | Sans ce handler, des cas comme `DisabledException` (compte `actif=false`) tombaient dans le handler générique → `500` au lieu d'un `401` propre, et révélaient indirectement qu'un compte existe mais est désactivé |
| `MethodArgumentNotValidException` | 400 | Erreurs de validation `@Valid` (ex: email malformé), renvoyées champ par champ pour aider un utilisateur légitime à corriger sa saisie |
| `HttpMessageNotReadableException` | 400 | JSON malformé/illisible → `400`, pas un `500` qui suggérerait un bug serveur |
| `Exception` (catch-all générique) | 500, message générique ("Une erreur est survenue") | Filet de sécurité final : ne **jamais** renvoyer la stack trace ou le message brut au client (cohérent avec `include-stacktrace: never` du YAML), mais **logguer** côté serveur pour pouvoir déboguer un vrai bug |

**Principe général qui traverse toute cette classe :** ne jamais donner à un client plus d'information que nécessaire sur *pourquoi* une opération a échoué, tout en gardant assez de détail côté serveur (logs) pour pouvoir diagnostiquer les vrais problèmes.

---

## 8. Package `repository`

Interfaces Spring Data JPA — pas d'implémentation à écrire, Spring génère le code à partir du nom des méthodes ou de l'annotation `@Query`.

**`DepenseRepository` / `EntreeRepository`** :
```java
// Toujours filtrer par userId : un client ne doit jamais voir les
// dépenses d'un autre utilisateur.
List<Depense> findByUserIdAndDateBetween(Long userId, LocalDate from, LocalDate to);
```
Le filtrage par `userId` est **intégré à la requête elle-même**, pas ajouté après coup en filtrant une liste en mémoire — c'est plus efficace (la base ne renvoie que les lignes pertinentes) et plus sûr (impossible d'oublier le filtre par accident dans un endroit du code).

`@Query` avec `COALESCE(SUM(...), 0)` : `SUM` sur un ensemble vide renverrait `NULL` en SQL, ce qui casserait un calcul arithmétique ensuite (`totalEntrees.subtract(totalDepenses)` lèverait une `NullPointerException`). `COALESCE(..., 0)` garantit toujours une valeur numérique, même si l'utilisateur n'a aucune dépense sur la période.

**`UserRepository`** : `findByEmail` et `existsByEmail` — les deux méthodes de base utilisées respectivement pour l'authentification et la vérification de doublon à l'inscription.

---

## 9. Package `security` — le cœur de la sécurité applicative

### 9.1 `CurrentUser` — pilier anti-IDOR

```java
/**
 * Point d'accès unique à l'identité de l'utilisateur authentifié.
 * Les services doivent TOUJOURS passer par ici pour déterminer le userId
 * concerné par une opération - jamais accepter un userId venant du body
 * ou d'un paramètre de requête envoyé par le client (protection anti-IDOR).
 */
@Component
public class CurrentUser {
    public CustomUserDetails get() {
        return (CustomUserDetails) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();
    }
    public Long id() { return get().getId(); }
}
```
**Pourquoi cette classe existe :** centraliser en **un seul endroit** la façon de récupérer "qui est connecté". Sans elle, chaque service devrait accéder directement à `SecurityContextHolder`, ce qui serait dupliqué partout et plus facile à contourner par erreur (par exemple, un développeur pressé qui accepte un `userId` du body "juste pour cette fois").

**Comment ça marche concrètement :** Spring Security stocke, pour chaque requête authentifiée, l'utilisateur courant dans le `SecurityContextHolder` (rempli par `JwtAuthFilter`, voir plus bas). `CurrentUser.id()` va chercher cette information — elle **ne peut donc venir que du token JWT vérifié**, jamais d'une donnée envoyée librement par le client.

### 9.2 `CustomUserDetails`
Implémentation de l'interface `UserDetails` de Spring Security, qui adapte l'entité `User` du domaine au format que Spring Security comprend.
```java
this.authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
```
Pourquoi le préfixe `"ROLE_"` : c'est une **convention obligatoire** de Spring Security — les méthodes comme `hasRole("ADMIN")` cherchent en réalité une autorité nommée `"ROLE_ADMIN"`. Sans ce préfixe, les vérifications de rôle échoueraient silencieusement.
```java
public boolean isAccountNonLocked() { return actif; }
public boolean isEnabled() { return actif; }
```
Relie le champ métier `actif` de `User` directement au mécanisme standard de Spring Security : un compte désactivé (`actif = false`) est automatiquement traité comme verrouillé/désactivé, sans code supplémentaire à écrire — Spring Security refuse alors l'authentification tout seul.

### 9.3 `JwtUtils`
Gère la création et vérification des tokens JWT (bibliothèque `jjwt`).

```java
@Value("${jwt.secret}")
private String jwtSecret;
```
Clé chargée depuis la configuration (donc depuis une variable d'environnement en prod), **jamais codée en dur** dans le code source — sinon elle finirait dans l'historique Git, visible par quiconque a accès au dépôt, même après suppression ultérieure.

**Distinction access token / refresh token — pourquoi c'est important :**
```java
private static final String TYPE_ACCESS = "access";
private static final String TYPE_REFRESH = "refresh";
...
.claim(CLAIM_TYPE, type)
```
Chaque JWT porte un claim `"type"` indiquant sa nature. Ça permet à `isAccessToken()` et `isRefreshToken()` de vérifier que **le bon type de token est utilisé au bon endroit** :
- Un `refresh token`, même valide et non expiré, est **rejeté** s'il est présenté comme moyen d'authentification directe (`JwtAuthFilter` n'accepte que `isAccessToken()`).
- Un `access token` est **rejeté** s'il est présenté à `/auth/refresh-token` (`AuthService.refresh()` n'accepte que `isRefreshToken()`).

**Pourquoi cette séparation est un avantage de sécurité :** sans elle, un access token (courte durée de vie, 15 min) pourrait être utilisé pour régénérer indéfiniment de nouveaux tokens comme un refresh token (durée 7 jours) — ce qui annulerait l'intérêt d'avoir des tokens de courte durée. Avec la séparation, chaque type de token n'a qu'un seul usage possible, ce qui limite les dégâts si l'un des deux est volé.

### 9.4 `JwtAuthFilter`
```java
if (jwtUtils.isAccessToken(token) && SecurityContextHolder.getContext().getAuthentication() == null) {
    String email = jwtUtils.extractEmail(token);
    UserDetails userDetails = userDetailsService.loadUserByUsername(email);
    var authToken = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
    ...
    SecurityContextHolder.getContext().setAuthentication(authToken);
}
```
**Rôle :** filtre exécuté sur chaque requête, qui lit le header `Authorization: Bearer <token>`, vérifie que c'est bien un access token valide, puis **peuple le contexte de sécurité** avec l'utilisateur correspondant. C'est ce mécanisme qui rend `CurrentUser` fonctionnel plus tard dans la requête.

**Condition `getAuthentication() == null` :** évite de retraiter une authentification déjà établie plus tôt dans la chaîne de filtres (défensif, mais peu susceptible d'arriver ici vu l'ordre des filtres).

**Si pas de header ou header malformé :** le filtre laisse simplement passer la requête sans authentifier (`filterChain.doFilter` direct) — ce n'est pas ce filtre qui décide si la route nécessite une authentification, c'est `SecurityConfig` (`anyRequest().authenticated()`) qui bloquera ensuite si besoin.

### 9.5 Protection anti brute-force : `AuthRateLimitFilter` + `RateLimiter`

C'est le mécanisme demandé explicitement — comment le brute-force est résolu ici.

**`RateLimiter` — l'algorithme :**
```java
private final ConcurrentHashMap<String, Deque<Instant>> hits = new ConcurrentHashMap<>();

public boolean tryConsume(String key, int maxRequests, Duration window) {
    Instant now = Instant.now();
    Deque<Instant> timestamps = hits.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());
    synchronized (timestamps) {
        Instant cutoff = now.minus(window);
        while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(cutoff)) {
            timestamps.pollFirst();  // purge les tentatives trop anciennes
        }
        if (timestamps.size() >= maxRequests) {
            return false;  // limite atteinte → refusé
        }
        timestamps.addLast(now);  // tentative acceptée, enregistrée
        return true;
    }
}
```
C'est un algorithme de **fenêtre glissante ("sliding window")** :
1. Pour chaque `key`, on garde la liste des horodatages des tentatives récentes.
2. À chaque nouvelle tentative, on **purge** celles plus vieilles que la fenêtre (`window`).
3. S'il reste déjà `maxRequests` tentatives dans la fenêtre → refus.
4. Sinon, la tentative est acceptée et horodatée.

**Pourquoi `ConcurrentHashMap` + `ConcurrentLinkedDeque` + `synchronized` :** l'application traite potentiellement plusieurs requêtes en parallèle (threads concurrents). `ConcurrentHashMap` gère l'accès concurrent au niveau des clés ; le bloc `synchronized (timestamps)` protège la lecture/purge/écriture de la deque d'une clé donnée contre les *race conditions* (deux requêtes simultanées de la même IP qui liraient/écriraient en même temps sans cohérence).

**Limite assumée (documentée en commentaire) :** solution **en mémoire**, donc valable pour **une seule instance** du serveur. Si l'application est déployée sur plusieurs instances (scaling horizontal), chaque instance aurait son propre compteur indépendant — un attaquant pourrait contourner la limite en étant redirigé vers des instances différentes. Le commentaire recommande explicitement une solution centralisée (**Redis**) pour ce cas.

**`AuthRateLimitFilter` — application concrète :**
```java
private static final int MAX_REQUESTS = 5;
private static final Duration WINDOW = Duration.ofMinutes(1);
...
String path = request.getRequestURI();
boolean limited = "/auth/login".equals(path) || "/auth/register".equals(path);

if (limited) {
    String key = clientIp(request) + ":" + path;
    if (!rateLimiter.tryConsume(key, MAX_REQUESTS, WINDOW)) {
        response.setStatus(429); // Too Many Requests
        ...
        return;
    }
}
filterChain.doFilter(request, response);
```
**Conditions et raisons :**
- **Seules `/auth/login` et `/auth/register` sont limitées** : ce sont les deux routes où un brute-force a du sens (deviner un mot de passe, ou spammer des créations de comptes) — pas besoin de limiter le reste de l'API de la même façon.
- **`5 requêtes / minute`** : compromis entre bloquer un attaquant automatisé (qui testerait des centaines de mots de passe rapidement) et ne pas gêner un utilisateur légitime qui se trompe deux ou trois fois de mot de passe.
- **Clé = `IP + route`** :
  ```java
  String key = clientIp(request) + ":" + path;
  ```
  Pourquoi combiner les deux plutôt que l'IP seule ? Pour ne pas pénaliser un client qui fait beaucoup de requêtes légitimes sur `/auth/login` à cause du trafic d'un *autre* client (ou du même) sur `/auth/register` — chaque route a son propre compteur, même pour la même IP.
- **`clientIp()` regarde `X-Forwarded-For` en priorité :**
  ```java
  String forwarded = request.getHeader("X-Forwarded-For");
  if (forwarded != null && !forwarded.isBlank()) {
      return forwarded.split(",")[0].trim();
  }
  return request.getRemoteAddr();
  ```
  Condition : derrière un reverse proxy (le commentaire cite Render comme exemple d'hébergeur), `request.getRemoteAddr()` renverrait l'IP du proxy, **pas** celle du vrai client — ce qui rendrait le rate-limiting inutile (toutes les requêtes auraient la même IP apparente). `X-Forwarded-For` porte l'IP d'origine ajoutée par le proxy. On prend le premier élément (`split(",")[0]`) car ce header peut contenir une chaîne de plusieurs IP si plusieurs proxies sont traversés.
- **Placement dans la chaîne de filtres** (vu en 3.2) : *avant* `JwtAuthFilter`, pour couper court avant tout traitement supplémentaire.
- **Réponse `429 Too Many Requests`** avec un message générique : statut HTTP standard, sémantiquement correct, qui indique clairement au client qu'il doit ralentir.

**Résumé du "comment on a résolu le brute-force" :** combinaison d'un compteur en mémoire à fenêtre glissante, limité aux routes sensibles, avec une clé IP+route, placé le plus tôt possible dans la chaîne de traitement — solution simple et suffisante pour une instance unique, avec une voie d'évolution explicitement documentée vers Redis pour un déploiement multi-instance.

---

## 10. Package `service`

### 10.1 `AuthService`
Voir en détail les trois méthodes (déjà expliquées en profondeur dans les échanges précédents) : `register()` (vérifie l'unicité de l'email, hash le mot de passe, force `Role.CLIENT`), `login()` (délègue la vérification à `AuthenticationManager`, génère les deux tokens), `refresh()` (vérifie le type de token avant de régénérer une paire access+refresh).

Point à noter : `authenticationManager.authenticate(...)` est **la** vérification des identifiants — c'est ce composant Spring Security qui applique `BCryptPasswordEncoder` en interne pour comparer le mot de passe fourni au hash stocké, sans que le code de `AuthService` ait besoin de le faire manuellement.

### 10.2 `DepenseService` / `EntreeService` — pilier anti-IDOR côté service

Ces deux services sont quasiment symétriques (dépense/entrée) et illustrent le même principe de sécurité que `CurrentUser` :

```java
public Depense create(MouvementRequest request) {
    User user = userRepository.getReferenceById(currentUser.id());
    Depense depense = Depense.builder()
            .user(user) // toujours l'utilisateur authentifié, jamais un id du body
            ...
```
**Pourquoi `getReferenceById` plutôt que `findById` :** `getReferenceById` renvoie un **proxy JPA "lazy"** sans exécuter de requête SQL immédiate — utile ici car on n'a besoin que de l'ID de l'utilisateur pour établir la relation (`@ManyToOne`), pas de charger toutes ses données. Avantage : une requête SQL en moins.

**`getOwnedOrThrow()` — la protection anti-IDOR explicite :**
```java
public Depense getOwnedOrThrow(Long depenseId) {
    Depense depense = depenseRepository.findById(depenseId)
            .orElseThrow(() -> new ResourceNotFoundException("Dépense introuvable"));

    if (!depense.getUser().getId().equals(currentUser.id())) {
        throw new ResourceNotFoundException("Dépense introuvable");
        // Volontairement "introuvable" plutôt que "accès refusé" :
        // ne pas confirmer à un utilisateur que l'id existe chez un autre.
    }
    return depense;
}
```
**Qu'est-ce qu'un IDOR ?** *Insecure Direct Object Reference* — une faille où un utilisateur peut accéder aux données d'un autre simplement en modifiant un ID dans une URL ou une requête (ex : `GET /depenses/20` alors que la dépense 20 appartient à quelqu'un d'autre).

**Comment c'est résolu ici, étape par étape :**
1. On cherche la ressource par son ID (`findById`) — si elle n'existe pas du tout → `404`.
2. Si elle existe, on compare son propriétaire (`depense.getUser().getId()`) à l'utilisateur actuellement connecté (`currentUser.id()`).
3. Si ce n'est **pas** le même utilisateur → on lève **la même exception, avec le même message** ("introuvable") que si la ressource n'existait pas du tout.

**Pourquoi renvoyer "introuvable" plutôt que "accès refusé" (403) dans ce second cas :** c'est un choix de sécurité délibéré. Si le serveur répondait différemment selon "la ressource n'existe pas" vs "la ressource existe mais n'est pas à vous", un attaquant pourrait **énumérer les IDs valides** en observant quelle réponse revient (403 = "ça existe chez quelqu'un" ; 404 = "ça n'existe pas"). En renvoyant systématiquement 404, on ne révèle jamais l'existence d'une ressource appartenant à un autre utilisateur.

**Avantage de centraliser cette logique dans `getOwnedOrThrow()` :** cette méthode est conçue pour être réutilisée avant toute opération de lecture, modification ou suppression sur une ressource précise — garantissant que la vérification de propriété est appliquée de façon uniforme partout, plutôt que réécrite (et potentiellement oubliée) à chaque endroit du code qui en a besoin.

### 10.3 `DashboardService`
Agrège les totaux de dépenses et d'entrées sur une période pour l'utilisateur courant, puis calcule `solde = totalEntrees - totalDepenses`. Toute la logique d'agrégation (comptage, somme) est déléguée aux requêtes `@Query` des repositories plutôt que recalculée en Java — plus efficace (calcul fait par la base de données).

### 10.4 `UserDetailsServiceImpl`
```java
public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
    return userRepository.findByEmail(email)
            .map(CustomUserDetails::new)
            // Message volontairement générique : ne pas révéler si l'email existe.
            .orElseThrow(() -> new UsernameNotFoundException("Identifiants invalides"));
}
```
**Rôle :** pont entre le domaine métier (`User`) et Spring Security (`UserDetailsService` est l'interface standard que Spring Security appelle pour retrouver un utilisateur à authentifier, notamment via `DaoAuthenticationProvider` configuré dans `SecurityConfig`). Même logique de message générique que partout ailleurs dans l'authentification : ne jamais confirmer/infirmer l'existence d'un compte par le contenu du message d'erreur.

### 10.5 `UserService`
`getMe()` / `updateMe()` opèrent **toujours** sur `currentUser.id()`, jamais sur un ID fourni par le client — même logique de protection que pour les dépenses/entrées, appliquée ici au profil utilisateur lui-même (empêche un utilisateur de modifier le profil d'un autre en changeant un ID dans la requête). `listAllUsers()` n'est appelée que depuis `AdminController`, protégée par le rôle `ADMIN`.

---

## 11. Package `util`

### `PeriodeResolver`
```java
public static Plage resolve(String periode, LocalDate from, LocalDate to) {
    if (from != null && to != null) {
        return new Plage(from, to);
    }
    LocalDate today = LocalDate.now();
    if ("mois".equalsIgnoreCase(periode)) {
        YearMonth ym = YearMonth.from(today);
        return new Plage(ym.atDay(1), ym.atEndOfMonth());
    }
    return new Plage(today, today); // "jour" par défaut
}
```
**Pourquoi une classe utilitaire dédiée :** `DepenseController`, `EntreeController` et `DashboardController` ont tous besoin de résoudre une période (soit des bornes explicites `from`/`to`, soit un mot-clé `"jour"`/`"mois"`). Centraliser cette logique évite de la dupliquer trois fois et garantit un comportement identique partout.

**Priorité des règles :** bornes explicites d'abord (si le client les fournit, elles priment), sinon dérivation depuis le mot-clé `periode` par rapport à aujourd'hui, avec `"jour"` comme valeur par défaut si `periode` est absent ou non reconnu — un comportement toujours défini, jamais d'erreur pour une période non spécifiée.

`public record Plage(LocalDate from, LocalDate to) {}` : un `record` Java (immutable, générant automatiquement constructeur/`equals`/`toString`) plutôt qu'une classe classique — adapté ici car `Plage` n'est qu'un simple porteur de deux valeurs, sans comportement propre.

---

## 12. `SubgetBackendApplication`
Point d'entrée standard Spring Boot (`@SpringBootApplication` + `main()`), sans logique propre — active l'auto-configuration, le scan des composants et l'exécution des `CommandLineRunner` (dont `AdminSeeder`).

---

## 13. Synthèse — les grands principes de sécurité qui traversent tout le projet

| Principe | Où il est appliqué |
|---|---|
| Anti brute-force | `AuthRateLimitFilter` + `RateLimiter` sur `/auth/login` et `/auth/register` |
| Anti-IDOR | `CurrentUser` (jamais de userId venant du client) + `getOwnedOrThrow()` dans `DepenseService`/`EntreeService` |
| Non-énumération de comptes | Messages génériques dans `GlobalExceptionHandler`, `UserDetailsServiceImpl` |
| Mots de passe jamais en clair | `PasswordEncoder` (BCrypt, coût 12) partout où un mot de passe est stocké |
| Séparation access/refresh token | Claim `"type"` dans `JwtUtils`, vérifié dans `JwtAuthFilter` et `AuthService.refresh()` |
| Pas d'élévation de privilège à l'inscription | `RegisterRequest` sans champ `role` ; `Role.CLIENT` forcé côté serveur |
| Fail-closed en production | `application-prod.yml` sans valeurs par défaut pour les secrets |
| Pas de fuite d'informations internes | `include-stacktrace: never`, `include-message: never`, handler générique loggant côté serveur uniquement |
| Défense en profondeur | Double vérification du rôle ADMIN (`SecurityConfig` + `@PreAuthorize`) |
