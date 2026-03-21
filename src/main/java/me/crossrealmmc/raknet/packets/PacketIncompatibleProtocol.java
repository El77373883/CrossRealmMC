package me.crossrealmmc.raknet.packets;

import io.netty.buffer.ByteBuf;
import me.crossrealmmc.raknet.RakNetServer;

public class PacketIncompatibleProtocol {

    public ByteBuf encode() {
        ByteBuf buf = PacketUtils.newBuffer();
        buf.writeByte(0x19); // ID_INCOMPATIBLE_PROTOCOL
        buf.writeByte(10);   // protocolo soportado
        PacketUtils.writeMagic(buf);
        buf.writeLong(RakNetServer.SERVER_GUID);
        return buf;
    }
}
