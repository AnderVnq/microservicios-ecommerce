package com.example.mspedidos.infrastructure.web.exception;

import com.example.mspedidos.domain.exception.ApprovalForbiddenException;
import com.example.mspedidos.domain.exception.AuthUnavailableException;
import com.example.mspedidos.domain.exception.BusinessRuleException;
import com.example.mspedidos.domain.exception.InventoryUnavailableException;
import com.example.mspedidos.domain.exception.ClientesUnavailableException;
import com.example.mspedidos.domain.exception.NotFoundException;
import com.example.mspedidos.domain.exception.PersonNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
                .body(new ApiError("VALIDATION_ERROR", "Datos invalidos", details));
    }

    /** Cuerpo ilegible o valor de enum desconocido en el JSON. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
                .body(new ApiError("VALIDATION_ERROR", "Datos invalidos", "Cuerpo de la solicitud ilegible o con valores no validos"));
    }

    /** Valor de query/path que no se puede convertir (p. ej. un enum desconocido). */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
                .body(new ApiError("VALIDATION_ERROR", "Datos invalidos",
                        "Valor no valido para el parametro " + ex.getName()));
    }

    @ExceptionHandler(PersonNotFoundException.class)
    public ResponseEntity<ApiError> handlePersonNotFound(PersonNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError.of("PERSON_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(NotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError.of("NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ApiError> handleBusinessRule(BusinessRuleException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of(codeOf(ex), ex.getMessage()));
    }

    @ExceptionHandler(ClientesUnavailableException.class)
    public ResponseEntity<ApiError> handleClientesUnavailable(ClientesUnavailableException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiError.of("CLIENTES_UNAVAILABLE", ex.getMessage()));
    }

    @ExceptionHandler(ApprovalForbiddenException.class)
    public ResponseEntity<ApiError> handleApprovalForbidden(ApprovalForbiddenException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError.of("APPROVAL_FORBIDDEN", ex.getMessage()));
    }

    @ExceptionHandler(InventoryUnavailableException.class)
    public ResponseEntity<ApiError> handleInventoryUnavailable(InventoryUnavailableException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiError.of("INVENTORY_UNAVAILABLE", ex.getMessage()));
    }

    @ExceptionHandler(AuthUnavailableException.class)
    public ResponseEntity<ApiError> handleAuthUnavailable(AuthUnavailableException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiError.of("AUTH_UNAVAILABLE", ex.getMessage()));
    }

    /** Invariantes de dominio (motivo/nota vacios, etc.). */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("IllegalArgumentException -> 422", ex);
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
                .body(new ApiError("VALIDATION_ERROR", "Datos invalidos", ex.getMessage()));
    }

    /** {@code QuotaExceededException} -> {@code QUOTA_EXCEEDED}. */
    private static String codeOf(BusinessRuleException ex) {
        String name = ex.getClass().getSimpleName().replaceFirst("Exception$", "");
        return name.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toUpperCase();
    }
}
