package me.crossrealmmc.raknet.packets;

import io.netty.buffer.ByteBuf;
import me.crossrealmmc.CrossRealmMC;
import me.crossrealmmc.raknet.RakNetServer;
import org.bukkit.Bukkit;

public class PacketUnconnectedPong {

    private final CrossRealmMC plugin;
    private final long pingTime;

    private static final int BEDROCK_PROTOCOL = 924;
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

        String motd1 = color(plugin.getConfigManager().getMotdLine1());
        String motd2 = color(plugin.getConfigManager().getMotdLine2());
        int online = Bukkit.getOnlinePlayers().size();
        int max = Bukkit.getMaxPlayers();

        String serverName = "MCPE"
                + ";" + motd1
                + ";" + BEDROCK_PROTOCOL
                + ";" + BEDROCK_VERSION
                + ";" + online
                + ";" + max
                + ";" + RakNetServer.SERVER_GUID
                + ";" + motd2
                + ";Survival;1;"
                + plugin.getConfigManager().getBedrockPort()
                + ";19133;";

        buf.writeByte(0x1C);
        buf.writeLong(pingTime);
        buf.writeLong(RakNetServer.SERVER_GUID);
        PacketUtils.writeMagic(buf);

        byte[] nameBytes = serverName.getBytes();
        buf.writeShort(nameBytes.length);
        buf.writeBytes(nameBytes);

        return buf;
    }

    private String color(String s) {
        return s.replace("§", "").replace("&", "");
    }
}
