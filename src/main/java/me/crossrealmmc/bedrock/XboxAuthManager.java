package me.crossrealmmc.bedrock;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

public class XboxAuthManager {

    public static class AuthResult {
        public boolean authenticated;
        public String username;
        public String xuid;
        public UUID uuid;
        public String errorMessage;
    }

    public static class PlayerInfo {
        public String username;
        public String xuid;
        public UUID uuid;
        public boolean success;
    }

    public AuthResult authenticate(String jwtChainJson, boolean onlineMode) {
        AuthResult result = new AuthResult();

        try {
            JsonObject root = JsonParser.parseString(jwtChainJson).getAsJsonObject();
            JsonArray chain = root.getAsJsonArray("chain");

            if (chain == null || chain.size() == 0) {
                result.authenticated = false;
                result.errorMessage = "JWT chain vacia";
                return result;
            }

            String lastJwt = chain.get(chain.size() - 1).getAsString();
            JsonObject payload = decodeJwtPayload(lastJwt);

            if (payload == null) {
                result.authenticated = false;
                result.errorMessage = "No se pudo decodificar JWT";
                return result;
            }

            JsonObject extraData = payload.has("extraData")
                    ? payload.getAsJsonObject("extraData") : null;

            if (extraData != null) {
                result.username = extraData.has("displayName")
                        ? extraData.get("displayName").getAsString() : "BedrockPlayer";
                result.xuid = extraData.has("XUID")
                        ? extraData.get("XUID").getAsString() : "";

                if (onlineMode && (result.xuid == null || result.xuid.isEmpty())) {
                    result.authenticated = false;
                    result.errorMessage = "Se requiere cuenta Xbox Live";
                    return result;
                }
            } else {
                result.username = "BedrockPlayer_" + (int)(Math.random() * 9999);
                result.xuid = "";
            }

            if (result.xuid != null && !result.xuid.isEmpty()) {
                result.uuid = UUID.nameUUIDFromBytes(("bedrock:" + result.xuid).getBytes(StandardCharsets.UTF_8));
            } else {
                result.uuid = UUID.nameUUIDFromBytes(("bedrock_offline:" + result.username).getBytes(StandardCharsets.UTF_8));
            }

            result.authenticated = true;
            return result;

        } catch (Exception e) {
            result.authenticated = false;
            result.errorMessage = "Error parseando JWT: " + e.getMessage();
            return result;
        }
    }

    public PlayerInfo extractFromJwt(String jwtChainJson) {
        PlayerInfo info = new PlayerInfo();
        try {
            // LOG 1: Ver qué llega
            System.out.println("[XboxAuth] JWT recibido: " + 
                (jwtChainJson.length() > 300 ? jwtChainJson.substring(0, 300) + "..." : jwtChainJson));
            
            JsonObject root = JsonParser.parseString(jwtChainJson).getAsJsonObject();
            JsonArray chain = root.getAsJsonArray("chain");
            
            System.out.println("[XboxAuth] Chain size: " + (chain != null ? chain.size() : 0));
            
            if (chain == null || chain.size() == 0) {
                info.success = false;
                return info;
            }
            
            String lastJwt = chain.get(chain.size() - 1).getAsString();
            System.out.println("[XboxAuth] Last JWT (primeros 200): " + 
                (lastJwt.length() > 200 ? lastJwt.substring(0, 200) : lastJwt));
            
            JsonObject payload = decodeJwtPayload(lastJwt);
            
            if (payload == null) {
                System.out.println("[XboxAuth] Payload es null");
                info.success = false;
                return info;
            }
            
            System.out.println("[XboxAuth] Payload decodificado: " + payload.toString());
            
            JsonObject extraData = payload.has("extraData") ? payload.getAsJsonObject("extraData") : null;
            
            if (extraData != null) {
                info.username = extraData.has("displayName") ? extraData.get("displayName").getAsString() : null;
                info.xuid = extraData.has("XUID") ? extraData.get("XUID").getAsString() : null;
                System.out.println("[XboxAuth] Username encontrado: " + info.username);
                System.out.println("[XboxAuth] XUID encontrado: " + info.xuid);
            } else {
                // Si no hay extraData, buscar directamente en el payload
                if (payload.has("displayName")) {
                    info.username = payload.get("displayName").getAsString();
                    System.out.println("[XboxAuth] Username en payload: " + info.username);
                }
                if (payload.has("XUID")) {
                    info.xuid = payload.get("XUID").getAsString();
                    System.out.println("[XboxAuth] XUID en payload: " + info.xuid);
                }
            }
            
            info.success = (info.username != null && !info.username.isEmpty());
            System.out.println("[XboxAuth] Success: " + info.success);
            return info;
            
        } catch (Exception e) {
            System.out.println("[XboxAuth] Error: " + e.getMessage());
            e.printStackTrace();
            info.success = false;
            return info;
        }
    }

    private JsonObject decodeJwtPayload(String jwt) {
        try {
            String[] parts = jwt.split("\\.");
            if (parts.length < 2) return null;
            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
            String payloadJson = new String(payloadBytes, StandardCharsets.UTF_8);
            return JsonParser.parseString(payloadJson).getAsJsonObject();
        } catch (Exception e) {
            return null;
        }
    }
}
