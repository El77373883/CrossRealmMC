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

/**
 * BedrockLoginHandler — Maneja el flujo de login de Bedrock
 * Login → ResourcePacks → StartGame → Spawn
 * Hecho por soyadrianyt001
 */
public class BedrockLoginHandler {

    private final CrossRealmMC plugin;
    private final XboxAuthManager xboxAuth;

    // IDs de paquetes Bedrock Game Protocol
    public static final int PACKET_LOGIN               = 0x01;
    public static final int PACKET_PLAY_STATUS         = 0x02;
    public static final int PACKET_RESOURCE_PACKS_INFO = 0x06;
    public static final int PACKET_RESOURCE_PACK_STACK = 0x07;
    public static final int PACKET_RESOURCE_PACK_RESPONSE = 0x08;
    public static final int PACKET_START_GAME          = 0x0B;
    public static final int PACKET_SET_TIME            = 0x1C;
    public static final int PACKET_RESPAWN             = 0x2D;
    public static final int PACKET_MOVE_PLAYER         = 0x13;
    public static final int PACKET_LEVEL_CHUNK         = 0x3A;
    public static final int PACKET_PLAYER_LIST         = 0x3F;

    // Play Status codes
    public static final int STATUS_LOGIN_SUCCESS        = 0;
    public static final int STATUS_FAILED_CLIENT        = 1;
    public static final int STATUS_FAILED_SPAWN         = 2;
    public static final int STATUS_PLAYER_SPAWN         = 3;

    public BedrockLoginHandler(CrossRealmMC plugin) {
        this.plugin = plugin;
        this.xboxAuth = new XboxAuthManager();
    }

    public void handleLoginPacket(ChannelHandlerContext ctx, ByteBuf buf, InetSocketAddress sender, BedrockPlayer player) {
        try {
            // Leer protocolo
            int protocol = buf.readIntLE();
            player.setProtocol(protocol);
            plugin.debugLog("Login Bedrock | Protocolo: " + protocol + " | IP: " + sender.getAddress().getHostAddress());

            // Leer JWT chain
            int chainLength = buf.readIntLE();
            if (chainLength <= 0 || chainLength > 65536) {
                plugin.debugLog("JWT chain invalida, length: " + chainLength);
                return;
            }

            byte[] chainBytes = new byte[chainLength];
            buf.readBytes(chainBytes);
            String jwtChain = new String(chainBytes, StandardCharsets.UTF_8);

            // Autenticar
            XboxAuthManager.AuthResult auth = xboxAuth.authenticate(
                    jwtChain, plugin.getConfigManager().isBedrockOnlineMode());

            if (!auth.authenticated) {
                plugin.debugLog("Auth fallida: " + auth.errorMessage);
                sendPlayStatus(ctx, sender, STATUS_FAILED_CLIENT);
                return;
            }

            // Aplicar prefijo Bedrock
            String prefixedName = plugin.getConfigManager().getBedrockPrefix() + auth.username;
            player.setUsername(prefixedName);
            player.setXuid(auth.xuid);
            player.setUuid(auth.uuid);
            player.setState(BedrockPlayer.State.LOGIN);

            plugin.debugLog("Jugador autenticado: " + prefixedName);

            // Verificar ban
            if (plugin.getBanManager().isBanned(auth.username, PlayerDetector.PlayerType.BEDROCK)) {
                sendPlayStatus(ctx, sender, STATUS_FAILED_CLIENT);
                return;
            }

            // Registrar en detector
            plugin.getPlayerDetector().registerPlayer(auth.uuid, PlayerDetector.PlayerType.BEDROCK,
                    player.getBedrockVersion());

            // Enviar login success
            sendPlayStatus(ctx, sender, STATUS_LOGIN_SUCCESS);

            // Enviar resource packs
            sendResourcePacksInfo(ctx, sender);

        } catch (Exception e) {
            plugin.getLogger().warning("Error en login Bedrock: " + e.getMessage());
            if (plugin.getConfigManager().isDebug()) e.printStackTrace();
        }
    }

    public void handleResourcePackResponse(ChannelHandlerContext ctx, ByteBuf buf,
            InetSocketAddress sender, BedrockPlayer player) {
        if (!buf.isReadable()) return;
        int status = buf.readByte() & 0xFF;
        plugin.debugLog("ResourcePackResponse: " + status + " de " + player.getUsername());

        // Status 4 = completado, Status 2 = tiene todos
        if (status == 4 || status == 2) {
            sendResourcePackStack(ctx, sender);
        } else if (status == 3) {
            // Jugador tiene los packs, proceder
            sendStartGame(ctx, sender, player);
        }
    }

