package me.crossrealmmc.bedrock;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import me.crossrealmmc.CrossRealmMC;
import me.crossrealmmc.detection.PlayerDetector;
import org.bukkit.Bukkit;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.Deflater;

public class BedrockLoginHandler {

    private final CrossRealmMC plugin;
    private final AtomicInteger sendSequence;
    private final AtomicInteger messageIndex;
    private final AtomicInteger orderIndex;

    public static final int PACKET_LOGIN               = 0x01;
    public static final int PACKET_PLAY_STATUS         = 0x02;
    public static final int PACKET_RESOURCE_PACKS_INFO = 0x06;
    public static final int PACKET_RESOURCE_PACK_STACK = 0x07;
    public static final int PACKET_RESOURCE_PACK_RESPONSE = 0x08;
    public static final int PACKET_START_GAME          = 0x0B;
    public static final int PACKET_SET_TIME            = 0x1C;
    public static final int PACKET_RESPAWN             = 0x2D;

    public static final int STATUS_LOGIN_SUCCESS = 0;
    public static final int STATUS_FAILED_CLIENT = 1;
    public static final int STATUS_PLAYER_SPAWN  = 3;

    public BedrockLoginHandler(CrossRealmMC plugin, AtomicInteger sendSequence,
            AtomicInteger messageIndex, AtomicInteger orderIndex) {
        this.plugin = plugin;
        this.sendSequence = sendSequence;
        this.messageIndex = messageIndex;
        this.orderIndex = orderIndex;
    }

    public void handleLoginPacket(ChannelHandlerContext ctx, ByteBuf buf,
            InetSocketAddress sender, BedrockPlayer player) {
        try {
            int protocol = buf.readIntLE();
            player.setProtocol(protocol);
            plugin.log("&aLogin Bedrock | Protocolo: &e" + protocol
                    + " &7de: &f" + sender.getAddress().getHostAddress());

            if (!plugin.getConfigManager().isBedrockOnlineMode()) {
                String username = "Bedrock_" + (int)(Math.random() * 99999);
                String prefixedName = plugin.getConfigManager().getBedrockPrefix() + username;
                UUID uuid = UUID.nameUUIDFromBytes(
                        ("bedrock_offline:" + username).getBytes(StandardCharsets.UTF_8));
                player.setUsername(prefixedName);
                player.setUuid(uuid);
                player.setState(BedrockPlayer.State.LOGIN);
                plugin.log("&aJugador offline aceptado: &e" + prefixedName);
                plugin.getPlayerDetector().registerPlayer(uuid, PlayerDetector.PlayerType.BEDROCK, "26.3");
                sendPlayStatus(ctx, sender, STATUS_LOGIN_SUCCESS);
                sendResourcePacksInfo(ctx, sender);
                return;
            }

            if (!buf.isReadable(4)) { sendPlayStatus(ctx, sender, STATUS_FAILED_CLIENT); return; }
            int chainLength = buf.readIntLE();
            if (chainLength <= 0 || chainLength > 65536) { sendPlayStatus(ctx, sender, STATUS_FAILED_CLIENT); return; }
            byte[] chainBytes = new byte[chainLength];
            buf.readBytes(chainBytes);
            String jwtChain = new String(chainBytes, StandardCharsets.UTF_8);

            XboxAuthManager xboxAuth = new XboxAuthManager();
            XboxAuthManager.AuthResult auth = xboxAuth.authenticate(jwtChain, true);
            if (!auth.authenticated) {
                plugin.log("&cAuth fallida: &f" + auth.errorMessage);
                sendPlayStatus(ctx, sender, STATUS_FAILED_CLIENT);
                return;
            }

            String prefixedName = plugin.getConfigManager().getBedrockPrefix() + auth.username;
            player.setUsername(prefixedName);
            player.setXuid(auth.xuid);
            player.setUuid(auth.uuid);
            player.setState(BedrockPlayer.State.LOGIN);
            plugin.log("&aJugador autenticado: &e" + prefixedName);
            plugin.getPlayerDetector().registerPlayer(auth.uuid, PlayerDetector.PlayerType.BEDROCK, "26.3");
            sendPlayStatus(ctx, sender, STATUS_LOGIN_SUCCESS);
            sendResourcePacksInfo(ctx, sender);

        } catch (Exception e) {
            plugin.log("&cError en login: &f" + e.getMessage());
            if (plugin.getConfigManager().isDebug()) e.printStackTrace();
        }
    }

