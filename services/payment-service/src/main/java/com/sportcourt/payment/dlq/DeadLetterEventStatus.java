package com.sportcourt.payment.dlq;

public enum DeadLetterEventStatus {
    RECEIVED,
    REPLAYED,
    FAILED
}
