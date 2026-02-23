package com.wsd.appfreeze.adb;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.ConnectException;
import java.net.Socket;
import java.util.HashMap;

/**
 * ADB 连接管理
 * 实现与 adbd 守护进程的 TCP 通信，包括认证和流管理
 * 基于 cgutman/AdbLib 开源库（Apache 2.0 许可证）
 */
public class AdbConnection implements Closeable {

    private Socket socket;
    private int lastLocalId;
    private InputStream inputStream;
    OutputStream outputStream;
    private Thread connectionThread;
    private boolean connectAttempted;
    private boolean connected;
    private int maxData;
    private AdbCrypto crypto;
    private boolean sentSignature;
    private HashMap<Integer, AdbStream> openStreams;

    private AdbConnection() {
        openStreams = new HashMap<>();
        lastLocalId = 0;
        connectionThread = createConnectionThread();
    }

    /** 创建 ADB 连接 */
    public static AdbConnection create(Socket socket, AdbCrypto crypto) throws IOException {
        AdbConnection newConn = new AdbConnection();
        newConn.crypto = crypto;
        newConn.socket = socket;
        newConn.inputStream = socket.getInputStream();
        newConn.outputStream = socket.getOutputStream();
        socket.setTcpNoDelay(true);
        return newConn;
    }

    private Thread createConnectionThread() {
        final AdbConnection conn = this;
        return new Thread(() -> {
            while (!connectionThread.isInterrupted()) {
                try {
                    AdbProtocol.AdbMessage msg = AdbProtocol.AdbMessage.parseAdbMessage(inputStream);
                    if (!AdbProtocol.validateMessage(msg)) continue;

                    switch (msg.command) {
                        case AdbProtocol.CMD_OKAY:
                        case AdbProtocol.CMD_WRTE:
                        case AdbProtocol.CMD_CLSE:
                            if (!conn.connected) continue;
                            AdbStream waitingStream = openStreams.get(msg.arg1);
                            if (waitingStream == null) continue;
                            synchronized (waitingStream) {
                                if (msg.command == AdbProtocol.CMD_OKAY) {
                                    waitingStream.updateRemoteId(msg.arg0);
                                    waitingStream.readyForWrite();
                                    waitingStream.notify();
                                } else if (msg.command == AdbProtocol.CMD_WRTE) {
                                    waitingStream.addPayload(msg.payload);
                                    waitingStream.sendReady();
                                } else if (msg.command == AdbProtocol.CMD_CLSE) {
                                    conn.openStreams.remove(msg.arg1);
                                    waitingStream.notifyClose();
                                }
                            }
                            break;

                        case AdbProtocol.CMD_AUTH:
                            byte[] packet;
                            if (msg.arg0 == AdbProtocol.AUTH_TYPE_TOKEN) {
                                if (conn.sentSignature) {
                                    // 签名失败，发送公钥（首次连接需要用户确认）
                                    packet = AdbProtocol.generateAuth(AdbProtocol.AUTH_TYPE_RSA_PUBLIC,
                                            conn.crypto.getAdbPublicKeyPayload());
                                } else {
                                    // 用私钥签名令牌
                                    packet = AdbProtocol.generateAuth(AdbProtocol.AUTH_TYPE_SIGNATURE,
                                            conn.crypto.signAdbTokenPayload(msg.payload));
                                    conn.sentSignature = true;
                                }
                                conn.outputStream.write(packet);
                                conn.outputStream.flush();
                            }
                            break;

                        case AdbProtocol.CMD_CNXN:
                            synchronized (conn) {
                                conn.maxData = msg.arg1;
                                conn.connected = true;
                                conn.notifyAll();
                            }
                            break;
                    }
                } catch (Exception e) {
                    break;
                }
            }
            synchronized (conn) {
                cleanupStreams();
                conn.notifyAll();
                conn.connectAttempted = false;
            }
        });
    }

    /** 建立连接（阻塞直到完成） */
    public void connect() throws IOException, InterruptedException {
        if (connected) throw new IllegalStateException("Already connected");
        outputStream.write(AdbProtocol.generateConnect());
        outputStream.flush();
        connectAttempted = true;
        connectionThread.start();
        synchronized (this) {
            if (!connected) wait();
            if (!connected) throw new IOException("Connection failed");
        }
    }

    /** 打开一个 shell 流 */
    public AdbStream open(String destination) throws UnsupportedEncodingException, IOException, InterruptedException {
        int localId = ++lastLocalId;
        if (!connectAttempted) throw new IllegalStateException("connect() must be called first");
        synchronized (this) {
            if (!connected) wait();
            if (!connected) throw new IOException("Connection failed");
        }
        AdbStream stream = new AdbStream(this, localId);
        openStreams.put(localId, stream);
        outputStream.write(AdbProtocol.generateOpen(localId, destination));
        outputStream.flush();
        synchronized (stream) {
            stream.wait();
        }
        if (stream.isClosed()) throw new ConnectException("Stream open rejected by remote peer");
        return stream;
    }

    private void cleanupStreams() {
        for (AdbStream s : openStreams.values()) {
            try { s.close(); } catch (IOException e) { }
        }
        openStreams.clear();
    }

    @Override
    public void close() throws IOException {
        if (connectionThread == null) return;
        socket.close();
        connectionThread.interrupt();
        try { connectionThread.join(); } catch (InterruptedException e) { }
    }
}
