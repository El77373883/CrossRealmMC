package me.crossrealmmc.realmgate;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import me.crossrealmmc.CrossRealmMC;
import me.crossrealmmc.raknet.RakNetHandler;

import java.net.InetSocketAddress;

public class JavaPacketForwarder {

    private final CrossRealmMC plugin;
    private final RakNetHandler rakNetHandler;
    private final InetSocketAddress bedrockAddress;

    public JavaPacketForwarder(CrossRealmMC plugin, RakNetHandler rakNetHandler, InetSocketAddress bedrockAddress) {
        this.plugin = plugin;
        this.rakNetHandler = rakNetHandler;
        this.bedrockAddress = bedrockAddress;
    }

    public void forwardToBedrock(int packetId, ByteBuf javaPacket) {
        plugin.debugLog("Traduciendo paquete Java: 0x" + String.format("%02X", packetId) + " para " + bedrockAddress);

        if (rakNetHandler == null || bedrockAddress == null) {
            plugin.debugLog("No se puede traducir: rakNetHandler o bedrockAddress es null");
            return;
        }

        switch (packetId) {
            case 0x26: // ChunkData (Java) → LevelChunkPacket (Bedrock)
                handleChunkData(javaPacket);
                break;
            case 0x1F: // AddEntity (Java) → AddEntityPacket (Bedrock)
                handleAddEntity(javaPacket);
                break;
            case 0x24: // AddPlayer (Java) → AddPlayerPacket (Bedrock)
                handleAddPlayer(javaPacket);
                break;
            case 0x2C: // UpdateBlock (Java) → UpdateBlockPacket (Bedrock)
                handleUpdateBlock(javaPacket);
                break;
            case 0x0F: // SetTime (Java) → SetTimePacket (Bedrock)
                handleSetTime(javaPacket);
                break;
            case 0x4A: // SetHealth (Java) → SetHealthPacket (Bedrock)
                handleSetHealth(javaPacket);
                break;
            case 0x32: // ChatMessage (Java) → TextPacket (Bedrock)
                handleChatMessage(javaPacket);
                break;
            default:
                plugin.debugLog("Paquete Java no traducido aún: 0x" + String.format("%02X", packetId));
                break;
        }
    }

    private void handleChunkData(ByteBuf buf) {
        try {
            int chunkX = buf.readInt();
            int chunkZ = buf.readInt();
            boolean fullChunk = buf.readBoolean();
            int dataSize = readVarInt(buf);
            
            byte[] chunkData = new byte[dataSize];
            buf.readBytes(chunkData);
            
            plugin.debugLog("Chunk recibido: " + chunkX + "," + chunkZ + " size=" + dataSize);
            
            // Construir LevelChunkPacket para Bedrock
            ByteBuf bedrockPacket = Unpooled.buffer();
            writeVarInt(bedrockPacket, 0x3A); // LevelChunkPacket ID
            writeZigZagInt(bedrockPacket, chunkX);
            writeZigZagInt(bedrockPacket, chunkZ);
            writeVarInt(bedrockPacket, 0); // subChunkCount
            bedrockPacket.writeBoolean(false); // cache enabled
            writeVarInt(bedrockPacket, 0); // blob count
            writeVarInt(bedrockPacket, dataSize); // data length
            bedrockPacket.writeBytes(chunkData);
            
            sendToBedrock(bedrockPacket);
            
        } catch (Exception e) {
            plugin.debugLog("Error traduciendo chunk: " + e.getMessage());
        }
    }

    private void handleAddEntity(ByteBuf buf) {
        try {
            int entityId = buf.readInt();
            long uuidMsb = buf.readLong();
            long uuidLsb = buf.readLong();
            int type = buf.readInt();
            double x = buf.readDouble();
            double y = buf.readDouble();
            double z = buf.readDouble();
            
            plugin.debugLog("Entidad añadida: id=" + entityId + " type=" + type);
            
            // Traducción simple: enviar posición
            ByteBuf bedrockPacket = Unpooled.buffer();
            writeVarInt(bedrockPacket, 0x1C); // MovePlayerPacket
            writeVarLong(bedrockPacket, entityId);
            bedrockPacket.writeFloatLE((float) x);
            bedrockPacket.writeFloatLE((float) y);
            bedrockPacket.writeFloatLE((float) z);
            bedrockPacket.writeFloatLE(0);
            bedrockPacket.writeFloatLE(0);
            bedrockPacket.writeByte(0);
            writeVarInt(bedrockPacket, 0);
            
            sendToBedrock(bedrockPacket);
            
        } catch (Exception e) {
            plugin.debugLog("Error traduciendo entidad: " + e.getMessage());
        }
    }

