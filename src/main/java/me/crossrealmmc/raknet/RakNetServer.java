package me.crossrealmmc.raknet;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import me.crossrealmmc.CrossRealmMC;
import org.bukkit.Bukkit;

public class RakNetServer {

    private final CrossRealmMC plugin;
    private Channel channel;
    private NioEventLoopGroup group;
    private boolean running = false;

    // GUID unico del servidor
    public static final long SERVER_GUID = System.currentTimeMillis();

    public RakNetServer(CrossRealmMC plugin) {
        this.plugin = plugin;
    }

    public void start() {
        String ip = plugin.getConfigManager().getBedrockIp();
        int port = plugin.getConfigManager().getBedrockPort();

        group = new NioEventLoopGroup();
        try {
            Bootstrap bootstrap = new Bootstrap()
                    .group(group)
                    .channel(NioDatagramChannel.class)
                    .option(ChannelOption.SO_BROADCAST, true)
                    .option(ChannelOption.SO_RCVBUF, 1024 * 1024 * 4)
                    .option(ChannelOption.SO_SNDBUF, 1024 * 1024 * 4)
                    .handler(new ChannelInitializer<NioDatagramChannel>() {
                        @Override
                        protected void initChannel(NioDatagramChannel ch) {
                            ch.pipeline().addLast(new RakNetHandler(plugin));
                        }
                    });

            ChannelFuture future = bootstrap.bind(ip, port).sync();
            channel = future.channel();
            running = true;
            plugin.log(plugin.getConfigManager().getMessage("bridge-started",
                    "{port}", String.valueOf(port)));
            plugin.debugLog("RakNet UDP escuchando en " + ip + ":" + port);

        } catch (Exception e) {
            running = false;
            plugin.log(plugin.getConfigManager().getMessage("bridge-failed",
                    "{port}", String.valueOf(port)));
            plugin.getLogger().severe("Error RakNet: " + e.getMessage());

            // Auto-restart si esta activado
            if (plugin.getConfigManager().isAutoRestartEnabled()) {
                int delay = plugin.getConfigManager().getAutoRestartDelay();
                plugin.debugLog("Intentando reiniciar bridge en " + delay + " segundos...");
                Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
                    stop();
                    start();
                    plugin.log(plugin.getConfigManager().getMessage("bridge-restarted"));
                }, delay * 20L);
            }
        }
    }

    public void stop() {
        running = false;
        if (channel != null) {
            try { channel.close().sync(); } catch (Exception ignored) {}
        }
        if (group != null) {
            group.shutdownGracefully();
        }
        plugin.debugLog("RakNet server detenido.");
    }

    public boolean isRunning() { return running; }
}
