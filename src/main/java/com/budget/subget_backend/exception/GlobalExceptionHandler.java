package com.budget.subget_backend.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // Ressource demandée introuvable (ex: getById sur un id qui n'existe pas) -> 404.
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Object> handleNotFound(ResourceNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    // Tentative de création d'une ressource qui existe déjà (ex: email déjà utilisé) -> 409.
    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<Object> handleDuplicate(DuplicateResourceException ex) {
        return build(HttpStatus.CONFLICT, ex.getMessage());
    }

    // Échec d'authentification classique (email/mot de passe incorrect) -> 401.
    // Message volontairement générique : ne jamais préciser si c'est l'email
    // ou le mot de passe qui est incorrect (évite l'énumération de comptes).
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Object> handleBadCredentials(BadCredentialsException ex) {
        return build(HttpStatus.UNAUTHORIZED, "Identifiants invalides");
    }

    // Catch-all pour les autres échecs d'authentification (ex: DisabledException
    // si actif=false, LockedException...). Sans ce handler, ces exceptions
    // tombaient dans handleGeneric() -> 500 au lieu d'un 401 propre, et surtout
    // ça revenait à révéler indirectement qu'un compte existe mais est désactivé.
    // Message générique ici aussi, pour la même raison que BadCredentialsException.
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Object> handleAuthentication(AuthenticationException ex) {
        return build(HttpStatus.UNAUTHORIZED, "Identifiants invalides");
    }

    // Utilisateur authentifié mais sans les droits nécessaires (ex: rôle insuffisant) -> 403.
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Object> handleAccessDenied(AccessDeniedException ex) {
        return build(HttpStatus.FORBIDDEN, "Accès refusé");
    }

    // Échec de validation des champs d'un DTO annoté @Valid (ex: @NotNull, @Email...) -> 400,
    // avec le détail champ par champ des erreurs (au lieu d'un simple message générique).
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Object> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(err ->
                errors.put(err.getField(), err.getDefaultMessage()));
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("errors", errors);
        return ResponseEntity.badRequest().body(body);
    }

    // JSON malformé / illisible dans le body -> 400, pas 500.
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Object> handleMalformedJson(HttpMessageNotReadableException ex) {
        return build(HttpStatus.BAD_REQUEST, "Requête invalide");
    }

    // Route inexistante (ex: /users/123 sans mapping correspondant) -> 404
    // propre. Sans ce handler, Spring MVC lève cette exception en interne et
    // elle tombait dans handleGeneric() -> 500 au lieu du 404 attendu.
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Object> handleNoResourceFound(NoResourceFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "Ressource introuvable");
    }

    // Filet de sécurité : toute exception non gérée renvoie un message
    // générique au client - jamais la stack trace ni le message brut - mais
    // on la logue côté serveur pour pouvoir débugger un vrai bug en prod.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleGeneric(Exception ex) {
        log.error("Erreur non gérée", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Une erreur est survenue");
    }

    // Construit une réponse d'erreur JSON uniforme (timestamp, status, message)
    // utilisée par la plupart des handlers ci-dessus.
    private ResponseEntity<Object> build(HttpStatus status, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", status.value());
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}
