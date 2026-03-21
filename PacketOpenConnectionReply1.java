package me.crossrealmmc.raknet.packets;

import io.netty.buffer.ByteBuf;
import me.crossrealmmc.raknet.RakNetServer;

public class PacketOpenConnectionReply1 {

    private final int mtu;

    public PacketOpenConnectionReply1(int mtu) {
        this.mtu = mtu;
    }

    public ByteBuf encode() {
        ByteBuf buf = PacketUtils.newBuffer();
        buf.writeByte(0x06); // ID_OPEN_CONNECTION_REPLY_1
        PacketUtils.writeMagic(buf);
        buf.writeLong(RakNetServer.SERVER_GUID);
        buf.writeBoolean(false); // security
        buf.writeShort(mtu);
        return buf;
    }
}
