package com.sealedwordorb.menu;

import com.sealedwordorb.SealedWordOrb;
import com.sealedwordorb.item.OrbData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

public final class OrbEditorMenu extends AbstractContainerMenu {
    private final Player owner;
    private final InteractionHand hand;
    private final int selectedSlot;
    private final ItemStack originalStack;

    public OrbEditorMenu(int containerId, Inventory inventory, FriendlyByteBuf data) {
        this(containerId, inventory, data.readEnum(InteractionHand.class));
    }

    public OrbEditorMenu(int containerId, Inventory inventory, InteractionHand hand) {
        super(SealedWordOrb.ORB_EDITOR_MENU.get(), containerId);
        this.owner = inventory.player;
        this.hand = hand;
        this.selectedSlot = inventory.selected;
        this.originalStack = owner.getItemInHand(hand);
    }

    public static boolean canEdit(Player player) {
        return player.isCreative() && player.hasPermissions(2);
    }

    @Override
    public boolean stillValid(Player player) {
        return player == owner && player.isAlive() && canEdit(player)
                && (hand != InteractionHand.MAIN_HAND || player.getInventory().selected == selectedSlot)
                && player.getItemInHand(hand) == originalStack
                && originalStack.is(SealedWordOrb.ORB.get()) && originalStack.getCount() == 1
                && !OrbData.isSealed(originalStack);
    }

    public void save(ServerPlayer player, String command, String permission, String description) {
        if (!canEdit(player)) throw new IllegalStateException("error.sealedwordorb.editor_permission");
        if (!stillValid(player)) throw new IllegalStateException("error.sealedwordorb.item_changed");
        int level = OrbData.parsePermission(permission);
        if (!player.createCommandSourceStack().hasPermission(level)) {
            throw new IllegalArgumentException("error.sealedwordorb.permission_escalation");
        }
        OrbData.seal(originalStack, command, permission, description);
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slot) {
        return ItemStack.EMPTY;
    }
}