    // ─────────────────────────────────────────
    // PAQUETES AL CLIENTE
    // ─────────────────────────────────────────

    private void sendPlayStatus(ChannelHandlerContext ctx, InetSocketAddress sender, int status) {
        ByteBuf buf = Unpooled.buffer();
        writeVarInt(buf, PACKET_PLAY_STATUS);
        buf.writeInt(status);
        sendGamePacket(ctx, sender, buf);
    }

    private void sendResourcePacksInfo(ChannelHandlerContext ctx, InetSocketAddress sender) {
        ByteBuf buf = Unpooled.buffer();
        writeVarInt(buf, PACKET_RESOURCE_PACKS_INFO);
        buf.writeBoolean(false); // must accept
        buf.writeBoolean(false); // has scripts
        buf.writeShortLE(0);     // behaviour packs count
        buf.writeShortLE(0);     // resource packs count
        sendGamePacket(ctx, sender, buf);
    }

    private void sendResourcePackStack(ChannelHandlerContext ctx, InetSocketAddress sender) {
        ByteBuf buf = Unpooled.buffer();
        writeVarInt(buf, PACKET_RESOURCE_PACK_STACK);
        buf.writeBoolean(false); // must accept
        writeVarInt(buf, 0);     // addons count
        writeVarInt(buf, 0);     // resource packs count
        writeString(buf, "1.21.0"); // game version
        buf.writeIntLE(0);       // experiments
        buf.writeBoolean(false); // experiments previously toggled
        sendGamePacket(ctx, sender, buf);
    }

