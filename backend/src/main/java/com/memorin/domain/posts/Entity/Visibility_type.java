package com.memorin.domain.posts.Entity;

public enum Visibility_type {

    PUBLIC("전체 공개"),
    FRIENDS("친구에게만 공개"),
    PRIVATE("비공개");

    private String visibility_type;
    Visibility_type(String visibility_type) {
        this.visibility_type = visibility_type;
    }
}
