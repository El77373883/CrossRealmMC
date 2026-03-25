package me.crossrealmmc.raknet;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.util.concurrent.DefaultThreadFactory;
import me.crossrealmmc.CrossRealmMC;

import java.net.InetSocketAddress;

public class RakNetServer {

    public static final long SERVER_GUID = System.currentTimeMillis();

    private final CrossRealmMC plugin;
    private Channel channel;
    private NioEventLoopGroup group;
    private RakNetHandler handler;
    private boolean running = false;

    public RakNetServer(CrossRealmMC plugin) {
        this.plugin = plugin;
    }

    public void start() {
        String host = plugin.getConfigManager().getBedrockAddress();
        int port = plugin.getConfigManager().getBedrockPort();
        try {
            group = new NioEventLoopGroup(4, new DefaultThreadFactory("RakNet"));
            
            // ✅ Crear handler ANTES de iniciar el bootstrap
            this.handler = new RakNetHandler(plugin);
            
            Bootstrap bootstrap = new Bootstrap()
                .group(group)
                .channel(NioDatagramChannel.class)
                .option(ChannelOption.SO_BROADCAST, true)
                .option(ChannelOption.SO_RCVBUF, 1024 * 1024)
                .option(ChannelOption.SO_SNDBUF, 1024 * 1024)
                .handler(new ChannelInitializer<NioDatagramChannel>() {
                    @Override
                    protected void initChannel(NioDatagramChannel ch) {
                        ch.pipeline().addLast(handler);
                    }
                });

            channel = bootstrap.bind(new InetSocketAddress(host, port)).sync().channel();
            running = true;
            plugin.log("&aBridge Bedrock iniciado en &e" + host + ":" + port);

        } catch (Exception e) {
            plugin.log("&cError iniciando Bridge Bedrock: &f" + e.getMessage());
            e.printStackTrace();
        }
    }

    public void stop() {
        running = false;
        if (channel != null) channel.close();
        if (group != null) group.shutdownGracefully();
        plugin.log("&cBridge Bedrock detenido");
    }

    public boolean isRunning() { return running; }
    public RakNetHandler getHandler() { return handler; }
}
