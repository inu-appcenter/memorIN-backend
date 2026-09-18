-- Existing rows contain legacy plaintext refresh tokens. New JWTs require a typ claim,
-- so these rows cannot be reissued after this deployment; remove them immediately rather
-- than retaining bearer credentials until their old expiry.
DELETE FROM refresh_token;
