package me.crossrealmmc.realmgate;

import java.net.InetSocketAddress;
import java.util.UUID;

public class BedrockSession {

    private final InetSocketAddress address;
    private final String bedrockVersion;
    private String username;
    private String xuid;
    private UUID uuid;
    private boolean authenticated = false;
    private final long createdAt;

    public BedrockSession(InetSocketAddress address, String bedrockVersion) {
        this.address = address;
        this.bedrockVersion = bedrockVersion;
        this.createdAt = System.currentTimeMillis();
    }

    public InetSocketAddress getAddress() { return address; }
    public String getBedrockVersion() { return bedrockVersion; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getXuid() { return xuid; }
    public void setXuid(String xuid) { this.xuid = xuid; }
    public UUID getUuid() { return uuid; }
    public void setUuid(UUID uuid) { this.uuid = uuid; }
    public boolean isAuthenticated() { return authenticated; }
    public void setAuthenticated(boolean authenticated) { this.authenticated = authenticated; }
    public long getCreatedAt() { return createdAt; }

    @Override
    public String toString() {
        return "BedrockSession{" +
                "ip=" + address.getAddress().getHostAddress() +
                ", version=" + bedrockVersion +
                ", username=" + username +
                ", authenticated=" + authenticated +
                '}';
    }
}
