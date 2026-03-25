package me.crossrealmmc.raknet.packets;

import io.netty.buffer.ByteBuf;
import me.crossrealmmc.CrossRealmMC;
import me.crossrealmmc.raknet.RakNetServer;
import org.bukkit.Bukkit;

public class PacketUnconnectedPong {

    private final CrossRealmMC plugin;
    private final long pingTime;

    public PacketUnconnectedPong(CrossRealmMC plugin, long pingTime) {
        this.plugin = plugin;
        this.pingTime = pingTime;
    }

    public PacketUnconnectedPong(CrossRealmMC plugin) {
        this(plugin, System.currentTimeMillis());
    }

    public ByteBuf encode() {
        ByteBuf buf = PacketUtils.newBuffer();

        String motd1 = color(plugin.getConfigManager().getMotdLine1());
        String motd2 = color(plugin.getConfigManager().getMotdLine2());
        int online = Bukkit.getOnlinePlayers().size();
        int max = Bukkit.getMaxPlayers();
        int port = plugin.getConfigManager().getBedrockPort();

        // Formato MCPE para Bedrock
        String serverName = "MCPE" + ";" 
                + motd1 + ";" 
                + "1048" + ";"          // Protocolo 1.21.60 (acepta cualquier versión)
                + "1.21.60" + ";"       // Versión (puede ser cualquier cosa)
                + online + ";" 
                + max + ";" 
                + RakNetServer.SERVER_GUID + ";" 
                + motd2 + ";" 
                + "Survival" + ";" 
                + "1" + ";" 
                + port + ";" 
                + "19133" + ";" 
                + "1";

        buf.writeByte(0x1C);
        buf.writeLong(pingTime);
        buf.writeLong(RakNetServer.SERVER_GUID);
        PacketUtils.writeMagic(buf);

        byte[] nameBytes = serverName.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        buf.writeShort(nameBytes.length);
        buf.writeBytes(nameBytes);

        return buf;
    }

    private String color(String s) {
        return s.replace("§", "").replace("&", "");
    }
}
