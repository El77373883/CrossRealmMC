package me.crossrealmmc.bridge;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import me.crossrealmmc.CrossRealmMC;
import me.crossrealmmc.raknet.RakNetHandler;
import org.bukkit.entity.Player;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class JavaPacketInterceptor {

    private final CrossRealmMC plugin;  // ← CrossRealmMC, no Plugin
    private final ProtocolManager protocolManager;
    private final RakNetHandler rakNetHandler;
    private final Map<UUID, InetSocketAddress> bedrockAddresses = new HashMap<>();

    public JavaPacketInterceptor(CrossRealmMC plugin, RakNetHandler rakNetHandler) {
        this.plugin = plugin;
        this.rakNetHandler = rakNetHandler;
        this.protocolManager = ProtocolLibrary.getProtocolManager();
    }

    public void registerBedrockPlayer(Player player, InetSocketAddress address) {
        bedrockAddresses.put(player.getUniqueId(), address);
        plugin.debugLog("Jugador Bedrock registrado: " + player.getName());
    }

    public void unregisterBedrockPlayer(Player player) {
        bedrockAddresses.remove(player.getUniqueId());
        plugin.debugLog("Jugador Bedrock desregistrado: " + player.getName());
    }

    public void start() {
        protocolManager.addPacketListener(new PacketAdapter(plugin, PacketType.Play.Server.MAP_CHUNK) {
            @Override
            public void onPacketSending(PacketEvent event) {
                Player player = event.getPlayer();
                if (bedrockAddresses.containsKey(player.getUniqueId())) {
                    plugin.debugLog("Chunk para Bedrock: " + player.getName());
                    // Aquí irá la traducción a chunk de Bedrock
                }
            }
        });

        protocolManager.addPacketListener(new PacketAdapter(plugin, PacketType.Play.Server.CHAT) {
            @Override
            public void onPacketSending(PacketEvent event) {
                Player player = event.getPlayer();
                if (bedrockAddresses.containsKey(player.getUniqueId())) {
                    String message = event.getPacket().getStrings().read(0);
                    plugin.debugLog("Chat para Bedrock: " + message);
                    // Aquí irá la traducción a TextPacket de Bedrock
                }
            }
        });

        plugin.log("&aJavaPacketInterceptor iniciado");
    }
}
