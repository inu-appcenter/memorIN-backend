package com.memorin.domain.messages.content;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = TextContent.class, name = "TEXT"),
    @JsonSubTypes.Type(value = ImageContent.class, name = "IMAGE"),
    @JsonSubTypes.Type(value = PostShareContent.class, name = "POST_SHARE")
})
public sealed interface MessageContent permits TextContent, ImageContent, PostShareContent {
    String type();
}
