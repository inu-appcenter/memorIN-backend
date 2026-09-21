package com.memorin.domain.users.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.memorin.global.common.ErrorCode;
import com.memorin.global.exception.BusinessException;

public record UpdateMyProfileRequest(JsonNode displayName, JsonNode profileImageKey, JsonNode bio) {
    private static final int DISPLAY_NAME_MAX_LENGTH = 100;
    private static final int PROFILE_IMAGE_KEY_MAX_LENGTH = 500;
    private static final int BIO_MAX_LENGTH = 500;

    public boolean hasDisplayName() { return displayName != null; }
    public boolean hasProfileImageKey() { return profileImageKey != null; }
    public boolean hasBio() { return bio != null; }
    public String displayNameValue() { return textValue(displayName); }
    public String profileImageKeyValue() { return textValue(profileImageKey); }
    public String bioValue() { return textValue(bio); }

    public void validate() {
        if (hasDisplayName()) {
            requireText(displayName, "displayName");
            String value = displayNameValue();
            if (value.isBlank() || value.length() > DISPLAY_NAME_MAX_LENGTH) invalid("displayName must be between 1 and 100 characters.");
        }
        if (hasProfileImageKey() && !profileImageKey.isNull()) {
            requireText(profileImageKey, "profileImageKey");
            String value = profileImageKeyValue();
            if (value.isBlank() || value.length() > PROFILE_IMAGE_KEY_MAX_LENGTH) invalid("profileImageKey must be between 1 and 500 characters.");
        }
        if (hasBio() && !bio.isNull()) {
            requireText(bio, "bio");
            if (bioValue().length() > BIO_MAX_LENGTH) invalid("bio must not exceed 500 characters.");
        }
    }

    private String textValue(JsonNode node) { return node == null || node.isNull() ? null : node.textValue(); }
    private void requireText(JsonNode node, String field) { if (!node.isTextual()) invalid(field + " must be a string."); }
    private void invalid(String message) { throw new BusinessException(ErrorCode.COMMON_002, message); }
}
