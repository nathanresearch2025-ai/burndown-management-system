package com.burndown.exception;

public class ResourceNotFoundException extends RuntimeException {
    private final String messageKey;

    public ResourceNotFoundException(String messageKey) {
        super(messageKey);
        this.messageKey = messageKey;
    }

    public String getMessageKey() {
        return messageKey;
    }
}
