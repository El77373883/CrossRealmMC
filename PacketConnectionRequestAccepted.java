package me.crossrealmmc.raknet.packets;

import io.netty.buffer.ByteBuf;
import me.crossrealmmc.raknet.RakNetServer;

import java.net.InetSocketAddress;

public class PacketConnectionRequestAccepted {

    private final InetSocketAddress clientAddress;
    private final long clientTime;

    public PacketConnectionRequestAccepted(InetSocketAddress clientAddress, long clientTime) {
        this.clientAddress = clientAddress;
        this.clientTime = clientTime;
    }

    public ByteBuf encode() {
        ByteBuf buf = PacketUtils.newBuffer();
        buf.writeByte(0x10); // ID_CONNECTION_REQUEST_ACCEPTED
        PacketUtils.writeAddress(buf, clientAddress);
        buf.writeShort(0); // index

        // 20 IPs vacias de sistema
        InetSocketAddress empty = new InetSocketAddress("0.0.0.0", 0);
        for (int i = 0; i < 20; i++) {
            PacketUtils.writeAddress(buf, empty);
        }

        buf.writeLong(clientTime);
        buf.writeLong(System.currentTimeMillis());
        return buf;
    }
}
