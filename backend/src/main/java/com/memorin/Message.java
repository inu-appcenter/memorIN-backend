package com.memorin;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String sender;
    private String content;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_room_id")
    private ChatRoom chatRoom;

    private LocalDateTime createdAt = LocalDateTime.now();

    protected Message() {}

    public Message(String sender, String content, ChatRoom chatRoom) {
        this.sender = sender;
        this.content = content;
        this.chatRoom = chatRoom;
    }

    public Long getId() { return id; }
    public String getSender() { return sender; }
    public String getContent() { return content; }
    public ChatRoom getChatRoom() { return chatRoom; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}