package me.crossrealmmc.bridge;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.UUID;

public class VirtualPlayerCreator {

    public static Player createVirtualPlayer(UUID uuid, String username, InetSocketAddress address) {
        try {
            // Obtener el servidor CraftServer
            Object craftServer = Bukkit.getServer();
            
            // Obtener MinecraftServer a través de reflection
            Method getServer = craftServer.getClass().getMethod("getServer");
            Object minecraftServer = getServer.invoke(craftServer);
            
            // Obtener el overworld (mundo principal)
            Method getOverworld = minecraftServer.getClass().getMethod("overworld");
            Object overworld = getOverworld.invoke(minecraftServer);
            
            // Crear GameProfile
            Class<?> gameProfileClass = Class.forName("com.mojang.authlib.GameProfile");
            Constructor<?> gameProfileConstructor = gameProfileClass.getConstructor(UUID.class, String.class);
            Object gameProfile = gameProfileConstructor.newInstance(uuid, username);
            
            // Crear ServerPlayer
            Class<?> serverPlayerClass = Class.forName("net.minecraft.server.level.ServerPlayer");
            Constructor<?> serverPlayerConstructor = serverPlayerClass.getConstructor(
                Class.forName("net.minecraft.server.MinecraftServer"),
                Class.forName("net.minecraft.server.level.ServerLevel"),
                gameProfileClass
            );
            Object serverPlayer = serverPlayerConstructor.newInstance(minecraftServer, overworld, gameProfile);
            
            // Crear conexión virtual (null para desconectado)
            Class<?> packetListenerClass = Class.forName("net.minecraft.server.network.ServerGamePacketListenerImpl");
            Constructor<?> packetListenerConstructor = packetListenerClass.getConstructor(
                Class.forName("net.minecraft.server.MinecraftServer"),
                Class.forName("net.minecraft.network.Connection"),
                serverPlayerClass
            );
            Object connection = packetListenerConstructor.newInstance(minecraftServer, null, serverPlayer);
            
            // Asignar la conexión al ServerPlayer
            Method setConnection = serverPlayerClass.getMethod("a", packetListenerClass);
            setConnection.invoke(serverPlayer, connection);
            
            // Añadir al servidor
            Method addPlayer = craftServer.getClass().getMethod("addPlayer", serverPlayerClass.getSuperclass());
            addPlayer.invoke(craftServer, serverPlayer);
            
            // Obtener el Player de Bukkit
            Method getBukkitEntity = serverPlayerClass.getMethod("getBukkitEntity");
            return (Player) getBukkitEntity.invoke(serverPlayer);
            
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
