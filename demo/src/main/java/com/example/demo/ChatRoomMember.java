package com.example.demo;

import jakarta.persistence.*;

@Entity
@Table(
        uniqueConstraints = @UniqueConstraint(columnNames = {"chat_room_id", "userId"})
)
public class ChatRoomMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_room_id")
    private ChatRoom chatRoom;

    private String userId;

    protected ChatRoomMember() {}

    public ChatRoomMember(ChatRoom chatRoom, String userId) {
        this.chatRoom = chatRoom;
        this.userId = userId;
    }

    public Long getId() { return id; }
    public ChatRoom getChatRoom() { return chatRoom; }
    public String getUserId() { return userId; }
}