    public void handleResourcePackResponse(ChannelHandlerContext ctx, ByteBuf buf,
            InetSocketAddress sender, BedrockPlayer player) {
        if (!buf.isReadable()) return;
        int status = buf.readByte() & 0xFF;
        plugin.log("&aResourcePackResponse: &e" + status);

        if (status == 3 || status == 2) {
            sendResourcePackStack(ctx, sender);
        } else if (status == 4 || status == 1) {
            sendStartGame(ctx, sender, player);
        }
    }

    private void sendPlayStatus(ChannelHandlerContext ctx, InetSocketAddress sender, int status) {
        ByteBuf buf = Unpooled.buffer();
        writeVarInt(buf, PACKET_PLAY_STATUS);
        buf.writeInt(status);
        sendGamePacket(ctx, sender, buf);
        plugin.log("&aPlayStatus enviado: &e" + status);
    }

    private void sendResourcePacksInfo(ChannelHandlerContext ctx, InetSocketAddress sender) {
        ByteBuf buf = Unpooled.buffer();
        writeVarInt(buf, PACKET_RESOURCE_PACKS_INFO);
        buf.writeBoolean(false);
        buf.writeBoolean(false);
        buf.writeBoolean(false);
        buf.writeShortLE(0);
        buf.writeShortLE(0);
        sendGamePacket(ctx, sender, buf);
        plugin.log("&aResourcePacksInfo enviado");
    }

    private void sendResourcePackStack(ChannelHandlerContext ctx, InetSocketAddress sender) {
        ByteBuf buf = Unpooled.buffer();
        writeVarInt(buf, PACKET_RESOURCE_PACK_STACK);
        buf.writeBoolean(false);
        writeVarInt(buf, 0);
        writeVarInt(buf, 0);
        writeString(buf, "1.21.1");
        buf.writeIntLE(0);
        buf.writeBoolean(false);
        sendGamePacket(ctx, sender, buf);
        plugin.log("&aResourcePackStack enviado");
    }

