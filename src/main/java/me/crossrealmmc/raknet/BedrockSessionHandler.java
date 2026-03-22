package me.crossrealmmc.raknet;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import me.crossrealmmc.CrossRealmMC;
import me.crossrealmmc.bedrock.*;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;

public class BedrockSessionHandler extends SimpleChannelInboundHandler<ByteBuf> {

    private final CrossRealmMC plugin;
    private final AtomicInteger sendSequence = new AtomicInteger(0);
    private BedrockLoginHandler loginHandler;
    private PacketTranslator translator;
    private BedrockPlayer player;
    private InetSocketAddress sender;

    public BedrockSessionHandler(CrossRealmMC plugin) {
        this.plugin = plugin;
        this.loginHandler = new BedrockLoginHandler(plugin, sendSequence);
        this.translator = new PacketTranslator(plugin, sendSequence);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        sender = (InetSocketAddress) ctx.channel().remoteAddress();
        player = new BedrockPlayer(sender);
        plugin.log("&aConexion Bedrock: &f" + sender.getAddress().getHostAddress());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (player != null) {
            if (player.getUuid() != null)
                plugin.getPlayerDetector().unregisterPlayer(player.getUuid());
            plugin.getConnectionLogger().logDisconnect(
                player.getUsername() != null ? player.getUsername() : "Unknown",
                me.crossrealmmc.detection.PlayerDetector.PlayerType.BEDROCK,
                "Disconnected"
            );
        }
        plugin.log("&cDesconexion: &f" + (sender != null ? sender.getAddress().getHostAddress() : "unknown"));
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf buf) {
        if (player == null || sender == null) return;
        try {
            // La libreria ya desenvuelve RakNet — pasar directo al translator
            translator.handleIncoming(buf, sender, player, loginHandler, ctx);
        } catch (Exception e) {
            plugin.log("&cError sesion: &f" + e.getMessage());
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        plugin.log("&cError canal: &f" + cause.getMessage());
    }
}
