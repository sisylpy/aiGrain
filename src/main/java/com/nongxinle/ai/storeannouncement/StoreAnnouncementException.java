package com.nongxinle.ai.storeannouncement;

import lombok.Getter;

@Getter
public class StoreAnnouncementException extends RuntimeException {

    private final String errorCode;

    public StoreAnnouncementException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
