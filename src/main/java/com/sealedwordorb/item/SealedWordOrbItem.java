package com.sealedwordorb.item;

import com.sealedwordorb.SealedWordOrb;
import com.sealedwordorb.menu.OrbEditorMenu;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

public final class SealedWordOrbItem extends Item {
    public SealedWordOrbItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return activate(player, hand);
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        return context.getPlayer() == null ? InteractionResult.PASS
                : activate(context.getPlayer(), context.getHand()).getResult();
    }

    public static InteractionResultHolder<ItemStack> activate(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.isEmpty() || !stack.is(SealedWordOrb.ORB.get())) return InteractionResultHolder.pass(stack);
        if (player.isSpectator()) return InteractionResultHolder.fail(stack);
        if (player instanceof ServerPlayer serverPlayer) {
            if (OrbData.isSealed(stack)) {
                OrbData.read(stack).ifPresentOrElse(settings -> {
                    // Consume before running arbitrary commands: they may clear/replace the hand,
                    // teleport the player, or recursively trigger another use of the same stack.
                    stack.shrink(1);
                    if (stack.isEmpty()) player.setItemInHand(hand, ItemStack.EMPTY);
                    // withPermission replaces the player's level; operators cannot raise a level-0 orb's rights.
                    var source = serverPlayer.createCommandSourceStack().withPermission(settings.permissionLevel());
                    serverPlayer.getServer().getCommands().performPrefixedCommand(source, settings.command());
                    player.getInventory().setChanged();
                    serverPlayer.inventoryMenu.broadcastChanges();
                }, () -> serverPlayer.sendSystemMessage(Component.translatable("error.sealedwordorb.invalid_data")
                        .withStyle(ChatFormatting.RED)));
            } else if (OrbEditorMenu.canEdit(player)) {
                NetworkHooks.openScreen(serverPlayer,
                        new SimpleMenuProvider((id, inventory, owner) -> new OrbEditorMenu(id, inventory, hand),
                                Component.translatable("screen.sealedwordorb.title")),
                        buffer -> buffer.writeEnum(hand));
            } else {
                serverPlayer.sendSystemMessage(Component.translatable("error.sealedwordorb.editor_permission")
                        .withStyle(ChatFormatting.RED));
            }
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), player.level().isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flags) {
        if (OrbData.isSealed(stack)) {
            OrbData.read(stack).ifPresent(settings -> {
                if (!settings.description().isEmpty()) {
                    for (String line : settings.description().split("\\R")) {
                        tooltip.add(Component.literal(line).withStyle(ChatFormatting.GRAY));
                    }
                }
            });
        } else {
            tooltip.add(Component.translatable("tooltip.sealedwordorb.unsealed").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
