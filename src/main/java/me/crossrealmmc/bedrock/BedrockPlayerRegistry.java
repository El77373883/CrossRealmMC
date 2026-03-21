package me.crossrealmmc.bedrock;

import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BedrockPlayerRegistry {

    private final Map<String, BedrockPlayer> players = new ConcurrentHashMap<>();

    public BedrockPlayer getOrCreate(InetSocketAddress address) {
        String key = key(address);
        return players.computeIfAbsent(key, k -> new BedrockPlayer(address));
    }

    public BedrockPlayer get(InetSocketAddress address) {
        return players.get(key(address));
    }

    public void remove(InetSocketAddress address) {
        players.remove(key(address));
    }

    public Collection<BedrockPlayer> getAll() {
        return players.values();
    }

    public int count() {
        return players.size();
    }

    private String key(InetSocketAddress address) {
        return address.getAddress().getHostAddress() + ":" + address.getPort();
    }
}
