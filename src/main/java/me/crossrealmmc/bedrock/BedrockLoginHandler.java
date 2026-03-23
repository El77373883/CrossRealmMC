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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class BedrockLoginHandler {

    private final CrossRealmMC plugin;
    private final AtomicInteger sendSequence;
    private final AtomicInteger messageIndex;
    private final AtomicInteger orderIndex;
    private final Map<Integer, byte[]> sentPacketCache;

    public static final int PACKET_LOGIN               = 0x01;
    public static final int PACKET_PLAY_STATUS         = 0x02;
    public static final int PACKET_RESOURCE_PACKS_INFO = 0x06;
    public static final int PACKET_RESOURCE_PACK_STACK = 0x07;
    public static final int PACKET_RESOURCE_PACK_RESPONSE = 0x08;
    public static final int PACKET_START_GAME          = 0x0B;
    public static final int PACKET_LEVEL_CHUNK         = 0x3A;
    public static final int PACKET_SET_TIME            = 0x1C;
    public static final int PACKET_RESPAWN             = 0x2D;
    public static final int PACKET_CHUNK_RADIUS_REPLY  = 0x46;

    public static final int STATUS_LOGIN_SUCCESS = 0;
    public static final int STATUS_FAILED_CLIENT = 1;
    public static final int STATUS_PLAYER_SPAWN  = 3;

    public BedrockLoginHandler(CrossRealmMC plugin, AtomicInteger sendSequence,
            AtomicInteger messageIndex, AtomicInteger orderIndex,
            Map<Integer, byte[]> sentPacketCache) {
        this.plugin = plugin;
        this.sendSequence = sendSequence;
        this.messageIndex = messageIndex;
        this.orderIndex = orderIndex;
        this.sentPacketCache = sentPacketCache;
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
                sendStartGame(ctx, sender, player);
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
            sendStartGame(ctx, sender, player);

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
        player.setPosition(0, 4, 0);
        plugin.log("&aEnviando StartGame a: &e" + player.getUsername());

        ByteBuf buf = Unpooled.buffer();
        writeVarInt(buf, PACKET_START_GAME);
        writeVarLong(buf, player.getEntityId());
        writeVarLong(buf, player.getEntityId());
        writeVarInt(buf, 0);
        buf.writeFloatLE(0);
        buf.writeFloatLE(4);
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

        sendBiomeDefinitionList(ctx, sender);
        sendAvailableEntityIdentifiers(ctx, sender);
        sendCreativeContent(ctx, sender);

        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            sendSetTime(ctx, sender);
            sendRespawn(ctx, sender, player);
            sendChunkRadiusReply(ctx, sender, 4);
            sendEmptyChunks(ctx, sender, player);
            sendNetworkChunkPublisherUpdate(ctx, sender, player);
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

        // Timer periódico cada 5 segundos
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (player.getState() == BedrockPlayer.State.PLAYING) {
                sendNetworkChunkPublisherUpdate(ctx, sender, player);
            }
        }, 20L, 100L);
    }

    private void sendBiomeDefinitionList(ChannelHandlerContext ctx, InetSocketAddress sender) {
        ByteBuf buf = Unpooled.buffer();
        writeVarInt(buf, 0x7A);
        buf.writeByte(0x0A);
        buf.writeByte(0x00);
        buf.writeByte(0x00);
        buf.writeByte(0x00);
        sendGamePacket(ctx, sender, buf);
        plugin.log("&aBiomeDefinitionList enviado");
    }

    private void sendAvailableEntityIdentifiers(ChannelHandlerContext ctx, InetSocketAddress sender) {
        ByteBuf buf = Unpooled.buffer();
        writeVarInt(buf, 0x77);
        buf.writeByte(0x0A);
        buf.writeByte(0x00);
        buf.writeByte(0x00);
        buf.writeByte(0x00);
        sendGamePacket(ctx, sender, buf);
        plugin.log("&aAvailableEntityIdentifiers enviado");
    }

    private void sendCreativeContent(ChannelHandlerContext ctx, InetSocketAddress sender) {
        ByteBuf buf = Unpooled.buffer();
        writeVarInt(buf, 0x91);
        writeVarInt(buf, 0);
        sendGamePacket(ctx, sender, buf);
        plugin.log("&aCreativeContent enviado");
    }

    private void sendNetworkChunkPublisherUpdate(ChannelHandlerContext ctx,
            InetSocketAddress sender, BedrockPlayer player) {
        ByteBuf buf = Unpooled.buffer();
        writeVarInt(buf, 0x79);
        writeZigZagInt(buf, (int) player.getX());
        writeVarInt(buf, (int) player.getY());
        writeZigZagInt(buf, (int) player.getZ());
        writeVarInt(buf, 32);
        writeVarInt(buf, 0);
        sendGamePacket(ctx, sender, buf);
    }

    private void sendChunkRadiusReply(ChannelHandlerContext ctx, InetSocketAddress sender, int radius) {
        ByteBuf buf = Unpooled.buffer();
        writeVarInt(buf, PACKET_CHUNK_RADIUS_REPLY);
        writeVarInt(buf, radius);
        sendGamePacket(ctx, sender, buf);
        plugin.log("&aChunkRadiusReply enviado: &e" + radius);
    }

    private void sendEmptyChunks(ChannelHandlerContext ctx, InetSocketAddress sender, BedrockPlayer player) {
        int radius = 4;
        int chunkX = (int) player.getX() >> 4;
        int chunkZ = (int) player.getZ() >> 4;
        int count = 0;
        for (int x = chunkX - radius; x <= chunkX + radius; x++) {
            for (int z = chunkZ - radius; z <= chunkZ + radius; z++) {
                sendEmptyChunk(ctx, sender, x, z);
                count++;
            }
        }
        plugin.log("&aChunks vacíos enviados: &e" + count);
    }

    private void sendEmptyChunk(ChannelHandlerContext ctx, InetSocketAddress sender, int chunkX, int chunkZ) {
        try {
            ByteBuf chunkData = Unpooled.buffer();

            for (int i = 0; i < 24; i++) {
                chunkData.writeByte(8);
                chunkData.writeByte(2);
                chunkData.writeByte(1);
                writeVarInt(chunkData, 0);
                chunkData.writeByte(1);
                writeVarInt(chunkData, 0);
            }

            for (int i = 0; i < 25; i++) {
                chunkData.writeByte(1);
                writeVarInt(chunkData, 1);
            }
            writeVarInt(chunkData, 0);

            ByteBuf buf = Unpooled.buffer();
            writeVarInt(buf, PACKET_LEVEL_CHUNK);
            writeZigZagInt(buf, chunkX);
            writeZigZagInt(buf, chunkZ);
            writeVarInt(buf, 0);
            writeVarInt(buf, 24);
            buf.writeBoolean(false);
            writeVarInt(buf, chunkData.readableBytes());
            buf.writeBytes(chunkData);
            chunkData.release();

            sendGamePacket(ctx, sender, buf);
        } catch (Exception e) {
            plugin.debugLog("Error enviando chunk: " + e.getMessage());
        }
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
        buf.writeFloatLE(4);
        buf.writeFloatLE(0);
        buf.writeByte(0);
        writeVarLong(buf, player.getEntityId());
        sendGamePacket(ctx, sender, buf);
    }

    private void sendGamePacket(ChannelHandlerContext ctx, InetSocketAddress sender, ByteBuf payload) {
        try {
            ByteBuf gamePacket = Unpooled.buffer();
            gamePacket.writeByte(0xFE);
            writeVarInt(gamePacket, payload.readableBytes() + 1);
            gamePacket.writeByte(0xFF);
            gamePacket.writeBytes(payload);
            payload.release();

            int seqNum = sendSequence.getAndIncrement();
            ByteBuf frame = Unpooled.buffer();
            frame.writeByte(0x84);
            frame.writeMediumLE(seqNum);
            frame.writeByte(0x60);
            frame.writeShort(gamePacket.readableBytes() * 8);
            frame.writeMediumLE(messageIndex.getAndIncrement());
            frame.writeMediumLE(orderIndex.getAndIncrement());
            frame.writeByte(0);
            frame.writeBytes(gamePacket);
            gamePacket.release();

            byte[] frameBytes = new byte[frame.readableBytes()];
            frame.getBytes(0, frameBytes);
            sentPacketCache.put(seqNum, frameBytes);

            ctx.writeAndFlush(new DatagramPacket(frame, sender));
        } catch (Exception e) {
            plugin.log("&cError enviando paquete: &f" + e.getMessage());
        }
    }

    public void sendRawGamePacketPublic(ChannelHandlerContext ctx, InetSocketAddress sender, ByteBuf payload) {
        try {
            ByteBuf gamePacket = Unpooled.buffer();
            gamePacket.writeByte(0xFE);
            writeVarInt(gamePacket, payload.readableBytes());
            gamePacket.writeBytes(payload);
            payload.release();

            int seqNum = sendSequence.getAndIncrement();
            ByteBuf frame = Unpooled.buffer();
            frame.writeByte(0x84);
            frame.writeMediumLE(seqNum);
            frame.writeByte(0x60);
            frame.writeShort(gamePacket.readableBytes() * 8);
            frame.writeMediumLE(messageIndex.getAndIncrement());
            frame.writeMediumLE(orderIndex.getAndIncrement());
            frame.writeByte(0);
            frame.writeBytes(gamePacket);
            gamePacket.release();

            byte[] frameBytes = new byte[frame.readableBytes()];
            frame.getBytes(0, frameBytes);
            sentPacketCache.put(seqNum, frameBytes);

            ctx.writeAndFlush(new DatagramPacket(frame, sender));
        } catch (Exception e) {
            plugin.log("&cError enviando raw: &f" + e.getMessage());
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

    public static void writeZigZagInt(ByteBuf buf, int value) {
        writeVarInt(buf, (value << 1) ^ (value >> 31));
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
