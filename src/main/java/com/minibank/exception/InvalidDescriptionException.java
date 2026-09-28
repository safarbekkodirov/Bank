package com.minibank.exception;

/**
 * 400 BAD REQUEST — комментарий (description) өлчөмдөн чыкты.
 *
 * 🧡 Биз ДААРЫ 3000 символду «кыскартып» салмайбыз.
 *     Себеби: «сиз жазганды кыскарттым» — эртең эсеп түзүкчөсү
 *     көзөмөлдөп, эмне үчүн кыскартылганын билгиси келбейт.
 *     Ачык ката («500 белгиден ашты») — адилеттүүлүгү жана
 *     оңдөлүшү оңой. Биз аны Controller'деги @Size менен да
 *     текшеребиз — бул Service'тин өзүнө кирүүчүлөр үчүн (тест,
 *     кийинки чакыруучулар) үчүн КАЙТА ТЕКШЕРҮҮ.
 */
public class InvalidDescriptionException extends RuntimeException {

    public InvalidDescriptionException(String message) {
        super(message);
    }
}