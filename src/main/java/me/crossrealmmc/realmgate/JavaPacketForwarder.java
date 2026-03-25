package me.crossrealmmc.realmgate;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import me.crossrealmmc.CrossRealmMC;
import me.crossrealmmc.raknet.RakNetHandler;

import java.net.InetSocketAddress;

public class JavaPacketForwarder {

    private final CrossRealmMC plugin;
    private final RakNetHandler rakNetHandler;

    public JavaPacketForwarder(CrossRealmMC plugin, RakNetHandler rakNetHandler) {
        this.plugin = plugin;
        this.rakNetHandler = rakNetHandler;
    }

    /**
     * Recibe un paquete del servidor Java y lo traduce a Bedrock
     */
    public void forwardToBedrock(ByteBuf javaPacket, BedrockSession session, InetSocketAddress bedrockAddress) {
        if (javaPacket == null || !javaPacket.isReadable()) return;

        int packetId = readVarInt(javaPacket);
        plugin.debugLog("Paquete Java → Bedrock: 0x" + String.format("%02X", packetId) + " para " + session.getUsername());

        switch (packetId) {
            case 0x26: // ChunkData (Java) → LevelChunkPacket (Bedrock)
                handleChunkData(javaPacket, session, bedrockAddress);
                break;

            case 0x1F: // AddEntity (Java) → AddEntityPacket (Bedrock)
                handleAddEntity(javaPacket, session, bedrockAddress);
                break;

            case 0x24: // AddPlayer (Java) → AddPlayerPacket (Bedrock)
                handleAddPlayer(javaPacket, session, bedrockAddress);
                break;

            case 0x2C: // UpdateBlock (Java) → UpdateBlockPacket (Bedrock)
                handleUpdateBlock(javaPacket, session, bedrockAddress);
                break;

            case 0x0F: // SetTime (Java) → SetTimePacket (Bedrock)
                handleSetTime(javaPacket, session, bedrockAddress);
                break;

            case 0x4A: // SetHealth (Java) → SetHealthPacket (Bedrock)
                handleSetHealth(javaPacket, session, bedrockAddress);
                break;

            case 0x32: // ChatMessage (Java) → TextPacket (Bedrock)
                handleChatMessage(javaPacket, session, bedrockAddress);
                break;

            default:
                plugin.debugLog("Paquete Java no traducido: 0x" + String.format("%02X", packetId));
                break;
        }
    }

    private void handleChunkData(ByteBuf buf, BedrockSession session, InetSocketAddress address) {
        try {
            // Leer chunk desde Java
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
            
            sendToBedrock(bedrockPacket, address);
            
        } catch (Exception e) {
            plugin.debugLog("Error traduciendo chunk: " + e.getMessage());
        }
    }

    private void handleAddEntity(ByteBuf buf, BedrockSession session, InetSocketAddress address) {
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
            
            sendToBedrock(bedrockPacket, address);
            
        } catch (Exception e) {
            plugin.debugLog("Error traduciendo entidad: " + e.getMessage());
        }
    }

    private void handleAddPlayer(ByteBuf buf, BedrockSession session, InetSocketAddress address) {
        try {
            int entityId = buf.readInt();
            long uuidMsb = buf.readLong();
            long uuidLsb = buf.readLong();
            double x = buf.readDouble();
            double y = buf.readDouble();
            double z = buf.readDouble();
            
            plugin.debugLog("Jugador añadido: id=" + entityId);
            
            // Traducción simple
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
            
            sendToBedrock(bedrockPacket, address);
            
        } catch (Exception e) {
            plugin.debugLog("Error traduciendo jugador: " + e.getMessage());
        }
    }

    private void handleUpdateBlock(ByteBuf buf, BedrockSession session, InetSocketAddress address) {
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
            
            sendToBedrock(bedrockPacket, address);
            
        } catch (Exception e) {
            plugin.debugLog("Error traduciendo bloque: " + e.getMessage());
        }
    }

    private void handleSetTime(ByteBuf buf, BedrockSession session, InetSocketAddress address) {
        try {
            long worldTime = buf.readLong();
            plugin.debugLog("Tiempo actualizado: " + worldTime);
            
            ByteBuf bedrockPacket = Unpooled.buffer();
            writeVarInt(bedrockPacket, 0x1C); // SetTimePacket
            writeVarInt(bedrockPacket, (int) worldTime);
            
            sendToBedrock(bedrockPacket, address);
            
        } catch (Exception e) {
            plugin.debugLog("Error traduciendo tiempo: " + e.getMessage());
        }
    }

    private void handleSetHealth(ByteBuf buf, BedrockSession session, InetSocketAddress address) {
        try {
            float health = buf.readFloat();
            plugin.debugLog("Salud actualizada: " + health);
            
            ByteBuf bedrockPacket = Unpooled.buffer();
            writeVarInt(bedrockPacket, 0x3C); // SetHealthPacket
            writeVarInt(bedrockPacket, (int) health);
            
            sendToBedrock(bedrockPacket, address);
            
        } catch (Exception e) {
            plugin.debugLog("Error traduciendo salud: " + e.getMessage());
        }
    }

    private void handleChatMessage(ByteBuf buf, BedrockSession session, InetSocketAddress address) {
        try {
            String message = readJavaString(buf);
            plugin.debugLog("Chat: " + message);
            
            ByteBuf bedrockPacket = Unpooled.buffer();
            writeVarInt(bedrockPacket, 0x09); // TextPacket
            writeByte(bedrockPacket, 1); // type = CHAT
            writeString(bedrockPacket, message);
            writeString(bedrockPacket, "");
            writeString(bedrockPacket, "");
            
            sendToBedrock(bedrockPacket, address);
            
        } catch (Exception e) {
            plugin.debugLog("Error traduciendo chat: " + e.getMessage());
        }
    }

    private void sendToBedrock(ByteBuf packet, InetSocketAddress address) {
        if (rakNetHandler != null && packet.isReadable()) {
            rakNetHandler.sendFrameSetOrdered(null, address, packet);
        } else {
            packet.release();
        }
    }

    // Utilidades de lectura/escritura (igual que en PacketTranslator)
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

    private void writeByte(ByteBuf buf, int value) {
        buf.writeByte(value);
    }
}
