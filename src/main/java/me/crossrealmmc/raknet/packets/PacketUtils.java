package me.crossrealmmc.raknet.packets;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.net.InetSocketAddress;

public class PacketUtils {

    // Magic bytes de RakNet - siempre los mismos
    public static final byte[] RAKNET_MAGIC = {
        (byte)0x00, (byte)0xFF, (byte)0xFF, (byte)0x00,
        (byte)0xFE, (byte)0xFE, (byte)0xFE, (byte)0xFE,
        (byte)0xFD, (byte)0xFD, (byte)0xFD, (byte)0xFD,
        (byte)0x12, (byte)0x34, (byte)0x56, (byte)0x78
    };

    public static boolean readMagic(ByteBuf buf) {
        if (buf.readableBytes() < 16) return false;
        for (byte b : RAKNET_MAGIC) {
            if (buf.readByte() != b) return false;
        }
        return true;
    }

    public static void writeMagic(ByteBuf buf) {
        buf.writeBytes(RAKNET_MAGIC);
    }

    public static void writeAddress(ByteBuf buf, InetSocketAddress address) {
        byte[] ip = address.getAddress().getAddress();
        if (ip.length == 4) {
            buf.writeByte(4);
            for (byte b : ip) buf.writeByte(~b & 0xFF);
        } else {
            buf.writeByte(6);
            buf.writeShort(23); // AF_INET6
            buf.writeShort(address.getPort());
            buf.writeInt(0);
            buf.writeBytes(ip);
            buf.writeInt(0);
        }
        buf.writeShort(address.getPort());
    }

    public static InetSocketAddress readAddress(ByteBuf buf) {
        byte version = buf.readByte();
        if (version == 4) {
            byte[] ip = new byte[4];
            for (int i = 0; i < 4; i++) ip[i] = (byte)(~buf.readByte() & 0xFF);
            int port = buf.readUnsignedShort();
            return new InetSocketAddress(
                (ip[0] & 0xFF) + "." + (ip[1] & 0xFF) + "." + (ip[2] & 0xFF) + "." + (ip[3] & 0xFF),
                port
            );
        }
        // IPv6
        buf.skipBytes(18);
        byte[] ip = new byte[16];
        buf.readBytes(ip);
        buf.skipBytes(4);
        int port = buf.readUnsignedShort();
        return new InetSocketAddress("::1", port);
    }

    public static ByteBuf newBuffer() {
        return Unpooled.buffer();
    }
}
