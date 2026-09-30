package com.minibank.exception;

/**
 * 400 BAD REQUEST — сумма мыйындык эмес.
 *
 * 🧡 Себеби 400, эмес 500: «сен 0 же -100 жаздың» — БУЛ СЕНИН
 *     катаң. Колдонмодун ички катасы эмес. Ченемсиз «анын ичинде
 *     эмне бар» деп айтууга да укугубуз жок, ошондуктан
 *     ар кимге өз катасы гана көрсөтүлөт.
 *
 *    Текшерүүлөр: null, <= 0, 4төн көп ондук бөлүк.
 */
public class InvalidAmountException extends RuntimeException {

    public InvalidAmountException(String message) {
        super(message);
    }
}
