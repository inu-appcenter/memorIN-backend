CREATE UNIQUE INDEX uq_notifications_like_actor_post
    ON notifications (user_id, actor_id, reference_id)
    WHERE type = 'LIKE';
