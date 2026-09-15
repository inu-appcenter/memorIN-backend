package com.memorin.support;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// 테스트용 TCP 중계 프록시.
//
// WebSocket 세션이 "어떻게 죽었는지"에 따라 서버가 알아채는 경로가 완전히 다르다. 그런데 일반적인
// WebSocket 클라이언트로는 정상 종료밖에 만들 수 없다 — close 프레임을 안 보내고 죽는 상황이나
// 패킷이 그냥 안 오는 상황은 클라이언트 API 위에서 재현이 안 된다.
//
// 그래서 클라이언트와 서버 사이에 소켓을 하나 끼워 넣고, 그 소켓을 직접 조작한다.
//
//   reset()  : SO_LINGER 0으로 닫아 RST를 보낸다        → 탭 강제 종료 / 프로세스 kill
//   freeze() : 소켓은 열어둔 채 바이트를 읽고 버린다      → 네트워크 단절 (FIN도 RST도 없다)
//   stall()  : 서버→클라 방향을 아예 읽지 않는다          → 수신이 느린 클라이언트
//
// freeze와 stall의 차이가 중요하다. freeze는 읽어서 버리므로 서버 입장에서는 잘 받아가는 것으로 보이고,
// 클라이언트가 조용해진 것만 남는다(→ 하트비트로 탐지). stall은 읽지 않아서 TCP 윈도가 닫히고
// 서버의 쓰기가 밀린다(→ 세션 버퍼·전송시간 상한으로 탐지). 탐지 경로가 서로 다르다.
public class TcpProxy implements AutoCloseable {

    private final ServerSocket listener;
    private final int targetPort;
    private final List<Socket> sockets = Collections.synchronizedList(new ArrayList<>());
    private final ExecutorService pool = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "tcp-proxy");
        thread.setDaemon(true);
        return thread;
    });

    private volatile boolean frozen = false;
    private volatile boolean stalled = false;

    public TcpProxy(int targetPort) throws IOException {
        this.targetPort = targetPort;
        this.listener = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        pool.submit(this::acceptLoop);
    }

    public int port() {
        return listener.getLocalPort();
    }

    // 이 시점 이후로 양방향 모두 바이트가 흐르지 않는다. 소켓은 열린 채로 남는다.
    public void freeze() {
        this.frozen = true;
    }

    // 서버가 보낸 바이트를 읽지 않는다. 커널 수신 버퍼가 차면 TCP 윈도가 닫히고
    // 서버의 write가 막힌다 — 실제 "수신이 느린 클라이언트"와 같은 상태다.
    public void stall() {
        this.stalled = true;
    }

    // 열려 있는 모든 연결에 RST를 보낸다.
    public void reset() {
        synchronized (sockets) {
            for (Socket socket : sockets) {
                try {
                    socket.setSoLinger(true, 0);   // close() 시 FIN 대신 RST
                    socket.close();
                } catch (IOException ignored) {
                    // 이미 닫힌 소켓
                }
            }
        }
    }

    private void acceptLoop() {
        while (!listener.isClosed()) {
            try {
                Socket downstream = listener.accept();                                   // 클라이언트 ↔ 프록시
                Socket upstream = new Socket(InetAddress.getLoopbackAddress(), targetPort); // 프록시 ↔ 서버
                sockets.add(downstream);
                sockets.add(upstream);
                pool.submit(() -> pump(downstream, upstream, false));
                pool.submit(() -> pump(upstream, downstream, true));
            } catch (IOException e) {
                return;   // close()로 리스너가 닫힌 정상 종료
            }
        }
    }

    private void pump(Socket from, Socket to, boolean serverToClient) {
        byte[] buffer = new byte[8192];
        try {
            InputStream in = from.getInputStream();
            OutputStream out = to.getOutputStream();
            int read;
            while (true) {
                if (stalled && serverToClient) {
                    Thread.sleep(50);   // 읽지 않는다. 여기서 read()를 부르면 백프레셔가 사라진다.
                    continue;
                }
                if ((read = in.read(buffer)) == -1) {
                    return;
                }
                if (frozen) {
                    continue;   // 읽고 버린다. 스트림을 닫으면 안 된다 — 닫는 순간 FIN이 나가버린다.
                }
                out.write(buffer, 0, read);
                out.flush();
            }
        } catch (IOException ignored) {
            // 상대가 끊었다. 중계 스레드만 끝낸다.
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        try {
            listener.close();
        } catch (IOException ignored) {
            // 무시
        }
        synchronized (sockets) {
            for (Socket socket : sockets) {
                try {
                    socket.close();
                } catch (IOException ignored) {
                    // 무시
                }
            }
        }
        pool.shutdownNow();
    }
}
