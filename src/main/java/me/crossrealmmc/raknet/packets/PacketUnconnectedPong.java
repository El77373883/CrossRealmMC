package me.crossrealmmc.raknet.packets;

import io.netty.buffer.ByteBuf;
import me.crossrealmmc.CrossRealmMC;
import me.crossrealmmc.raknet.RakNetServer;
import org.bukkit.Bukkit;

public class PacketUnconnectedPong {

    private final CrossRealmMC plugin;
    private final long pingTime;

    private static final int BEDROCK_PROTOCOL = 786;      // v26.3
    private static final String BEDROCK_VERSION = "26.3";

    public PacketUnconnectedPong(CrossRealmMC plugin, long pingTime) {
        this.plugin = plugin;
        this.pingTime = pingTime;
    }

    public PacketUnconnectedPong(CrossRealmMC plugin) {
        this(plugin, System.currentTimeMillis());
    }

    public ByteBuf encode() {
        ByteBuf buf = PacketUtils.newBuffer();

        // Limpiar códigos de color (el MOTD de Bedrock no soporta &)
        String motd1 = color(plugin.getConfigManager().getMotdLine1());
        String motd2 = color(plugin.getConfigManager().getMotdLine2());

        int online = Bukkit.getOnlinePlayers().size();
        int max = Bukkit.getMaxPlayers();

        // Formato del servidor para Bedrock (MCPE)
        String serverName = "MCPE" + ";"
                + motd1 + ";"
                + BEDROCK_PROTOCOL + ";"
                + BEDROCK_VERSION + ";"
                + online + ";"
                + max + ";"
                + RakNetServer.SERVER_GUID + ";"
                + motd2 + ";"
                + "Creative" + ";"
                + "1" + ";"
                + plugin.getConfigManager().getBedrockPort() + ";"
                + "19133" + ";"
                + "1";

        buf.writeByte(0x1C);                               // ID_UNCONNECTED_PONG
        buf.writeLong(pingTime);                           // Ping time
        buf.writeLong(RakNetServer.SERVER_GUID);           // Server GUID
        PacketUtils.writeMagic(buf);                       // RakNet magic

        byte[] nameBytes = serverName.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        buf.writeShort(nameBytes.length);
        buf.writeBytes(nameBytes);

        return buf;
    }

    private String color(String s) {
        // Eliminar los códigos de color (tanto § como &) porque Bedrock no los entiende en el MOTD
        return s.replace("§", "").replace("&", "");
    }
}
