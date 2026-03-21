package me.crossrealmmc.log;

import me.crossrealmmc.CrossRealmMC;
import me.crossrealmmc.detection.PlayerDetector;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

public class ConnectionLogger {

    private final CrossRealmMC plugin;
    private BufferedWriter writer;
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AtomicInteger totalBedrock = new AtomicInteger(0);
    private final AtomicInteger totalJava = new AtomicInteger(0);
    private final AtomicInteger antiCheatKicks = new AtomicInteger(0);

    public ConnectionLogger(CrossRealmMC plugin) {
        this.plugin = plugin;
        openWriter();
    }

    private void openWriter() {
        if (!plugin.getConfigManager().isLoggingEnabled()) return;
        try {
            File logFile = new File(plugin.getConfigManager().getLogFile());
            logFile.getParentFile().mkdirs();
            this.writer = new BufferedWriter(new FileWriter(logFile, true));
            writeRaw("=== CrossRealmMC iniciado: " + now() + " ===");
        } catch (IOException e) {
            plugin.getLogger().severe("No se pudo abrir el archivo de logs: " + e.getMessage());
        }
    }

    public void logJoin(String playerName, PlayerDetector.PlayerType type, String ip, String version) {
        if (!plugin.getConfigManager().isLoggingEnabled()) return;
        boolean logBedrock = plugin.getConfigManager().isLogBedrockJoin();
        boolean logJava = plugin.getConfigManager().isLogJavaJoin();

        if (type == PlayerDetector.PlayerType.BEDROCK) {
            totalBedrock.incrementAndGet();
            if (!logBedrock) return;
            writeRaw("[" + now() + "] [JOIN-BEDROCK] " + playerName + " | IP: " + ip + " | Version: " + version);
        } else {
            totalJava.incrementAndGet();
            if (!logJava) return;
            writeRaw("[" + now() + "] [JOIN-JAVA] " + playerName + " | IP: " + ip);
        }
    }

    public void logDisconnect(String playerName, PlayerDetector.PlayerType type, String reason) {
        if (!plugin.getConfigManager().isLoggingEnabled()) return;
        if (!plugin.getConfigManager().isLogDisconnect()) return;
        writeRaw("[" + now() + "] [LEAVE-" + type.name() + "] " + playerName + " | Razon: " + reason);
    }

    public void logAntiCheatKick(String playerName, String reason) {
        antiCheatKicks.incrementAndGet();
        writeRaw("[" + now() + "] [ANTICHEAT-KICK] " + playerName + " | " + reason);
    }

    public void logBan(String playerName, String edition, String by) {
        writeRaw("[" + now() + "] [BAN] " + playerName + " | Edicion: " + edition + " | Por: " + by);
    }

    private void writeRaw(String line) {
        if (writer == null) return;
        try {
            writer.write(line);
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            plugin.getLogger().warning("Error escribiendo log: " + e.getMessage());
        }
    }

    private String now() {
        return LocalDateTime.now().format(formatter);
    }

    public void close() {
        if (writer != null) {
            try {
                writeRaw("=== CrossRealmMC detenido: " + now() + " ===");
                writer.close();
            } catch (IOException ignored) {}
        }
    }

    public int getTotalBedrock() { return totalBedrock.get(); }
    public int getTotalJava() { return totalJava.get(); }
    public int getAntiCheatKicks() { return antiCheatKicks.get(); }
}
