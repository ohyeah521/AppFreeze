package com.wsd.appfreeze.adb;

import java.io.Closeable;
import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * ADB 数据流抽象
 * 基于 cgutman/AdbLib 开源库（Apache 2.0 许可证）
 */
public class AdbStream implements Closeable {

    private AdbConnection adbConn;
    private int localId;
    private int remoteId;
    private AtomicBoolean writeReady;
    private Queue<byte[]> readQueue;
    private boolean isClosed;

    public AdbStream(AdbConnection adbConn, int localId) {
        this.adbConn = adbConn;
        this.localId = localId;
        this.readQueue = new ConcurrentLinkedQueue<>();
        this.writeReady = new AtomicBoolean(false);
        this.isClosed = false;
    }

    void addPayload(byte[] payload) {
        synchronized (readQueue) {
            readQueue.add(payload);
            readQueue.notifyAll();
        }
    }

    void sendReady() throws IOException {
        byte[] packet = AdbProtocol.generateReady(localId, remoteId);
        adbConn.outputStream.write(packet);
        adbConn.outputStream.flush();
    }

    void updateRemoteId(int remoteId) {
        this.remoteId = remoteId;
    }

    void readyForWrite() {
        writeReady.set(true);
    }

    void notifyClose() {
        isClosed = true;
        synchronized (this) { notifyAll(); }
        synchronized (readQueue) { readQueue.notifyAll(); }
    }

    /** 读取远端发送的数据 */
    public byte[] read() throws InterruptedException, IOException {
        byte[] data = null;
        synchronized (readQueue) {
            while (!isClosed && (data = readQueue.poll()) == null) {
                readQueue.wait();
            }
            if (isClosed) throw new IOException("Stream closed");
        }
        return data;
    }

    /** 发送字符串数据 */
    public void write(String payload) throws IOException, InterruptedException {
        write(payload.getBytes("UTF-8"), false);
        write(new byte[]{0}, true);
    }

    /** 发送字节数组数据 */
    public void write(byte[] payload) throws IOException, InterruptedException {
        write(payload, true);
    }

    public void write(byte[] payload, boolean flush) throws IOException, InterruptedException {
        synchronized (this) {
            while (!isClosed && !writeReady.compareAndSet(true, false))
                wait();
            if (isClosed) throw new IOException("Stream closed");
        }
        byte[] packet = AdbProtocol.generateWrite(localId, remoteId, payload);
        adbConn.outputStream.write(packet);
        if (flush) adbConn.outputStream.flush();
    }

    @Override
    public void close() throws IOException {
        synchronized (this) {
            if (isClosed) return;
            notifyClose();
        }
        byte[] packet = AdbProtocol.generateClose(localId, remoteId);
        adbConn.outputStream.write(packet);
        adbConn.outputStream.flush();
    }

    public boolean isClosed() {
        return isClosed;
    }
}