    public void sendStartGame(ChannelHandlerContext ctx, InetSocketAddress sender, BedrockPlayer player) {
        if (player.getState() == BedrockPlayer.State.PLAYING ||
            player.getState() == BedrockPlayer.State.SPAWNING) return;

        player.setState(BedrockPlayer.State.SPAWNING);
        player.setPosition(0, 64, 0);
        plugin.log("&aEnviando StartGame a: &e" + player.getUsername());

        ByteBuf buf = Unpooled.buffer();
        writeVarInt(buf, PACKET_START_GAME);
        writeVarLong(buf, player.getEntityId());
        writeVarLong(buf, player.getEntityId());
        writeVarInt(buf, 0);
        buf.writeFloatLE(0);
        buf.writeFloatLE(64);
        buf.writeFloatLE(0);
        buf.writeFloatLE(0);
        buf.writeFloatLE(0);
        buf.writeLongLE(0);
        buf.writeShortLE(0);
        writeString(buf, "plains");
        writeVarInt(buf, 0);
        writeVarInt(buf, 1);
        writeVarInt(buf, 0);
        writeVarInt(buf, 1);
        writeVarInt(buf, 0);
        writeVarInt(buf, 0);
        buf.writeBoolean(false);
        buf.writeBoolean(false);
        buf.writeBoolean(false);
        writeString(buf, "");
        buf.writeFloatLE(0);
        buf.writeFloatLE(0);
        buf.writeBoolean(false);
        buf.writeBoolean(true);
        buf.writeBoolean(true);
        writeVarInt(buf, 4);
        writeVarInt(buf, 4);
        buf.writeBoolean(true);
        buf.writeBoolean(false);
        writeVarInt(buf, 0);
        buf.writeIntLE(0);
        buf.writeBoolean(false);
        buf.writeBoolean(false);
        buf.writeBoolean(false);
        buf.writeBoolean(false);
        buf.writeBoolean(false);
        buf.writeBoolean(false);
        buf.writeBoolean(false);
        buf.writeBoolean(false);
        buf.writeBoolean(false);
        buf.writeBoolean(false);
        buf.writeBoolean(false);
        writeString(buf, "26.3");
        buf.writeIntLE(0);
        buf.writeIntLE(0);
        buf.writeBoolean(false);
        writeString(buf, "");
        writeString(buf, "");
        buf.writeBoolean(false);
        writeString(buf, "CrossRealmMC");
        writeString(buf, "CrossRealmMC");
        writeString(buf, "");
        buf.writeBoolean(false);
        writeVarInt(buf, 0);
        writeVarInt(buf, 0);
        buf.writeBoolean(false);
        buf.writeLongLE(System.currentTimeMillis());
        writeString(buf, "CrossRealmMC");
        writeVarInt(buf, 0);
        writeVarInt(buf, 0);
        writeString(buf, "");
        buf.writeBoolean(true);
        writeString(buf, "CrossRealmMC");
        writeVarInt(buf, 0);
        buf.writeLongLE(0);
        writeString(buf, player.getUuid().toString());
        buf.writeBoolean(false);
        buf.writeBoolean(false);
        buf.writeBoolean(false);

        sendGamePacket(ctx, sender, buf);
        plugin.log("&aStartGame enviado");

        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            sendSetTime(ctx, sender);
            sendRespawn(ctx, sender, player);
            sendPlayStatus(ctx, sender, STATUS_PLAYER_SPAWN);
            player.setState(BedrockPlayer.State.PLAYING);
            plugin.log("&a✔ Jugador spawneado: &e" + player.getUsername());
            plugin.getConnectionLogger().logJoin(
                player.getUsername(),
                PlayerDetector.PlayerType.BEDROCK,
                sender.getAddress().getHostAddress(),
                "26.3"
            );
            Bukkit.getScheduler().runTask(plugin, () -> {
                String joinMsg = plugin.getConfigManager().getMessage(
                        "player-join-bedrock",
                        "{player}", player.getUsername(),
                        "{version}", "26.3");
                Bukkit.broadcastMessage(joinMsg);
            });
        }, 2L);
    }

    private void sendSetTime(ChannelHandlerContext ctx, InetSocketAddress sender) {
        ByteBuf buf = Unpooled.buffer();
        writeVarInt(buf, PACKET_SET_TIME);
        writeVarInt(buf, 6000);
        sendGamePacket(ctx, sender, buf);
    }

    private void sendRespawn(ChannelHandlerContext ctx, InetSocketAddress sender, BedrockPlayer player) {
        ByteBuf buf = Unpooled.buffer();
        writeVarInt(buf, PACKET_RESPAWN);
        buf.writeFloatLE(0);
        buf.writeFloatLE(64);
        buf.writeFloatLE(0);
        buf.writeByte(0);
        writeVarLong(buf, player.getEntityId());
        sendGamePacket(ctx, sender, buf);
    }

    private void sendGamePacket(ChannelHandlerContext ctx, InetSocketAddress sender, ByteBuf payload) {
        try {
            byte[] data = new byte[payload.readableBytes()];
            payload.readBytes(data);
            payload.release();

            // Comprimir con zlib
            Deflater deflater = new Deflater();
            deflater.setInput(data);
            deflater.finish();
            byte[] compressed = new byte[data.length + 100];
            int compressedLen = deflater.deflate(compressed);
            deflater.end();

            ByteBuf gamePacket = Unpooled.buffer();
            gamePacket.writeByte(0xFE);
            writeVarInt(gamePacket, compressedLen + 1); // +1 por el byte de algoritmo
            gamePacket.writeByte(0x00); // algorithm = zlib
            gamePacket.writeBytes(compressed, 0, compressedLen);

            ByteBuf frame = Unpooled.buffer();
            frame.writeByte(0x84);
            frame.writeMediumLE(sendSequence.getAndIncrement());
            frame.writeByte(0x60);
            frame.writeShort(gamePacket.readableBytes() * 8);
            frame.writeMediumLE(messageIndex.getAndIncrement());
            frame.writeMediumLE(orderIndex.getAndIncrement());
            frame.writeByte(0);
            frame.writeBytes(gamePacket);
            gamePacket.release();

            ctx.writeAndFlush(new DatagramPacket(frame, sender));
        } catch (Exception e) {
            plugin.log("&cError enviando paquete: &f" + e.getMessage());
        }
    }

    public void sendGamePacketPublic(ChannelHandlerContext ctx, InetSocketAddress sender, ByteBuf payload) {
        sendGamePacket(ctx, sender, payload);
    }

    public static void writeVarInt(ByteBuf buf, int value) {
        while ((value & ~0x7F) != 0) {
            buf.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        buf.writeByte(value);
    }

    public static void writeVarLong(ByteBuf buf, long value) {
        while ((value & ~0x7FL) != 0) {
            buf.writeByte((int)((value & 0x7F) | 0x80));
            value >>>= 7;
        }
        buf.writeByte((int) value);
    }

    public static void writeString(ByteBuf buf, String str) {
        byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
        writeVarInt(buf, bytes.length);
        buf.writeBytes(bytes);
    }
}
