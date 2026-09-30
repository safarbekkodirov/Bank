package com.minibank.dto.common;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * PAGINATION JOOPU: mazmun + metadata.
 *
 * 🔴 EMNE SPRING Page OBJECTINI BIR API RESPONSE EDIP BERMEÝÄRIS?
 *     Task 6: "Spring Page<T> objektini tuz API response kataru
 *     chygarbaga arak et." Sebabi MÖHÜM:
 *
 *     1. 🔒 SERVERYN IÇKI KONTRACTY KLIENTE DE TAANYLYP KALADY.
 *        Page - Spring Data JPA-nyn KLASY. Biz ony bir ýylda
 *        täzelesek, klientiň KODY döwülýär. ÖZ DTO-MYZ -> kontrakt
 *        DIŇE bize bagly.
 *     2. 🧡 ARTIQCHA MAGLYMAT UÇUP GÝBÄR. PageImpl JSON-da
 *        `pageable`, `sort`, `numberOfElements`, `empty`... ýaly
 *        ownuk-owuk maglumatlar hem dasýar. Gabyň 20 setirine
 *        200 setir jogap. Klient okap bolmaz.
 *     3. 🔁 Töwerek salgylanma. Içki düzümde Page bolup, ol ýene
 *        Page saklasa - cyklyk ýagdaý çykýar.
 *
 * 🧡 EMNE GOÝÝARYS? AZ (6 meýdan) we ÄNDIKME:
 *     content, page, size, totalElements, totalPages, first, last.
 *     Ölçeg üçin totalPages hökmany: klient "meniň ýeňimji
 *     sahypam näme?" diýip sorasa biler.
 *
 * @param content       şu sahypadaky elementler (iň täze - birinji)
 * @param page          0-den başlaýan sahypa belgisi
 * @param size          bir sahypada näçe element
 * @param totalElements filterlenen ÄHLI ýazgynyň sany
 * @param totalPages    ähli sahypalaryň sany (boş bolanda 0)
 * @param first         bu birinji sahypamy?
 * @param last          бу соңку sahypamy?
 *
 * 📄 PHASE 10A: тек ДОКУМЕНТАЦИЯ (@Schema). Аттары жана иреттери
 *    ӨЗГӨРГӨН ЖОК — чыныгы JSON контракт ушул.
 */
@Schema(
        description = "Бет боюнча жооп: мазмун + мета маалымат. "
                + "Бүт маалымат бир жерде. Ирет: createdAt DESC, id DESC "
                + "(эң жаңа биринчи).",
        example = """
                {
                  "content": [],
                  "page": 0,
                  "size": 20,
                  "totalElements": 0,
                  "totalPages": 0,
                  "first": true,
                  "last": true
                }"""
)
public record PageResponse<T>(

        @Schema(description = "Бул беттеги жазуулар (эң жаңасы "
                + "биринчи). 🧡 КЕЙИНКИ БЕТ БОШ БОЛСО ДА, ЖООП "
                + "БОШ ЭМЕС: `content: []`, `totalElements: 0`, "
                + "`totalPages: 0`, `first: true`, `last: true`. "
                + "Бу КАТА ЭМЕС — ошондуктан 200 кайтарылат.",
                example = "[]")
        List<T> content,

        @Schema(description = "Беттин номуру, 0ден башталат. "
                + "Суралса: `?page=2`. Код: page < 0 болсо → 400. "
                + "Бош болсо → 0.",
                example = "0", minimum = "0", defaultValue = "0")
        int page,

        @Schema(description = "Бир беттеги жазуулардын саны. "
                + "Суралса: `?size=50`. Код: 1..100 аралыгында; "
                + "башкача болсо → 400. Бош болсо → 20.",
                example = "20", minimum = "1", maximum = "100", defaultValue = "20")
        int size,

        @Schema(description = "ФИЛЬТР КОШУЛГАНДАН КИЙИН "
                + "бардык жазуулардын саны (башка беттердеги "
                + "кийинкилер да камтылат).",
                example = "0")
        long totalElements,

        @Schema(description = "Бардык беттердин саны. "
                + "Жазуу жок болсо 0.",
                example = "0", minimum = "0")
        int totalPages,

        @Schema(description = "Бул биринчи бетпи?", example = "true")
        boolean first,

        @Schema(description = "Бул акыркы бетпи?", example = "true")
        boolean last
) {

    /**
     * 🧡 Spring Page -> biziň DTO, göçürme arkaly.
     *
     * 🧠 mapper: Entity -> DTO funksiýasy. MOHÜM: bary elementler
     *    ÝYGNAN ÝOK, alar bir ýylyň içinde öwrülýär (BUL "gold":
     *    10 000 ýazgy bir gezek bellikde öwrülse, olaryň ählisi
     *    bir wagtda RAM-da durýar).
     *
     * 🧡 "Boş" ýagdaý: content = [], totalElements = 0,
     *    totalPages = 0, first = true, last = true.
     *    Bu - KATA DÄL, şonuň üçin 200 döner (task 13).
     *
     * @param source  bazadan gelen ýazgylar (LIMIT/OFFSET ýylylan)
     * @param mapper  her bir ýazgy üçin Entity -> DTO
     */
    public static <S, T> PageResponse<T> of(Page<S> source, Function<S, T> mapper) {

        // 🧡 content() Spring-da List (iň täzeleri birinji) - biz ony
        //    gaýtadan sıralamaýarys: SORT ALREADY DB-de amala aşdyrylýar
        //    (ORDER BY created_at DESC, id DESC). Java-da ikinji gezek
        //    sıralamak = iki gezek iş, we ýalňyşlyk mümkinçiligi.
        List<T> content = source.getContent().stream()
                .map(mapper)
                .toList();

        return new PageResponse<>(
                content,
                source.getNumber(),
                source.getSize(),
                source.getTotalElements(),
                source.getTotalPages(),
                source.isFirst(),
                source.isLast());
    }
}
