package com.memorin.global.config;

import com.memorin.global.exception.UserDetailsImpl;
import com.memorin.support.PostgresTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 모든 컨트롤러의 {@code @AuthenticationPrincipal} 파라미터가 올바른 타입인지 검사한다.
 *
 * <p><b>왜 이 테스트가 있나</b> — {@code AuthenticationPrincipalArgumentResolver}는 타입이 맞지 않으면
 * <b>예외를 던지지 않고 조용히 {@code null}을 주입한다</b>({@code errorOnInvalidType} 기본값 {@code false}).
 * 그래서 잘못 선언해도 컴파일도 되고 기동도 되고 CI도 green이다. 실제 호출에서만 깨진다.
 *
 * <p>실제로 그렇게 나갔다. 채팅방 7개 엔드포인트와 게시물 검색이 {@code @AuthenticationPrincipal UUID}로
 * 받고 있었고, 그대로 {@code main}에 두 번 릴리스됐다(#198 · #200 → #207).
 * 증상은 둘로 갈렸다.
 *
 * <ul>
 *   <li>채팅방 — 서비스 진입부에서 NPE → 500. 눈에 띈다</li>
 *   <li>검색 — {@code viewerId}가 null이라 SQL 조건이 UNKNOWN이 되어 <b>결과만 조용히 틀렸다.</b>
 *       에러가 나지 않아 더 늦게 발견된다</li>
 * </ul>
 *
 * <p><b>MockMvc 테스트를 엔드포인트마다 붙이는 것과의 차이</b> — 그쪽이 더 많은 것을 보지만
 * 엔드포인트가 늘 때마다 사람이 기억해서 붙여야 한다. 이 테스트는 <b>등록된 핸들러 전체</b>를
 * 훑으므로 새 엔드포인트가 추가되면 자동으로 포함된다. 둘은 대체재가 아니라 보완재다.
 * 커버리지 현황과 우선순위는 {@code docs/controller-test-coverage.md}에 있다.
 */
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AuthenticationPrincipalContractTest extends PostgresTestSupport {

    @Autowired
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    void 모든_AuthenticationPrincipal_파라미터는_UserDetailsImpl이어야_한다() {
        List<String> violations = new ArrayList<>();
        int checked = 0;

        for (Map.Entry<?, HandlerMethod> entry : handlerMapping.getHandlerMethods().entrySet()) {
            HandlerMethod handler = entry.getValue();

            // springdoc 등 라이브러리가 등록한 핸들러는 우리 계약의 대상이 아니다.
            if (!handler.getBeanType().getPackageName().startsWith("com.memorin")) {
                continue;
            }

            for (MethodParameter parameter : handler.getMethodParameters()) {
                if (!parameter.hasParameterAnnotation(AuthenticationPrincipal.class)) {
                    continue;
                }
                checked++;
                if (!UserDetailsImpl.class.equals(parameter.getParameterType())) {
                    violations.add("%s.%s(..) 의 %d번째 파라미터가 %s다".formatted(
                        handler.getBeanType().getSimpleName(),
                        handler.getMethod().getName(),
                        parameter.getParameterIndex() + 1,
                        parameter.getParameterType().getSimpleName()));
                }
            }
        }

        System.out.printf("%n>>> @AuthenticationPrincipal 파라미터 %d개 검사%n%n", checked);

        // 검사 대상이 0이면 이 테스트는 아무것도 지키지 못한 채 통과한다.
        // 핸들러 매핑이 비거나 패키지명이 바뀌면 그 상태가 되므로 함께 막는다.
        assertThat(checked)
            .as("@AuthenticationPrincipal을 쓰는 엔드포인트를 하나도 못 찾았다면 이 테스트가 무의미해진 것")
            .isGreaterThan(20);

        assertThat(violations)
            .as("""
                @AuthenticationPrincipal은 타입이 맞지 않으면 예외 없이 null이 주입된다.
                principal에 들어가는 것은 JwtTokenProvider.getAuthentication()이 넣는 UserDetailsImpl이다.
                UUID가 필요하면 userDetails.getUserId()를 쓴다.""")
            .isEmpty();
    }

    /**
     * 클래스 레벨 {@code @RequestMapping} 경로는 {@code /}로 시작해야 한다.
     *
     * <p>Spring이 알아서 붙여주므로 동작에는 문제가 없다. 다만 하나만 다른 규칙을 쓰면
     * 경로를 문자열로 다루는 곳(테스트·문서·로그 필터)에서 어긋난다.
     */
    @Test
    void 컨트롤러_기본_경로는_슬래시로_시작한다() {
        List<String> violations = handlerMapping.getHandlerMethods().values().stream()
            .map(HandlerMethod::getBeanType)
            .filter(type -> type.getPackageName().startsWith("com.memorin"))
            .distinct()
            .map(type -> type.getAnnotation(org.springframework.web.bind.annotation.RequestMapping.class))
            .filter(mapping -> mapping != null && mapping.value().length > 0)
            .map(mapping -> mapping.value()[0])
            .filter(path -> !path.isEmpty() && !path.startsWith("/"))
            .toList();

        assertThat(violations)
            .as("클래스 레벨 @RequestMapping 경로는 \"/api/...\" 형태여야 한다")
            .isEmpty();
    }
}