    private void handleAddPlayer(ByteBuf buf) {
        try {
            int entityId = buf.readInt();
            long uuidMsb = buf.readLong();
            long uuidLsb = buf.readLong();
            double x = buf.readDouble();
            double y = buf.readDouble();
            double z = buf.readDouble();
            
            plugin.debugLog("Jugador añadido: id=" + entityId);
            
            ByteBuf bedrockPacket = Unpooled.buffer();
            writeVarInt(bedrockPacket, 0x1C); // MovePlayerPacket
            writeVarLong(bedrockPacket, entityId);
            bedrockPacket.writeFloatLE((float) x);
            bedrockPacket.writeFloatLE((float) y);
            bedrockPacket.writeFloatLE((float) z);
            bedrockPacket.writeFloatLE(0);
            bedrockPacket.writeFloatLE(0);
            bedrockPacket.writeByte(0);
            writeVarInt(bedrockPacket, 0);
            
            sendToBedrock(bedrockPacket);
            
        } catch (Exception e) {
            plugin.debugLog("Error traduciendo jugador: " + e.getMessage());
        }
    }

    private void handleUpdateBlock(ByteBuf buf) {
        try {
            int x = buf.readInt();
            int y = buf.readInt();
            int z = buf.readInt();
            int blockState = readVarInt(buf);
            
            plugin.debugLog("Bloque actualizado: " + x + "," + y + "," + z);
            
            ByteBuf bedrockPacket = Unpooled.buffer();
            writeVarInt(bedrockPacket, 0x21); // UpdateBlockPacket
            writeZigZagInt(bedrockPacket, x);
            writeZigZagInt(bedrockPacket, y);
            writeZigZagInt(bedrockPacket, z);
            writeVarInt(bedrockPacket, 1); // blockRuntimeId
            writeVarInt(bedrockPacket, 0); // flags
            
            sendToBedrock(bedrockPacket);
            
        } catch (Exception e) {
            plugin.debugLog("Error traduciendo bloque: " + e.getMessage());
        }
    }

    private void handleSetTime(ByteBuf buf) {
        try {
            long worldTime = buf.readLong();
            plugin.debugLog("Tiempo actualizado: " + worldTime);
            
            ByteBuf bedrockPacket = Unpooled.buffer();
            writeVarInt(bedrockPacket, 0x1C); // SetTimePacket
            writeVarInt(bedrockPacket, (int) worldTime);
            
            sendToBedrock(bedrockPacket);
            
        } catch (Exception e) {
            plugin.debugLog("Error traduciendo tiempo: " + e.getMessage());
        }
    }

    private void handleSetHealth(ByteBuf buf) {
        try {
            float health = buf.readFloat();
            plugin.debugLog("Salud actualizada: " + health);
            
            ByteBuf bedrockPacket = Unpooled.buffer();
            writeVarInt(bedrockPacket, 0x3C); // SetHealthPacket
            writeVarInt(bedrockPacket, (int) health);
            
            sendToBedrock(bedrockPacket);
            
        } catch (Exception e) {
            plugin.debugLog("Error traduciendo salud: " + e.getMessage());
        }
    }

    private void handleChatMessage(ByteBuf buf) {
        try {
            String message = readJavaString(buf);
            plugin.debugLog("Chat: " + message);
            
            ByteBuf bedrockPacket = Unpooled.buffer();
            writeVarInt(bedrockPacket, 0x09); // TextPacket
            bedrockPacket.writeByte(1); // type = CHAT
            writeString(bedrockPacket, message);
            writeString(bedrockPacket, "");
            writeString(bedrockPacket, "");
            
            sendToBedrock(bedrockPacket);
            
        } catch (Exception e) {
            plugin.debugLog("Error traduciendo chat: " + e.getMessage());
        }
    }

    private void sendToBedrock(ByteBuf packet) {
        if (rakNetHandler != null && bedrockAddress != null && packet.isReadable()) {
            rakNetHandler.sendFrameSetOrdered(null, bedrockAddress, packet);
        } else {
            packet.release();
        }
    }

    // Utilidades
    private int readVarInt(ByteBuf buf) {
        int value = 0;
        int size = 0;
        int b;
        while (((b = buf.readByte()) & 0x80) == 0x80) {
            value |= (b & 0x7F) << (size++ * 7);
        }
        return value | ((b & 0x7F) << (size * 7));
    }

    private String readJavaString(ByteBuf buf) {
        int len = readVarInt(buf);
        byte[] bytes = new byte[len];
        buf.readBytes(bytes);
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }

    private void writeVarInt(ByteBuf buf, int value) {
        while ((value & ~0x7F) != 0) {
            buf.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        buf.writeByte(value);
    }

    private void writeZigZagInt(ByteBuf buf, int value) {
        writeVarInt(buf, (value << 1) ^ (value >> 31));
    }

    private void writeVarLong(ByteBuf buf, long value) {
        while ((value & ~0x7FL) != 0) {
            buf.writeByte((int) ((value & 0x7F) | 0x80));
            value >>>= 7;
        }
        buf.writeByte((int) value);
    }

    private void writeString(ByteBuf buf, String str) {
        byte[] bytes = str.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        writeVarInt(buf, bytes.length);
        buf.writeBytes(bytes);
    }
}
