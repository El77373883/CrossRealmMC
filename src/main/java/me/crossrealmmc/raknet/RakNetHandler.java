package me.crossrealmmc.raknet;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;
import me.crossrealmmc.CrossRealmMC;
import me.crossrealmmc.bedrock.BedrockLoginHandler;
import me.crossrealmmc.bedrock.BedrockPlayer;
import me.crossrealmmc.bedrock.BedrockPlayerRegistry;
import me.crossrealmmc.bedrock.PacketTranslator;
import me.crossrealmmc.raknet.packets.*;

import java.net.InetSocketAddress;

public class RakNetHandler extends SimpleChannelInboundHandler<DatagramPacket> {

    private final CrossRealmMC plugin;
    private final BedrockPlayerRegistry registry;
    private final BedrockLoginHandler loginHandler;
    private final PacketTranslator translator;

    public static final byte ID_UNCONNECTED_PING          = 0x01;
    public static final byte ID_UNCONNECTED_PING_OPEN     = 0x02;
    public static final byte ID_OPEN_CONNECTION_REQUEST_1 = 0x05;
    public static final byte ID_OPEN_CONNECTION_REPLY_1   = 0x06;
    public static final byte ID_OPEN_CONNECTION_REQUEST_2 = 0x07;
    public static final byte ID_OPEN_CONNECTION_REPLY_2   = 0x08;
    public static final byte ID_CONNECTION_REQUEST        = 0x09;
    public static final byte ID_CONNECTION_REQUEST_ACCEPTED = 0x10;
    public static final byte ID_NEW_INCOMING_CONNECTION   = 0x13;
    public static final byte ID_DISCONNECTION_NOTIFICATION = 0x15;
    public static final byte ID_INCOMPATIBLE_PROTOCOL     = 0x19;
    public static final byte ID_FRAME_SET_0               = (byte) 0x80;
    public static final byte ID_FRAME_SET_F               = (byte) 0x8F;
    public static final byte ID_ACK                       = (byte) 0xC0;
    public static final byte ID_NACK                      = (byte) 0xA0;

    public RakNetHandler(CrossRealmMC plugin) {
        this.plugin = plugin;
        this.registry = new BedrockPlayerRegistry();
        this.loginHandler = new BedrockLoginHandler(plugin);
        this.translator = new PacketTranslator(plugin);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket packet) {
        ByteBuf buf = packet.content();
        if (!buf.isReadable()) return;

        InetSocketAddress sender = packet.sender();
        byte packetId = buf.getByte(buf.readerIndex());

        // Frame sets = paquetes del juego encapsulados en RakNet
        if ((packetId & 0xFF) >= 0x80 && (packetId & 0xFF) <= 0x8F) {
            buf.readByte(); // consumir ID
            handleFrameSet(ctx, buf, sender);
            return;
        }

        buf.readByte(); // consumir ID

        switch (packetId) {
            case ID_UNCONNECTED_PING:
            case ID_UNCONNECTED_PING_OPEN:
                handleUnconnectedPing(ctx, sender);
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
            case ID_ACK:
            case ID_NACK:
                // Acknowledge packets — ignorar por ahora
                break;
            case ID_DISCONNECTION_NOTIFICATION:
                handleDisconnect(sender);
                break;
            default:
                plugin.debugLog("Paquete RakNet desconocido: 0x" + String.format("%02X", packetId & 0xFF));
                break;
        }
    }

    private void handleFrameSet(ChannelHandlerContext ctx, ByteBuf buf, InetSocketAddress sender) {
        try {
            if (!buf.isReadable(3)) return;

            // Leer sequence number
            buf.readMediumLE();

            // Parsear frames
            while (buf.isReadable()) {
                if (!buf.isReadable(3)) break;

                int flags = buf.readByte() & 0xFF;
                int bitLength = buf.readShort() & 0xFFFF;
                int byteLength = (bitLength + 7) / 8;

                boolean isFragmented = (flags & 0x10) != 0;
                int reliability = (flags >> 5) & 0x07;

                // Leer campos segun reliability
                if (reliability >= 2 && reliability <= 7) {
                    buf.skipBytes(3); // reliable frame index
                }
                if (reliability == 1 || reliability == 4) {
                    buf.skipBytes(3); // sequenced frame index
                }
                if (reliability == 3 || reliability == 4 ||
                    reliability == 6 || reliability == 7) {
                    buf.skipBytes(3 + 1 + 2); // ordered frame index + order channel + sequence index
                }

                // Si es fragmento
                if (isFragmented) {
                    if (!buf.isReadable(4 + 2 + 4)) break;
                    buf.skipBytes(4 + 2 + 4); // compound size, id, offset
                }

                if (!buf.isReadable(byteLength)) break;

                ByteBuf payload = buf.readBytes(byteLength);

                // Enviar ACK
                sendAck(ctx, sender);

                // Procesar payload del frame
                processGamePayload(ctx, payload, sender);
                payload.release();
            }
        } catch (Exception e) {
            plugin.debugLog("Error parseando FrameSet: " + e.getMessage());
        }
    }

