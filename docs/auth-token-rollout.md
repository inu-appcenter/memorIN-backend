# Authentication token rollout

## Frontend coordination

Deploy this backend change together with the frontend release. Tokens issued before this
release have no `typ` claim and are rejected, so clients must discard stored access and
refresh tokens and sign in again after deployment. The request and response JSON fields
remain `accessToken` and `refreshToken`; only the signed JWT payload changes.

The deployment migration removes legacy plaintext refresh-token rows, so every active
client must sign in again as soon as this version is deployed.

## Token contract

Access and refresh JWTs now carry a `typ` claim: `access` and `refresh` respectively.
Only an `access` token authenticates HTTP and STOMP API requests. `POST /auth/refresh`
accepts only a `refresh` token. Refresh-token database values are HMAC-SHA-256 hashes and are
never reusable from a database export.
