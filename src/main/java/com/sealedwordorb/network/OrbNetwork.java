package com.sealedwordorb.network;

import com.sealedwordorb.SealedWordOrb;
import com.sealedwordorb.item.OrbData;
import com.sealedwordorb.menu.OrbEditorMenu;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class OrbNetwork {
    private static final String VERSION = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(SealedWordOrb.MOD_ID, "main"), () -> VERSION, VERSION::equals, VERSION::equals);

    private OrbNetwork() {}

    public static void register() {
        CHANNEL.messageBuilder(SaveOrb.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SaveOrb::encode).decoder(SaveOrb::decode)
                .consumerMainThread(SaveOrb::handle).add();
    }

    public static void save(int containerId, String command, String permission, String description) {
        CHANNEL.sendToServer(new SaveOrb(containerId, command, permission, description));
    }

    private record SaveOrb(int containerId, String command, String permission, String description) {
        private void encode(FriendlyByteBuf buffer) {
            buffer.writeVarInt(containerId);
            buffer.writeUtf(command, OrbData.MAX_COMMAND_LENGTH);
            buffer.writeUtf(permission, 32);
            buffer.writeUtf(description, OrbData.MAX_DESCRIPTION_LENGTH);
        }

        private static SaveOrb decode(FriendlyByteBuf buffer) {
            return new SaveOrb(buffer.readVarInt(), buffer.readUtf(OrbData.MAX_COMMAND_LENGTH),
                    buffer.readUtf(32), buffer.readUtf(OrbData.MAX_DESCRIPTION_LENGTH));
        }

        private void handle(Supplier<NetworkEvent.Context> context) {
            ServerPlayer player = context.get().getSender();
            if (player == null || !(player.containerMenu instanceof OrbEditorMenu menu)
                    || menu.containerId != containerId) return;
            try {
                menu.save(player, command, permission, description);
                player.sendSystemMessage(Component.translatable("message.sealedwordorb.saved").withStyle(ChatFormatting.GREEN));
            } catch (IllegalArgumentException | IllegalStateException exception) {
                player.sendSystemMessage(Component.translatable(exception.getMessage()).withStyle(ChatFormatting.RED));
            } finally {
                player.closeContainer();
            }
        }
    }
}
