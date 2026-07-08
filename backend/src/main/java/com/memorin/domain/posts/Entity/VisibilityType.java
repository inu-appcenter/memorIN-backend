package com.memorin.domain.posts.Entity;

public enum VisibilityType {

    PUBLIC("전체 공개"),
    FRIENDS("친구에게만 공개"),
    PRIVATE("비공개");

    private String visibilityType;
    VisibilityType(String visibilityType) {}

}
