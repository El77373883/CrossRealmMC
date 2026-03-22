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

    private final CrossRealmMC plugin;
    private Channel channel;
    private NioEventLoopGroup bossGroup;
    private NioEventLoopGroup workerGroup;

    public RakNetServer(CrossRealmMC plugin) {
        this.plugin = plugin;
    }

    public void start(String host, int port) throws Exception {
        bossGroup = new NioEventLoopGroup(1, new DefaultThreadFactory("RakNet Boss"));
        workerGroup = new NioEventLoopGroup(4, new DefaultThreadFactory("RakNet Worker"));

        String motd1 = plugin.getConfigManager().getMotdLine1();
        String motd2 = plugin.getConfigManager().getMotdLine2();
        int maxPlayers = plugin.getConfigManager().getMaxBedrockPlayers();
        long guid = System.currentTimeMillis();

        String advertisement = "MCPE;" + motd1 + ";924;26.3;0;" + maxPlayers + ";"
            + guid + ";" + motd2 + ";Survival;1;" + port + ";19133;1";

        ServerBootstrap bootstrap = new ServerBootstrap()
            .group(bossGroup, workerGroup)
            .channelFactory(RakChannelFactory.server(NioDatagramChannel.class))
            .option(RakChannelOption.RAK_ADVERTISEMENT,
                io.netty.buffer.Unpooled.wrappedBuffer(
                    advertisement.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
            .option(RakChannelOption.RAK_MAX_CONNECTIONS, 100)
            .option(RakChannelOption.RAK_GUID, guid)
            .childHandler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel ch) {
                    ch.pipeline().addLast(new BedrockSessionHandler(plugin));
                }
            });

        channel = bootstrap.bind(new InetSocketAddress(host, port)).sync().channel();
        plugin.log("&a[CrossRealmMC] &fBridge Bedrock iniciado en &e" + host + ":" + port);
    }

    public void stop() {
        if (channel != null) channel.close();
        if (bossGroup != null) bossGroup.shutdownGracefully();
        if (workerGroup != null) workerGroup.shutdownGracefully();
        plugin.log("&c[CrossRealmMC] Bridge Bedrock detenido");
    }
}
