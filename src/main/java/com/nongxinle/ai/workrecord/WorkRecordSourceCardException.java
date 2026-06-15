package com.nongxinle.ai.workrecord;

import lombok.Getter;

@Getter
public class WorkRecordSourceCardException extends IllegalArgumentException {

    private final String errorCode;

    public WorkRecordSourceCardException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
