package com.memorin.domain.emoji.service;

import com.memorin.domain.emoji.dto.response.EmojiSummary;
import com.memorin.domain.emoji.dto.response.EmojiToggleResponse;
import com.memorin.domain.emoji.entity.CommentEmoji;
import com.memorin.domain.emoji.entity.EmojiType;
import com.memorin.domain.emoji.entity.MessageEmoji;
import com.memorin.domain.emoji.repository.CommentEmojiRepository;
import com.memorin.domain.emoji.repository.MessageEmojiRepository;
import com.memorin.domain.messages.entity.Messages;
import com.memorin.domain.messages.repository.MessageRepository;
import com.memorin.domain.post_comments.entity.PostComments;
import com.memorin.domain.post_comments.repository.PostCommentRepository;
import com.memorin.domain.users.entity.User;
import com.memorin.domain.users.repository.UserRepository;
import com.memorin.global.common.ErrorCode;
import com.memorin.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MessageEmojiService {

    private final MessageEmojiRepository messageEmojiRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;

    @Transactional
    public EmojiToggleResponse toggle(UUID userId, UUID messageId, EmojiType type) {
        Messages messages = messageRepository.findById(messageId)
            .orElseThrow(() -> new BusinessException(ErrorCode.MESSAGE_001, "메세지가 존재하지 않습니다.: " + messageId));

        if (messages.isDeleted()) { // tombstone 방어
            throw new BusinessException(ErrorCode.MESSAGE_EMOJI_001, "삭제된 메세지에는 이모지를 달 수 없습니다.: " + messageId);
        }

        Optional<MessageEmoji> existing = messageEmojiRepository
            .findByUserIdAndMessageIdAndEmojiType(userId, messageId, type);

        if (existing.isPresent()) {
            messageEmojiRepository.delete(existing.get()); // 토글 -> 제거
            return new EmojiToggleResponse(type, false);
        }

        User user = userRepository.getReferenceById(userId); // 프록시 참조로 충분

        try {
            messageEmojiRepository.saveAndFlush(MessageEmoji.of(user, messages, type));
            return new EmojiToggleResponse(type, true);
        } catch (DataIntegrityViolationException e) {
            // 동시 더블클릭 -> 이미 존재. 이미 "추가된" 상태이므로 멱등 처리
            return new EmojiToggleResponse(type, true);
        }
    }

    @Transactional
    public void remove(UUID userId, UUID messageId, EmojiType type) {
        messageEmojiRepository
            .deleteByUserIdAndMessageIdAndEmojiType(userId, messageId, type);
        // 없어도 예외 안 던짐으로 DELETE 멱등성 보장
    }

    @Transactional(readOnly = true)
    public List<EmojiSummary> getEmojis(UUID messageId, UUID meId) {
        return messageEmojiRepository.countByMessageIds(List.of(messageId), meId)
            .stream().map(EmojiSummary::from).toList();
    }
}
