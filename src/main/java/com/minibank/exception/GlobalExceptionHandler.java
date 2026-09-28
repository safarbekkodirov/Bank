package com.minibank.exception;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.minibank.dto.common.ApiError;
import com.minibank.dto.common.ApiError.FieldViolation;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Бүт колдонмодогу КАТАЛАРДЫ БИР ЖЕРГЕ ЖЫЮУЧУ.
 *
 * 🧠 @RestControllerAdvice деген эмне?
 *   "ControllerAdvice" = кеңешчи: Controller'ларга "катанды мындай
 *   көрсөт" деген. "Rest" = JSON түзүп берет (HTML эмес).
 *   Жана бир жери — Controller'лардын өзүнүн өзгөртпөй, БААРЫ
 *   бир форматта жооп берет.
 *
 *   Мына бул жердеги Stack trace-түү код:
 *     КАТАПА ТУРМА -> "Кечиресиз, ката болду" + логго толук маалымат.
 *     Stack trace HTTP аркылуу ЧЫГАРЫЛБАЙТ. Дүйнөгө чыгаруу үчүн жаратылган эмес.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 409 CONFLICT — колдонмону катталып жатат.
     */
    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    public ResponseEntity<ApiError> handleEmailAlreadyRegistered(
            EmailAlreadyRegisteredException ex, HttpServletRequest request) {

        log.warn("Registration rejected: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(
                        HttpStatus.CONFLICT.value(),
                        HttpStatus.CONFLICT.getReasonPhrase(),
                        ex.getMessage(),
                        request.getRequestURI()));
    }

    /**
     * 409 CONFLICT — маалымат базасы "жок" деди.
     *
     * 🧠 МИНИМУМ: эгер экөнү тез жазып жүрсө, биздин existsByEmail() текшерүүсүн
     * жазып, экөөнү тез жазып, биз унутуп калсак — маалымат базасынын
     * UNIQUE эрежеси бизди КАЙТАРЫП, биз да 409 берсек, колдонмону сынык
     * болбосо. Бул "экинчи коргоо катмары".
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrityViolation(
            DataIntegrityViolationException ex, HttpServletRequest request) {

        // Толук маалымат — ЛОГДА (кийинки кадамда биз өзүбүздөн "эмне" экенин
        // ачып, аны коозуна кошо алабыз. Бирок КЛИЕНТКЕ ЭМЕС.)
        log.error("Database constraint violated", ex);

        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(
                        HttpStatus.CONFLICT.value(),
                        HttpStatus.CONFLICT.getReasonPhrase(),
                        "Request conflicts with existing data",
                        request.getRequestURI()));
    }

    /**
     * 400 BAD REQUEST — @NotBlank / @Email / @Size текшерүүлөрү өтпөдү.
     *
     * Бул бизнес-логика эмес, СЫРТТАН КЕЛГЭН маалымат туура эмес.
     * Java текшерүүсү = БИРИНЧИ катмар.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        List<FieldViolation> violations = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> new FieldViolation(error.getField(), error.getDefaultMessage()))
                .collect(Collectors.toList());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError(
                        Instant.now(),
                        HttpStatus.BAD_REQUEST.value(),
                        HttpStatus.BAD_REQUEST.getReasonPhrase(),
                        "Validation failed for the request",
                        request.getRequestURI(),
                        violations));
    }

    /**
     * 400 BAD REQUEST — JSON өзү бузулган (мисалы, { ачылбаган же
     * кошумча тыныштын болбосоң) же таалуу таа жок.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(
            HttpMessageNotReadableException ex, HttpServletRequest request) {

        log.warn("Malformed request body: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiError.of(
                        HttpStatus.BAD_REQUEST.value(),
                        HttpStatus.BAD_REQUEST.getReasonPhrase(),
                        "Malformed JSON request body",
                        request.getRequestURI()));
    }

    /**
     * 500 INTERNAL SERVER ERROR — колдонмодун ички катасы.
     *
     * Бул биздин күнөмүздө күтпөгөн ката: клиенттин катасы эмес.
     * Ошондуктан колдонуучуга жөн гана "кайда, эмне жөнүндө" эмес,
     * "ички ката, кийин карайбыз" деп айтабыз. Толук маалымат — логдо.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(
            Exception ex, HttpServletRequest request) {

        log.error("Unexpected error at {}", request.getRequestURI(), ex);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of(
                        HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                        "An unexpected error occurred. Please try again later.",
                        request.getRequestURI()));
    }
}
