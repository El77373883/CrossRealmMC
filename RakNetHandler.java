package me.crossrealmmc.raknet;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;
import me.crossrealmmc.CrossRealmMC;
import me.crossrealmmc.raknet.packets.*;

import java.net.InetSocketAddress;

public class RakNetHandler extends SimpleChannelInboundHandler<DatagramPacket> {

    private final CrossRealmMC plugin;

    // IDs de paquetes RakNet
    public static final byte ID_UNCONNECTED_PING            = 0x01;
    public static final byte ID_UNCONNECTED_PING_OPEN       = 0x02;
    public static final byte ID_UNCONNECTED_PONG            = 0x1C;
    public static final byte ID_OPEN_CONNECTION_REQUEST_1   = 0x05;
    public static final byte ID_OPEN_CONNECTION_REPLY_1     = 0x06;
    public static final byte ID_OPEN_CONNECTION_REQUEST_2   = 0x07;
    public static final byte ID_OPEN_CONNECTION_REPLY_2     = 0x08;
    public static final byte ID_CONNECTION_REQUEST          = 0x09;
    public static final byte ID_CONNECTION_REQUEST_ACCEPTED = 0x10;
    public static final byte ID_NEW_INCOMING_CONNECTION     = 0x13;
    public static final byte ID_DISCONNECTION_NOTIFICATION  = 0x15;
    public static final byte ID_INCOMPATIBLE_PROTOCOL       = 0x19;

    public RakNetHandler(CrossRealmMC plugin) {
        this.plugin = plugin;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket packet) {
        ByteBuf buf = packet.content();
        if (!buf.isReadable()) return;

        InetSocketAddress sender = packet.sender();
        byte packetId = buf.readByte();

        plugin.debugLog("Paquete recibido: 0x" + String.format("%02X", packetId)
                + " desde " + sender.getAddress().getHostAddress());

        switch (packetId) {
            case ID_UNCONNECTED_PING:
            case ID_UNCONNECTED_PING_OPEN:
                handleUnconnectedPing(ctx, buf, sender);
                break;

            case ID_OPEN_CONNECTION_REQUEST_1:
                handleOpenConnectionRequest1(ctx, buf, sender);
                break;

            case ID_OPEN_CONNECTION_REQUEST_2:
                handleOpenConnectionRequest2(ctx, buf, sender);
                break;

            case ID_CONNECTION_REQUEST:
                handleConnectionRequest(ctx, buf, sender);
                break;

            case ID_DISCONNECTION_NOTIFICATION:
                plugin.debugLog("Desconexion de: " + sender.getAddress().getHostAddress());
                break;

            default:
                plugin.debugLog("Paquete desconocido: 0x" + String.format("%02X", packetId));
                break;
        }
    }

    private void handleUnconnectedPing(ChannelHandlerContext ctx, ByteBuf buf, InetSocketAddress sender) {
        PacketUnconnectedPong pong = new PacketUnconnectedPong(plugin);
        ctx.writeAndFlush(new DatagramPacket(pong.encode(), sender));
        plugin.debugLog("Pong enviado a " + sender.getAddress().getHostAddress());
    }

    private void handleOpenConnectionRequest1(ChannelHandlerContext ctx, ByteBuf buf, InetSocketAddress sender) {
        // Verificar magic bytes de RakNet
        if (!PacketUtils.readMagic(buf)) {
            plugin.debugLog("Magic bytes invalidos en OCR1 desde " + sender);
            return;
        }

        byte protocol = buf.readByte();
        plugin.debugLog("OpenConnectionRequest1 | Protocolo: " + protocol + " | Desde: " + sender);

        // Protocolo Bedrock usa RakNet protocol 10
        if (protocol != 10) {
            PacketIncompatibleProtocol reply = new PacketIncompatibleProtocol();
            ctx.writeAndFlush(new DatagramPacket(reply.encode(), sender));
            return;
        }

        // MTU size
        int mtu = buf.readableBytes() + 1 + 16 + 1;

        PacketOpenConnectionReply1 reply = new PacketOpenConnectionReply1(mtu);
        ctx.writeAndFlush(new DatagramPacket(reply.encode(), sender));
    }

    private void handleOpenConnectionRequest2(ChannelHandlerContext ctx, ByteBuf buf, InetSocketAddress sender) {
        if (!PacketUtils.readMagic(buf)) return;

        // Leer server address
        PacketUtils.readAddress(buf);

        short mtu = buf.readShort();
        long clientGuid = buf.readLong();

        plugin.debugLog("OpenConnectionRequest2 | MTU: " + mtu + " | ClientGUID: " + clientGuid);

        PacketOpenConnectionReply2 reply = new PacketOpenConnectionReply2(sender, mtu, clientGuid);
        ctx.writeAndFlush(new DatagramPacket(reply.encode(), sender));

        // Registrar sesion en RealmGate
        plugin.debugLog("Nueva sesion Bedrock registrada: " + sender.getAddress().getHostAddress());
    }

    private void handleConnectionRequest(ChannelHandlerContext ctx, ByteBuf buf, InetSocketAddress sender) {
        long clientGuid = buf.readLong();
        long time = buf.readLong();

        plugin.debugLog("ConnectionRequest | GUID: " + clientGuid + " | Desde: " + sender);

        PacketConnectionRequestAccepted reply = new PacketConnectionRequestAccepted(sender, time);
        ctx.writeAndFlush(new DatagramPacket(reply.encode(), sender));
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        plugin.debugLog("Error en RakNetHandler: " + cause.getMessage());
    }
}
