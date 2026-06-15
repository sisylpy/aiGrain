package com.nongxinle.ai.workrecord;

import lombok.Getter;

@Getter
public class WorkRecordMutationException extends IllegalArgumentException {

    private final String errorCode;

    public WorkRecordMutationException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