    private void processGamePayload(ChannelHandlerContext ctx, ByteBuf payload, InetSocketAddress sender) {
        if (!payload.isReadable()) return;

        byte firstByte = payload.getByte(payload.readerIndex());

        // Connection Request (0x09) y New Incoming Connection (0x13)
        if (firstByte == 0x09) {
            payload.readByte();
            handleConnectionRequest(ctx, payload, sender);
            return;
        }

        if (firstByte == 0x13) {
            plugin.debugLog("NewIncomingConnection de: " + sender.getAddress().getHostAddress());
            return;
        }

        if (firstByte == 0x15) {
            handleDisconnect(sender);
            return;
        }

        // Bedrock game packet — está dentro de un batch comprimido (0xFE)
        if ((firstByte & 0xFF) == 0xFE) {
            payload.readByte();
            handleBatchPacket(ctx, payload, sender);
            return;
        }

        plugin.debugLog("Payload desconocido: 0x" + String.format("%02X", firstByte & 0xFF));
    }

    private void handleBatchPacket(ChannelHandlerContext ctx, ByteBuf buf, InetSocketAddress sender) {
        try {
            // El payload del batch está comprimido con zlib
            byte[] compressed = new byte[buf.readableBytes()];
            buf.readBytes(compressed);

            byte[] decompressed = decompress(compressed);
            if (decompressed == null) return;

            ByteBuf decompressedBuf = Unpooled.wrappedBuffer(decompressed);
            BedrockPlayer player = registry.getOrCreate(sender);

            while (decompressedBuf.isReadable()) {
                int packetLength = PacketTranslator.readVarInt(decompressedBuf);
                if (packetLength <= 0 || !decompressedBuf.isReadable(packetLength)) break;

                ByteBuf packetBuf = decompressedBuf.readBytes(packetLength);
                translator.handleIncoming(packetBuf, sender, player, loginHandler, ctx);
                packetBuf.release();
            }

            decompressedBuf.release();
        } catch (Exception e) {
            plugin.debugLog("Error en batch packet: " + e.getMessage());
        }
    }

    private byte[] decompress(byte[] data) {
        try {
            java.util.zip.Inflater inflater = new java.util.zip.Inflater(true);
            inflater.setInput(data);
            byte[] buffer = new byte[65536];
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            while (!inflater.finished()) {
                int count = inflater.inflate(buffer);
                if (count == 0) break;
                out.write(buffer, 0, count);
            }
            inflater.end();
            return out.toByteArray();
        } catch (Exception e) {
            // Algunos paquetes no están comprimidos
            return data;
        }
    }

    private void sendAck(ChannelHandlerContext ctx, InetSocketAddress sender) {
        ByteBuf ack = Unpooled.buffer(4);
        ack.writeByte(0xC0); // ACK
        ack.writeShort(1);   // record count
        ack.writeByte(1);    // is single sequence
        ack.writeMediumLE(0);
        ctx.writeAndFlush(new DatagramPacket(ack, sender));
    }

    private void handleUnconnectedPing(ChannelHandlerContext ctx, InetSocketAddress sender) {
        PacketUnconnectedPong pong = new PacketUnconnectedPong(plugin);
        ctx.writeAndFlush(new DatagramPacket(pong.encode(), sender));
    }

    private void handleOpenConnectionRequest1(ChannelHandlerContext ctx, ByteBuf buf, InetSocketAddress sender) {
        if (!PacketUtils.readMagic(buf)) return;
        byte protocol = buf.readByte();
        if (protocol != 10) {
            ctx.writeAndFlush(new DatagramPacket(new PacketIncompatibleProtocol().encode(), sender));
            return;
        }
        int mtu = buf.readableBytes() + 1 + 16 + 1;
        ctx.writeAndFlush(new DatagramPacket(new PacketOpenConnectionReply1(mtu).encode(), sender));
    }

    private void handleOpenConnectionRequest2(ChannelHandlerContext ctx, ByteBuf buf, InetSocketAddress sender) {
        if (!PacketUtils.readMagic(buf)) return;
        PacketUtils.readAddress(buf);
        short mtu = buf.readShort();
        long clientGuid = buf.readLong();
        ctx.writeAndFlush(new DatagramPacket(new PacketOpenConnectionReply2(sender, mtu, clientGuid).encode(), sender));
    }

    private void handleConnectionRequest(ChannelHandlerContext ctx, ByteBuf buf, InetSocketAddress sender) {
        if (!buf.isReadable(16)) return;
        long clientGuid = buf.readLong();
        long time = buf.readLong();
        ctx.writeAndFlush(new DatagramPacket(new PacketConnectionRequestAccepted(sender, time).encode(), sender));
    }

    private void handleDisconnect(InetSocketAddress sender) {
        BedrockPlayer player = registry.get(sender);
        if (player != null) {
            if (player.getUuid() != null)
                plugin.getPlayerDetector().unregisterPlayer(player.getUuid());
            plugin.getConnectionLogger().logDisconnect(
                player.getUsername() != null ? player.getUsername() : "Unknown",
                me.crossrealmmc.detection.PlayerDetector.PlayerType.BEDROCK,
                "Disconnected"
            );
            registry.remove(sender);
        }
        plugin.debugLog("Desconexion: " + sender.getAddress().getHostAddress());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        plugin.debugLog("Error en RakNetHandler: " + cause.getMessage());
    }
}
