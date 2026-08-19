CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    actor_id UUID,
    type VARCHAR(30) NOT NULL,
    title VARCHAR(100) NOT NULL,
    message VARCHAR(500) NOT NULL,
    reference_id UUID,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_notifications_user
    FOREIGN KEY (user_id) REFERENCES users(id)
    ON DELETE CASCADE,

    CONSTRAINT fk_notifications_actor
    FOREIGN KEY (actor_id) REFERENCES users(id)
    ON DELETE SET NULL
);

CREATE INDEX idx_notifications_user_id ON notifications(user_id, id DESC);
