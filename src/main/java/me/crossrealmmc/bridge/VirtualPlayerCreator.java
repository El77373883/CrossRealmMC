package me.crossrealmmc.bridge;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_21_R1.CraftServer;
import org.bukkit.entity.Player;

import java.net.InetSocketAddress;
import java.util.UUID;

public class VirtualPlayerCreator {

    public static Player createVirtualPlayer(UUID uuid, String username, InetSocketAddress address) {
        try {
            CraftServer server = (CraftServer) Bukkit.getServer();
            MinecraftServer nmsServer = server.getServer();
            
            GameProfile profile = new GameProfile(uuid, username);
            
            ServerPlayer serverPlayer = new ServerPlayer(
                nmsServer,
                nmsServer.overworld(),
                profile
            );
            
            serverPlayer.connection = new ServerGamePacketListenerImpl(
                nmsServer,
                null,
                serverPlayer
            );
            
            server.addPlayer(serverPlayer.getBukkitEntity());
            
            return serverPlayer.getBukkitEntity();
            
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
