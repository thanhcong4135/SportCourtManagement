package com.sportcourt.core.dlq;

public enum DeadLetterEventStatus {
    RECEIVED,
    REPLAYED,
    FAILED
}
