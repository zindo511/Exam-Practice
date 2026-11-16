package vn.exampractice.identity.internal;

/**
 * Response của {@code POST /api/auth/login}. Client gửi lại token trong header
 * {@code Authorization: <tokenType> <accessToken>}.
 *
 * @param accessToken JWT
 * @param tokenType   luôn là {@code Bearer}
 * @param expiresIn   số giây token còn hiệu lực kể từ lúc nhận
 */
record TokenResponse(String accessToken, String tokenType, long expiresIn) {

    static TokenResponse bearer(String accessToken, long expiresIn) {
        return new TokenResponse(accessToken, "Bearer", expiresIn);
    }
}
