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
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.minibank.dto.common.ApiError;
import com.minibank.dto.common.ApiError.FieldViolation;
import com.minibank.exception.AccountAlreadyExistsException;
import com.minibank.exception.AccountNotFoundException;
import com.minibank.exception.InvalidCredentialsException;
import com.minibank.exception.UnauthorizedException;
import com.minibank.exception.UserNotFoundException;

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
     * 401 UNAUTHORIZED — пароль туура эмес же колдонуучу таанылбады.
     *
     * 🧠 Биз «Invalid email or password» деп БИР message беребиз.
     *     Эгер «бундай email жок» деп айтсак, чыгаруучу чоң таблицада
     *     кайсы эсептер BAR экенин аныктап алар эле (user enumeration).
     *     «Пароль туура эмес» десек — эсеп бар экенин ачып беребиз.
     *     Демек: эки учурда БИРДЕЙ жооп.
     */
    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiError> handleInvalidCredentials(
            InvalidCredentialsException ex, HttpServletRequest request) {

        log.warn("Failed login attempt on {}", request.getRequestURI());

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiError.of(
                        HttpStatus.UNAUTHORIZED.value(),
                        HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                        ex.getMessage(),
                        request.getRequestURI()));
    }

    /**
     * 401 UNAUTHORIZED — сүрөттүн ичинде эч ким жок.
     * (Бул GlobalExceptionHandler аркылуу эмес, SecurityConfig'теги
     *  RestAuthenticationEntryPoint аркылуу кайтарылат. Бул жердеги
     *  — SecurityUtils чалган учур үчүн, эгер Controller кодуна келип калса.)
     */
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiError> handleUnauthorized(
            UnauthorizedException ex, HttpServletRequest request) {

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiError.of(
                        HttpStatus.UNAUTHORIZED.value(),
                        HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                        ex.getMessage(),
                        request.getRequestURI()));
    }

    /**
     * 404 NOT FOUND — БАР БОЛГОН ЭНДПОИНТ ЖОК.
     *
     * 🧠 Бул катанын чыныгы себеби:
     *   Клиент `/api/accounts/7` деп сурап жатат. Мындай эндпоинт
     *   БИЗДЕ ЖОК (биз атайын `{id}` жазбайбыз — коопсуздук үчүн).
     *   Токен туура болгондуктан коопсуздук чыпкасы ӨТҮП, кийин
     *   Spring «мындай Controller методу жок» деп ыйлайт.
     *
     * 🛡️ Эмне үчүн бул 404, эмес 500?
     *   Бул КЛИЕНТтин катасы («сен ушул баракты таппадың»), биздин
     *   ЭМЕС. 500 деп берсек, биз «сервер бузилды» деп жалган айтабыз,
     *   ошондо өз ичин мониторингде күчтүү шуудан пайда болот.
     *   Демек: 404 = "сен тууруш кылмадың", 500 = "БИЗ бузулдук".
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResourceFound(
            NoResourceFoundException ex, HttpServletRequest request) {

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of(
                        HttpStatus.NOT_FOUND.value(),
                        HttpStatus.NOT_FOUND.getReasonPhrase(),
                        "No endpoint " + ex.getResourcePath() + " on this API",
                        request.getRequestURI()));
    }

    /**
     * 404 NOT FOUND — изделген нерсе жок.
     */
    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ApiError> handleUserNotFound(
            UserNotFoundException ex, HttpServletRequest request) {
        return notFound(ex.getMessage(), request);
    }

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ApiError> handleAccountNotFound(
            AccountNotFoundException ex, HttpServletRequest request) {
        return notFound(ex.getMessage(), request);
    }

    private ResponseEntity<ApiError> notFound(String message, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of(
                        HttpStatus.NOT_FOUND.value(),
                        HttpStatus.NOT_FOUND.getReasonPhrase(),
                        message,
                        request.getRequestURI()));
    }

    /**
     * 409 CONFLICT — эсеп мурунтан бар.
     */
    @ExceptionHandler(AccountAlreadyExistsException.class)
    public ResponseEntity<ApiError> handleAccountAlreadyExists(
            AccountAlreadyExistsException ex, HttpServletRequest request) {

        log.warn("Account creation rejected on {}", request.getRequestURI());

        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(
                        HttpStatus.CONFLICT.value(),
                        HttpStatus.CONFLICT.getReasonPhrase(),
                        ex.getMessage(),
                        request.getRequestURI()));
    }

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
     * 409 CONFLICT — колдонуучунун эсебине акча ЖЕТПЕЙТ.
     *
     * 🧠 БУЛ КАТА «СЕРВЕР БУЗУЛДУ» ЭМЕС, «сен өтө көп сураптың»
     *     ДЕГЕН. Ошондуктан 500 ЭМЕС, 409 (Conflict).
     *
     * 🧡 Эмне 400 ЭМЕС? 400 = «сен БУРУШ СУРОМУ КЫЛДЫҢ»
     *     (терс сан, 4төн көп каттуу). 409 = «СУРОМУҢ ТУУРА,
     *     бирок учунчуран тап тура» — эсепте жетишпейт.
     *     Бул экиге БАШКАЧА оңдөлүш: 400'дө сумманы, 409'да
     *     «башка сумма» жазып кайра суруу керек.
     */
    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ApiError> handleInsufficientFunds(
            InsufficientFundsException ex, HttpServletRequest request) {

        // Логго — ким, канча сурады. Клиентке — «Insufficient funds».
        log.warn("Withdrawal rejected on {}: {}", request.getRequestURI(), ex.getMessage());

        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(
                        HttpStatus.CONFLICT.value(),
                        HttpStatus.CONFLICT.getReasonPhrase(),
                        ex.getMessage(),
                        request.getRequestURI()));
    }

    /**
     * 400 BAD REQUEST — сумма мыйындык эмес (0, терс, 4төн көп каттуу).
     *
     * 🧡 БУЛ КАТАДА 409 ЭМЕС. Себеби: 409 = «маалымат КОНФЛИКТ»
     *     (сервердеги менен ойлонбай калмак). Бул жагым:
     *     «сен 4 каттуу кыл» деп АЧЫК айтабыз. Биз аны
     *     ТЕГЕРЕКТЕБЕЙБИЗ — чыныгы акчаны «тегеректеп» жазуу
     *     колдонуучунун эсебин ЧАЛЫП, эсептөө 1 тыйын чайралган.
     */
    @ExceptionHandler(InvalidAmountException.class)
    public ResponseEntity<ApiError> handleInvalidAmount(
            InvalidAmountException ex, HttpServletRequest request) {

        return badRequest(ex.getMessage(), request);
    }

    /** 400 BAD REQUEST — комментарий өлчөмдөн чыкты. */
    @ExceptionHandler(InvalidDescriptionException.class)
    public ResponseEntity<ApiError> handleInvalidDescription(
            InvalidDescriptionException ex, HttpServletRequest request) {

        return badRequest(ex.getMessage(), request);
    }

    /**
     * 409 CONFLICT — колдонуучу ӨЗ ЭСЕБИНЕ ӨЗҮН КОТУРМОКЧУ (А → А).
     *
     * 🧠 ЭМНЕ 409, ЭМЕС 400?
     *     400 = «сен БУРУШ СҮРОМУ КЫЛДЫҢ» (маалыматын туура эмес).
     *     409 = «сен БУРУШ СҮРОМУ КЫЛДЫҢ, бирок аны ИШКЕ КОЛДОНБАЙТ»:
     *           номур чыныгы, сумма чыныгы — бирок «А → А» деген
     *           амал БИЗДЕ ЖОК. Бул абалды оңдөп БОЛОТ (башка эсепке
     *           котору), демек кайта аракет кылууга бөтөт.
     *
     * 🛡️ КАЙСЫ АСПЕКТТЕ МААЛЫМАТ УГУУ БОЛБОЙТ? — БАЙКОТ:
     *     Биз «сен көрбөгөн эсепке өтүү аракет кылдың» деп
     *     айтпайбыз. Чыныгы эсеп ТАПЫЛБАГАНДА — 404 (жалпы хабар,
     *     чыныгы эсеп бар же жок экенин айтпайт). Болгондо гана
     *     «сен өзүңдүн эсебиң» деп айтабыз — бул ЧЫНАГЫ колдонуучунун
     *     ӨЗ маалыматы, ошондуктан кооптук эмес.
     */
    @ExceptionHandler(SelfTransferException.class)
    public ResponseEntity<ApiError> handleSelfTransfer(
            SelfTransferException ex, HttpServletRequest request) {

        log.warn("Self-transfer rejected on {}: {}", request.getRequestURI(), ex.getMessage());

        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(
                        HttpStatus.CONFLICT.value(),
                        HttpStatus.CONFLICT.getReasonPhrase(),
                        ex.getMessage(),
                        request.getRequestURI()));
    }

    private ResponseEntity<ApiError> badRequest(String message, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiError.of(
                        HttpStatus.BAD_REQUEST.value(),
                        HttpStatus.BAD_REQUEST.getReasonPhrase(),
                        message,
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
