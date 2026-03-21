package me.crossrealmmc.raknet.packets;

import io.netty.buffer.ByteBuf;
import me.crossrealmmc.CrossRealmMC;
import me.crossrealmmc.raknet.RakNetServer;
import org.bukkit.Bukkit;

public class PacketUnconnectedPong {

    private final CrossRealmMC plugin;

    public PacketUnconnectedPong(CrossRealmMC plugin) {
        this.plugin = plugin;
    }

    public ByteBuf encode() {
        ByteBuf buf = PacketUtils.newBuffer();

        String motd1 = color(plugin.getConfigManager().getMotdLine1());
        String motd2 = color(plugin.getConfigManager().getMotdLine2());
        int online = Bukkit.getOnlinePlayers().size();
        int max = Bukkit.getMaxPlayers();
        String serverName = "MCPE;" + motd1 + ";748;26.3;" + online + ";" + max
                + ";" + RakNetServer.SERVER_GUID + ";" + motd2 + ";Survival;1;19132;19133;";

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