    public void sendStartGame(ChannelHandlerContext ctx, InetSocketAddress sender, BedrockPlayer player) {
        player.setState(BedrockPlayer.State.SPAWNING);
        player.setPosition(0, 64, 0);

        ByteBuf buf = Unpooled.buffer();
        writeVarInt(buf, PACKET_START_GAME);

        // Entity IDs
        writeVarLong(buf, player.getEntityId()); // entity unique id
        writeVarLong(buf, player.getEntityId()); // entity runtime id

        // Gamemode
        writeVarInt(buf, 0); // survival

        // Posicion spawn
        buf.writeFloatLE(player.getX());
        buf.writeFloatLE(player.getY());
        buf.writeFloatLE(player.getZ());

        // Rotacion
        buf.writeFloatLE(player.getYaw());
        buf.writeFloatLE(player.getPitch());

        // Seed
        buf.writeLongLE(0);

        // Spawn biome type
        buf.writeShortLE(0);
        writeString(buf, "plains");

        // Dimension
        writeVarInt(buf, 0); // overworld

        // Generator
        writeVarInt(buf, 1); // infinite

        // World gamemode
        writeVarInt(buf, 0); // survival

        // Difficulty
        writeVarInt(buf, 1); // easy

        // World spawn
        writeVarInt(buf, 0);  // x
        writeVarInt(buf, 64); // y
        writeVarInt(buf, 0);  // z

        buf.writeBoolean(false); // achievements disabled
        buf.writeBoolean(false); // editor world

        writeVarInt(buf, 0);     // day cycle stop time
        writeVarInt(buf, 0);     // edu edition offers

        buf.writeBoolean(false); // edu features
        writeString(buf, "");    // edu product uuid

        buf.writeFloatLE(0);     // rain level
        buf.writeFloatLE(0);     // lightning level

        buf.writeBoolean(false); // confirmed platform locked
        buf.writeBoolean(true);  // multi player game
        buf.writeBoolean(true);  // broadcast to lan

        writeVarInt(buf, 4);     // xbox live broadcast mode
        writeVarInt(buf, 4);     // platform broadcast mode

        buf.writeBoolean(true);  // commands enabled
        buf.writeBoolean(false); // texture packs required

        writeVarInt(buf, 0);     // gamerules count

        buf.writeIntLE(0);       // experiments count
        buf.writeBoolean(false); // experiments previously toggled

        buf.writeBoolean(false); // bonus chest
        buf.writeBoolean(false); // start with map
        writeVarInt(buf, 1);     // permission level (member)
        buf.writeIntLE(16);      // chunk tick range

        buf.writeBoolean(false); // locked behavior pack
        buf.writeBoolean(false); // locked resource pack
        buf.writeBoolean(false); // from locked template
        buf.writeBoolean(false); // msa gamer tags only
        buf.writeBoolean(false); // from world template
        buf.writeBoolean(false); // world template option locked
        buf.writeBoolean(false); // only spawn v1 villagers
        buf.writeBoolean(false); // persona disabled
        buf.writeBoolean(false); // custom skins disabled
        buf.writeBoolean(false); // emote chat muted

        writeString(buf, "26.3"); // game version
        buf.writeIntLE(0);       // limited world width
        buf.writeIntLE(0);       // limited world height
        buf.writeBoolean(false); // nether type
        writeString(buf, "");    // edu shared uri button name
        writeString(buf, "");    // edu shared uri link
        buf.writeBoolean(false); // force experimental gameplay

        writeString(buf, "CrossRealmMC"); // level id
        writeString(buf, "CrossRealmMC"); // world name
        writeString(buf, "");    // premium world template id

        buf.writeBoolean(false); // is trial
        writeVarInt(buf, 0);     // movement authority
        writeVarInt(buf, 0);     // rewind history size
        buf.writeBoolean(false); // server auth block breaking
        buf.writeLongLE(System.currentTimeMillis()); // current time
        writeString(buf, "CrossRealmMC-" + player.getEntityId()); // enchantment seed

        writeVarInt(buf, 0);     // block properties count
        writeVarInt(buf, 0);     // itemstates count

        writeString(buf, "");    // multiplayer correlation id
        buf.writeBoolean(true);  // server authoritative inventory
        writeString(buf, "CrossRealmMC"); // engine version
        writeVarInt(buf, 0);     // property data

        buf.writeLongLE(0);      // block type registry checksum
        writeString(buf, player.getUuid().toString()); // world template id
        buf.writeBoolean(false); // client side generation
        buf.writeBoolean(false); // block network ids are hashes
        buf.writeBoolean(false); // server controlled sound

        sendGamePacket(ctx, sender, buf);

        // Después del start game, hacer spawn
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            sendSetTime(ctx, sender);
            sendRespawn(ctx, sender, player);
            sendPlayStatus(ctx, sender, STATUS_PLAYER_SPAWN);
            player.setState(BedrockPlayer.State.PLAYING);
            plugin.debugLog("Jugador spawneado: " + player.getUsername());

            // Log de conexion
            plugin.getConnectionLogger().logJoin(
                player.getUsername(),
                PlayerDetector.PlayerType.BEDROCK,
                sender.getAddress().getHostAddress(),
                player.getBedrockVersion()
            );

            // Anunciar en servidor
            Bukkit.getScheduler().runTask(plugin, () -> {
                String joinMsg = plugin.getConfigManager().getMessage("player-join-bedrock",
                        "{player}", player.getUsername(),
                        "{version}", player.getBedrockVersion() != null ? player.getBedrockVersion() : "Bedrock");
                Bukkit.broadcastMessage(joinMsg);
            });
        }, 20L);
    }

    private void sendSetTime(ChannelHandlerContext ctx, InetSocketAddress sender) {
        ByteBuf buf = Unpooled.buffer();
        writeVarInt(buf, PACKET_SET_TIME);
        writeVarInt(buf, 6000); // mediodía
        sendGamePacket(ctx, sender, buf);
    }

    private void sendRespawn(ChannelHandlerContext ctx, InetSocketAddress sender, BedrockPlayer player) {
        ByteBuf buf = Unpooled.buffer();
        writeVarInt(buf, PACKET_RESPAWN);
        buf.writeFloatLE(player.getX());
        buf.writeFloatLE(player.getY());
        buf.writeFloatLE(player.getZ());
        buf.writeByte(0); // state
        writeVarLong(buf, player.getEntityId());
        sendGamePacket(ctx, sender, buf);
    }

    // ─────────────────────────────────────────
    // UTILS
    // ─────────────────────────────────────────

    private void sendGamePacket(ChannelHandlerContext ctx, InetSocketAddress sender, ByteBuf payload) {
        try {
            ByteBuf wrapper = Unpooled.buffer();
            // Header del game packet
            writeVarInt(wrapper, payload.readableBytes());
            wrapper.writeBytes(payload);
            ctx.writeAndFlush(new DatagramPacket(wrapper, sender));
        } catch (Exception e) {
            plugin.debugLog("Error enviando game packet: " + e.getMessage());
        } finally {
            payload.release();
        }
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
