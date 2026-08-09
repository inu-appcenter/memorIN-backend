package com.memorin.domain.emoji.service;

import com.memorin.domain.emoji.dto.response.EmojiSummary;
import com.memorin.domain.emoji.dto.response.EmojiToggleResponse;
import com.memorin.domain.emoji.entity.CommentEmoji;
import com.memorin.domain.emoji.entity.EmojiType;
import com.memorin.domain.emoji.repository.CommentEmojiRepository;
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
public class CommentEmojiService {

    private final CommentEmojiRepository commentEmojiRepository;
    private final PostCommentRepository postCommentRepository;
    private final UserRepository userRepository;

    @Transactional
    public EmojiToggleResponse toggle(UUID userId, UUID commentId, EmojiType type) {
        PostComments comment = postCommentRepository.findById(commentId)
            .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_001, "댓글이 존재하지 않습니다.: " + commentId));

        if (comment.isDeleted()) { // tombstone 방어
            throw new BusinessException(ErrorCode.COMMENT_EMOJI_001, "삭제된 댓글에는 이모지를 달 수 없습니다.: " + commentId);
        }

        Optional<CommentEmoji> existing = commentEmojiRepository
            .findByUserIdAndPostCommentsIdAndEmojiType(userId, commentId, type);

        if (existing.isPresent()) {
            commentEmojiRepository.delete(existing.get()); // 토글 -> 제거
            return new EmojiToggleResponse(type, false);
        }

        User user = userRepository.getReferenceById(userId); // 프록시 참조로 충분

        try {
            commentEmojiRepository.save(CommentEmoji.of(user, comment, type));
            return new EmojiToggleResponse(type, true);
        } catch (DataIntegrityViolationException e) {
            // 동시 더블클릭 -> 이미 존재. 이미 "추가된" 상태이므로 멱등 처리
            return new EmojiToggleResponse(type, true);
        }
    }

    @Transactional
    public void remove(UUID userId, UUID commentId, EmojiType type) {
        commentEmojiRepository
            .deleteByUserIdAndPostCommentsIdAndEmojiType(userId, commentId, type);
        // 없어도 예외 안 던짐으로 DELETE 멱등성 보장
    }

    @Transactional(readOnly = true)
    public List<EmojiSummary> getEmojis(UUID commentId, UUID meId) {
        return commentEmojiRepository.countByCommentIds(List.of(commentId), meId)
            .stream().map(EmojiSummary::from).toList();
    }
}
