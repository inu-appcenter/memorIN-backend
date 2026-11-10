package com.memorin.global.config;

import com.memorin.domain.auth.jwt.JwtTokenProvider;
import com.memorin.domain.chat_rooms.dto.request.CreateGroupRoomRequest;
import com.memorin.domain.chat_rooms.service.ChatRoomService;
import com.memorin.domain.users.entity.User;
import com.memorin.domain.users.repository.UserRepository;
import com.memorin.support.PostgresTestSupport;
import com.memorin.support.RawStompClient;
import com.memorin.support.RawStompClient.Frame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.support.MessageBuilder;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * #304의 우회 4가지를 실제 브로커로 확인한다. 인터셉터 단위 테스트만으로는 부족했다 —
 * 단위 테스트는 통과하는데 운영에서는 뚫려 있었다(배달 필터가 실제 브로커 메시지 모양을 몰랐다).
 *
 * <ul>
 *   <li>토큰 없이 {@code STOMP} 프레임으로 연결</li>
 *   <li>패턴 목적지({@code /topic/**}) 구독으로 남의 방 수신</li>
 *   <li>{@code /topic/rooms/{id}}로 직접 SEND해 senderId 위조</li>
 *   <li>강퇴 후에도 이미 맺은 구독으로 수신</li>
 * </ul>
 * 정상 흐름(로그인한 멤버의 연결·구독·송수신)이 그대로인지도 함께 본다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class StompSecurityIntegrationTest extends PostgresTestSupport {

    private static final Duration WAIT = Duration.ofSeconds(5);
    // "오지 않는다"를 확인하는 시간. 같은 메시지를 다른 구독자가 먼저 받은 뒤에 재므로 길 필요가 없다.
    private static final Duration QUIET = Duration.ofMillis(700);

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private ChatRoomService chatRoomService;

    @Autowired
    private SimpleBrokerMessageHandler simpleBroker;

    private final List<RawStompClient> clients = new ArrayList<>();

    private UUID ownerId;
    private UUID memberId;
    private UUID outsiderId;
    private UUID roomId;

    @BeforeEach
    void setUp() {
        ownerId = newUser("owner");
        memberId = newUser("member");
        outsiderId = newUser("outsider");
        roomId = chatRoomService.createGroupRoom(ownerId, new CreateGroupRoomRequest("보안 테스트 방", List.of(memberId)))
            .roomId();
    }

    @AfterEach
    void tearDown() {
        clients.forEach(RawStompClient::close);
        clients.clear();
    }

    private UUID newUser(String prefix) {
        String tag = prefix + UUID.randomUUID().toString().substring(0, 8);
        return userRepository.save(new User(tag + "@memorin.test", "hash", tag, tag, null)).getId();
    }

    private String token(UUID userId) {
        return jwtTokenProvider.createAccessToken(userId);
    }

    private RawStompClient open() throws Exception {
        RawStompClient client = RawStompClient.open(port);
        clients.add(client);
        return client;
    }

    private RawStompClient connected(UUID userId) throws Exception {
        RawStompClient client = open();
        Frame frame = client.connect("CONNECT", token(userId));
        assertThat(frame).isNotNull();
        assertThat(frame.command()).isEqualTo("CONNECTED");
        return client;
    }

    private String roomTopic() {
        return "/topic/rooms/" + roomId;
    }

    // 구독은 브로커 스레드에서 비동기로 등록된다. 등록되기 전에 메시지를 보내면 테스트가 흔들리므로 기다린다.
    private void awaitSubscribers(String destination, int expected) throws InterruptedException {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        accessor.setDestination(destination);
        var probe = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        long deadline = System.nanoTime() + WAIT.toNanos();
        while (System.nanoTime() < deadline) {
            if (simpleBroker.getSubscriptionRegistry().findSubscriptions(probe).size() >= expected) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError(destination + " 구독자가 " + expected + "명이 되지 않았다");
    }

    private void sendText(RawStompClient client, String text) throws Exception {
        client.sendJson("/app/chat.sendText", "{\"roomId\":\"" + roomId + "\",\"text\":\"" + text + "\"}");
    }

    // ---- 정상 흐름 ----

    @Test
    void 로그인한_멤버는_연결_구독_송수신이_그대로_된다() throws Exception {
        RawStompClient owner = connected(ownerId);
        RawStompClient member = connected(memberId);
        owner.subscribe("sub-0", roomTopic());
        member.subscribe("sub-0", roomTopic());
        awaitSubscribers(roomTopic(), 2);

        sendText(member, "hello");

        Frame toOwner = owner.nextOf("MESSAGE", WAIT);
        Frame toMember = member.nextOf("MESSAGE", WAIT);
        assertThat(toOwner).as("방장이 받아야 한다").isNotNull();
        assertThat(toMember).as("보낸 사람도 자기 구독으로 받아야 한다").isNotNull();
        assertThat(toOwner.body()).contains("hello").contains(memberId.toString());
        assertThat(toOwner.header("destination")).isEqualTo(roomTopic());
    }

    // ---- 우회 1: STOMP 프레임 ----

    @Test
    void 토큰_없는_STOMP_프레임으로는_연결되지_않는다() throws Exception {
        RawStompClient attacker = open();

        Frame frame = attacker.connect("STOMP", null);

        assertThat(frame).isNotNull();
        assertThat(frame.command()).isEqualTo("ERROR");
        assertThat(attacker.awaitClosed(WAIT)).as("연결이 끊겨야 한다").isTrue();
    }

    @Test
    void 연결_프레임_없이_보낸_구독은_거절된다() throws Exception {
        RawStompClient attacker = open();

        attacker.subscribe("sub-0", roomTopic());

        Frame frame = attacker.next(WAIT);
        assertThat(frame).isNotNull();
        assertThat(frame.command()).isEqualTo("ERROR");
    }

    // ---- 우회 2: 패턴 구독 ----

    @Test
    void 패턴_목적지를_구독해도_남의_방_대화를_받지_못한다() throws Exception {
        RawStompClient owner = connected(ownerId);
        owner.subscribe("sub-0", roomTopic());
        awaitSubscribers(roomTopic(), 1);

        RawStompClient outsider = connected(outsiderId);
        outsider.subscribe("sub-0", "/topic/**");

        Frame rejected = outsider.next(WAIT);
        assertThat(rejected).isNotNull();
        assertThat(rejected.command()).isEqualTo("ERROR");

        sendText(owner, "secret");
        assertThat(owner.nextOf("MESSAGE", WAIT)).isNotNull();
        assertThat(outsider.nextOf("MESSAGE", QUIET)).as("비멤버에게 대화가 가면 안 된다").isNull();
    }

    // ---- 우회 3: /topic 직접 SEND ----

    @Test
    void topic으로_직접_보내_senderId를_위조할_수_없다() throws Exception {
        RawStompClient member = connected(memberId);
        member.subscribe("sub-0", roomTopic());
        awaitSubscribers(roomTopic(), 1);

        // 방 멤버가 아닌 사람이 방장인 척 메시지를 만든다.
        RawStompClient attacker = connected(outsiderId);
        attacker.sendJson(roomTopic(), "{\"senderId\":\"" + ownerId + "\",\"content\":{\"text\":\"forged\"}}");

        Frame rejected = attacker.next(WAIT);
        assertThat(rejected).isNotNull();
        assertThat(rejected.command()).isEqualTo("ERROR");
        assertThat(member.nextOf("MESSAGE", QUIET)).as("위조 메시지가 구독자에게 가면 안 된다").isNull();
    }

    // ---- 우회 4: 강퇴 후 수신 ----

    @Test
    void 강퇴당한_멤버는_이미_맺은_구독으로도_받지_못한다() throws Exception {
        RawStompClient owner = connected(ownerId);
        RawStompClient member = connected(memberId);
        owner.subscribe("sub-0", roomTopic());
        member.subscribe("sub-0", roomTopic());
        awaitSubscribers(roomTopic(), 2);

        chatRoomService.kickMember(roomId, ownerId, memberId);

        sendText(owner, "after-kick");
        assertThat(owner.nextOf("MESSAGE", WAIT)).as("남은 멤버는 계속 받아야 한다").isNotNull();
        assertThat(member.nextOf("MESSAGE", QUIET)).as("강퇴당한 멤버에게 가면 안 된다").isNull();
    }
}
