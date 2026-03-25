package me.crossrealmmc.bridge;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import me.crossrealmmc.CrossRealmMC;
import me.crossrealmmc.raknet.RakNetHandler;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.Set;

public class JavaPacketInterceptor {

    private final CrossRealmMC plugin;
    private final ProtocolManager protocolManager;
    private final RakNetHandler rakNetHandler;
    private final Set<String> bedrockPlayers = new HashSet<>();

    public JavaPacketInterceptor(CrossRealmMC plugin, RakNetHandler rakNetHandler) {
        this.plugin = plugin;
        this.rakNetHandler = rakNetHandler;
        this.protocolManager = ProtocolLibrary.getProtocolManager();
    }

    public void registerBedrockPlayer(String playerName, InetSocketAddress address) {
        bedrockPlayers.add(playerName);
        plugin.debugLog("Jugador Bedrock registrado: " + playerName);
    }

    public void unregisterBedrockPlayer(String playerName) {
        bedrockPlayers.remove(playerName);
        plugin.debugLog("Jugador Bedrock desregistrado: " + playerName);
    }

    public void start() {
        final CrossRealmMC pluginInstance = this.plugin;
        Plugin pluginRef = this.plugin;

        protocolManager.addPacketListener(new PacketAdapter(pluginRef, PacketType.Play.Server.MAP_CHUNK) {
            @Override
            public void onPacketSending(PacketEvent event) {
                Player player = event.getPlayer();
                if (bedrockPlayers.contains(player.getName())) {
                    pluginInstance.debugLog("Chunk para Bedrock: " + player.getName());
                    // Aquí irá la traducción a LevelChunkPacket de Bedrock
                }
            }
        });

        protocolManager.addPacketListener(new PacketAdapter(pluginRef, PacketType.Play.Server.CHAT) {
            @Override
            public void onPacketSending(PacketEvent event) {
                Player player = event.getPlayer();
                if (bedrockPlayers.contains(player.getName())) {
                    String message = event.getPacket().getStrings().read(0);
                    pluginInstance.debugLog("Chat para Bedrock: " + message);
                    // Aquí irá la traducción a TextPacket de Bedrock
                }
            }
        });

        plugin.log("&aJavaPacketInterceptor iniciado");
    }
}
