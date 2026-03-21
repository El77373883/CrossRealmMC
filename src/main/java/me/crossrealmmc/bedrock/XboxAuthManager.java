package me.crossrealmmc.bedrock;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

/**
 * XboxAuthManager — Maneja autenticacion Xbox Live para Bedrock
 * Parsea el JWT chain del LoginPacket
 * Hecho por soyadrianyt001
 */
public class XboxAuthManager {

    public static class AuthResult {
        public boolean authenticated;
        public String username;
        public String xuid;
        public UUID uuid;
        public String errorMessage;
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

            // Parsear el ultimo JWT del chain que contiene los datos del jugador
            String lastJwt = chain.get(chain.size() - 1).getAsString();
            JsonObject payload = decodeJwtPayload(lastJwt);

            if (payload == null) {
                result.authenticated = false;
                result.errorMessage = "No se pudo decodificar JWT";
                return result;
            }

            // Extraer extraData del payload
            JsonObject extraData = payload.has("extraData")
                    ? payload.getAsJsonObject("extraData") : null;

            if (extraData != null) {
                result.username = extraData.has("displayName")
                        ? extraData.get("displayName").getAsString() : "BedrockPlayer";
                result.xuid = extraData.has("XUID")
                        ? extraData.get("XUID").getAsString() : "";

                String identityPublicKey = extraData.has("identity")
                        ? extraData.get("identity").getAsString() : "";

                // Si tiene XUID valido = cuenta Xbox real
                if (onlineMode && (result.xuid == null || result.xuid.isEmpty())) {
                    result.authenticated = false;
                    result.errorMessage = "Se requiere cuenta Xbox Live";
                    return result;
                }
            } else {
                // Sin extraData = modo offline
                result.username = "BedrockPlayer_" + (int)(Math.random() * 9999);
                result.xuid = "";
            }

            // Generar UUID
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
