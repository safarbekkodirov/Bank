package com.minibank.mapper;

import org.springframework.stereotype.Component;

import com.minibank.dto.user.UserResponse;
import com.minibank.entity.User;

/**
 * Entity -> DTO которуучу.
 *
 * 🧠 Эмне ушул өзүнчө файлда?
 *   Биздин эреже: Entity ТЕКСТЕ колдонулбайт. Бирок Entity менен
 *   DTO — эки ар башка дүйнө: Entity DB колонкаларына, DTO JSON таалуусуна
 *   жабышкан. "Көпүрөө" деген (Entity, UserResponse) тик чегит
 *   менен качы.
 *
 *   Азыр бир гана Entity болгондуктан ушул атайын файл керек эмес эле.
 *   Аны бирөөлүк 2-3 Entity кошулушунда бөлүүбүз — анын мааниси
 *   "кайтаруу чегити" жана ошол чегит КАЙДА экенин көрүү.
 *
 *   @param user — базадан чыккан объект
 * @return      — APIга жиберүүчү объект (пароль жок)
 */
@Component
public class UserMapper {

    /**
     * User -> UserResponse
     *
     * 🔴 Абайлагын: passwordHash КОПСУНЧУ ЖОК жана кошулбайт.
     * Мына бул жерде "секретти коюп унутуу" катасы көп учурда болот.
     */
    public UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getRole(),
                user.getCreatedAt()
        );
    }
}
