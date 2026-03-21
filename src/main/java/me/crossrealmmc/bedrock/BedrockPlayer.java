package me.crossrealmmc.bedrock;

import java.net.InetSocketAddress;
import java.util.UUID;

public class BedrockPlayer {

    public enum State {
        HANDSHAKING, LOGIN, ENCRYPTING, SPAWNING, PLAYING
    }

    private final InetSocketAddress address;
    private String username;
    private String xuid;
    private UUID uuid;
    private State state = State.HANDSHAKING;
    private String bedrockVersion;
    private int protocol;
    private long entityId;
    private float x, y, z;
    private float yaw, pitch;

    public BedrockPlayer(InetSocketAddress address) {
        this.address = address;
        this.entityId = (long)(Math.random() * 100000);
    }

    public InetSocketAddress getAddress() { return address; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getXuid() { return xuid; }
    public void setXuid(String xuid) { this.xuid = xuid; }
    public UUID getUuid() { return uuid; }
    public void setUuid(UUID uuid) { this.uuid = uuid; }
    public State getState() { return state; }
    public void setState(State state) { this.state = state; }
    public String getBedrockVersion() { return bedrockVersion; }
    public void setBedrockVersion(String bedrockVersion) { this.bedrockVersion = bedrockVersion; }
    public int getProtocol() { return protocol; }
    public void setProtocol(int protocol) { this.protocol = protocol; }
    public long getEntityId() { return entityId; }
    public float getX() { return x; }
    public float getY() { return y; }
    public float getZ() { return z; }
    public void setPosition(float x, float y, float z) { this.x = x; this.y = y; this.z = z; }
    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }
    public void setRotation(float yaw, float pitch) { this.yaw = yaw; this.pitch = pitch; }
}
