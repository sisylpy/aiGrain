package com.nongxinle.ai.workrecord;

import lombok.Getter;

@Getter
public class WorkRecordBusinessCardException extends IllegalArgumentException {

    private final String errorCode;

    public WorkRecordBusinessCardException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
