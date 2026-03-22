package me.crossrealmmc.raknet;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.util.concurrent.DefaultThreadFactory;
import me.crossrealmmc.CrossRealmMC;
import org.cloudburstmc.netty.channel.raknet.RakChannelFactory;
import org.cloudburstmc.netty.channel.raknet.config.RakChannelOption;

import java.net.InetSocketAddress;

public class RakNetServer {

    public static final long SERVER_GUID = System.currentTimeMillis();

    private final CrossRealmMC plugin;
    private Channel channel;
    private NioEventLoopGroup bossGroup;
    private NioEventLoopGroup workerGroup;
    private boolean running = false;

    public RakNetServer(CrossRealmMC plugin) {
        this.plugin = plugin;
    }

    public void start() {
        String host = plugin.getConfigManager().getBedrockIp();
        int port = plugin.getConfigManager().getBedrockPort();
        try {
            bossGroup = new NioEventLoopGroup(1, new DefaultThreadFactory("RakNet Boss"));
            workerGroup = new NioEventLoopGroup(4, new DefaultThreadFactory("RakNet Worker"));

            String motd1 = plugin.getConfigManager().getMotdLine1();
            String motd2 = plugin.getConfigManager().getMotdLine2();
            int maxPlayers = plugin.getConfigManager().getMaxBedrockPlayers();

            String advertisement = "MCPE;" + motd1 + ";924;26.3;0;" + maxPlayers + ";"
                + SERVER_GUID + ";" + motd2 + ";Survival;1;" + port + ";19133;1";

            ServerBootstrap bootstrap = new ServerBootstrap()
                .group(bossGroup, workerGroup)
                .channelFactory(RakChannelFactory.server(NioDatagramChannel.class))
                .option(RakChannelOption.RAK_ADVERTISEMENT,
                    io.netty.buffer.Unpooled.wrappedBuffer(
                        advertisement.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .option(RakChannelOption.RAK_MAX_CONNECTIONS, 100)
                .option(RakChannelOption.RAK_GUID, SERVER_GUID)
                .childHandler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) {
                        ch.pipeline().addLast(new BedrockSessionHandler(plugin));
                    }
                });

            channel = bootstrap.bind(new InetSocketAddress(host, port)).sync().channel();
            running = true;
            plugin.log("&aBridge Bedrock iniciado en &e" + host + ":" + port);
        } catch (Exception e) {
            plugin.log("&cError iniciando Bridge Bedrock: &f" + e.getMessage());
        }
    }

    public void stop() {
        running = false;
        if (channel != null) channel.close();
        if (bossGroup != null) bossGroup.shutdownGracefully();
        if (workerGroup != null) workerGroup.shutdownGracefully();
        plugin.log("&cBridge Bedrock detenido");
    }

    public boolean isRunning() { return running; }
}
