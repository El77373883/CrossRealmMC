package me.crossrealmmc.raknet.packets;

import io.netty.buffer.ByteBuf;
import me.crossrealmmc.raknet.RakNetServer;

import java.net.InetSocketAddress;

public class PacketOpenConnectionReply2 {

    private final InetSocketAddress clientAddress;
    private final short mtu;
    private final long clientGuid;

    public PacketOpenConnectionReply2(InetSocketAddress clientAddress, short mtu, long clientGuid) {
        this.clientAddress = clientAddress;
        this.mtu = mtu;
        this.clientGuid = clientGuid;
    }

    public ByteBuf encode() {
        ByteBuf buf = PacketUtils.newBuffer();
        buf.writeByte(0x08);
        PacketUtils.writeMagic(buf);
        buf.writeLong(RakNetServer.SERVER_GUID);
        PacketUtils.writeAddress(buf, clientAddress);
        buf.writeShort(mtu);
        buf.writeBoolean(false);
        return buf;
    }
}
