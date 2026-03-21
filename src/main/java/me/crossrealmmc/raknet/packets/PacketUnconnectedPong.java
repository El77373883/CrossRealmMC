package me.crossrealmmc.raknet.packets;

import io.netty.buffer.ByteBuf;
import me.crossrealmmc.CrossRealmMC;
import me.crossrealmmc.raknet.RakNetServer;
import org.bukkit.Bukkit;

public class PacketUnconnectedPong {

    private final CrossRealmMC plugin;

    // Protocolos soportados por version
    private static final int PROTOCOL_26_3 = 924;
    private static final int PROTOCOL_26_2 = 918;
    private static final int PROTOCOL_26_1 = 912;
    private static final int PROTOCOL_26_0 = 904;

    // Usamos el mas nuevo siempre
    private static final int BEDROCK_PROTOCOL = PROTOCOL_26_3;
    private static final String BEDROCK_VERSION = "26.3";

    public PacketUnconnectedPong(CrossRealmMC plugin) {
        this.plugin = plugin;
    }

    public ByteBuf encode() {
        ByteBuf buf = PacketUtils.newBuffer();

        String motd1 = color(plugin.getConfigManager().getMotdLine1());
        String motd2 = color(plugin.getConfigManager().getMotdLine2());
        int online = Bukkit.getOnlinePlayers().size();
        int max = Bukkit.getMaxPlayers();

        // Formato oficial MCPE MOTD
        String serverName = "MCPE"
                + ";" + motd1
                + ";" + BEDROCK_PROTOCOL
                + ";" + BEDROCK_VERSION
                + ";" + online
                + ";" + max
                + ";" + RakNetServer.SERVER_GUID
                + ";" + motd2
                + ";Survival;1;19132;19133;";

        buf.writeByte(0x1C); // ID_UNCONNECTED_PONG
        buf.writeLong(System.currentTimeMillis());
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
