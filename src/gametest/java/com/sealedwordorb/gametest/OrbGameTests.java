package com.sealedwordorb.gametest;

import com.mojang.authlib.GameProfile;
import com.sealedwordorb.SealedWordOrb;
import com.sealedwordorb.item.OrbData;
import com.sealedwordorb.item.SealedWordOrbItem;
import com.sealedwordorb.menu.OrbEditorMenu;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Runs only in the dedicated gametest source set, never in the released mod. */
@GameTestHolder(SealedWordOrb.MOD_ID)
@PrefixGameTestTemplate(false)
public final class OrbGameTests {
    @GameTest(template = "empty")
    public static void defaultsSurviveItemNbtRoundTrip(GameTestHelper helper) {
        ItemStack original = orb();
        OrbData.seal(original, "  /say 缄言珠  ", "", "");
        ItemStack loaded = ItemStack.of(original.save(new CompoundTag()));
        helper.assertTrue(OrbData.isSealed(loaded), "Sealed marker must survive item serialization");
        OrbData.Settings settings = OrbData.read(loaded).orElseThrow();
        helper.assertTrue(settings.command().equals("say 缄言珠"), "Command must normalize slash and surrounding spaces");
        helper.assertTrue(settings.permissionLevel() == 2, "Blank permission must default to level 2");
        helper.assertTrue(settings.description().isEmpty(), "Blank description must remain empty");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void invalidInputsCannotPartiallySealAnItem(GameTestHelper helper) {
        for (String command : new String[]{"", " ", "/", " / ", "say a\nsay b", "say a\0", "x".repeat(OrbData.MAX_COMMAND_LENGTH + 1)}) {
            ItemStack stack = orb();
            rejected(helper, () -> OrbData.seal(stack, command, "2", ""));
            helper.assertTrue(!OrbData.isSealed(stack), "Invalid command must not mark the item as sealed");
        }
        for (String permission : new String[]{"-1", "5", "abc", "1.5", "9999999999999999999999"}) {
            ItemStack stack = orb();
            rejected(helper, () -> OrbData.seal(stack, "say test", permission, ""));
            helper.assertTrue(!OrbData.isSealed(stack), "Invalid permission must not mark the item as sealed");
        }
        ItemStack stack = orb();
        rejected(helper, () -> OrbData.seal(stack, "say test", "2", "x".repeat(OrbData.MAX_DESCRIPTION_LENGTH + 1)));
        helper.assertTrue(!OrbData.isSealed(stack), "Overlong description must not mark the item as sealed");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void permissionBoundsAndDescriptionRoundTrip(GameTestHelper helper) {
        for (int level = 0; level <= 4; level++) {
            ItemStack stack = orb();
            OrbData.seal(stack, "say test", Integer.toString(level), "测试物品描述");
            OrbData.Settings settings = OrbData.read(stack).orElseThrow();
            helper.assertTrue(settings.permissionLevel() == level, "All permission levels 0 through 4 must round trip");
            helper.assertTrue(settings.description().equals("测试物品描述"), "Description must round trip without loss");
        }
        helper.assertTrue(OrbData.parsePermission("   ") == 2, "Whitespace-only permission must use the default");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void anyModifiedMarkerPreventsResealing(GameTestHelper helper) {
        ItemStack stack = orb();
        OrbData.seal(stack, "say original", "2", "original");
        CompoundTag before = stack.save(new CompoundTag());
        rejected(helper, () -> OrbData.seal(stack, "say replacement", "4", "replacement"));
        helper.assertTrue(before.equals(stack.save(new CompoundTag())), "Rejected second save must not change the NBT");

        for (boolean wrongType : new boolean[]{false, true}) {
            ItemStack malformed = orb();
            CompoundTag data = malformed.getOrCreateTagElement(OrbData.ROOT_TAG);
            if (wrongType) {
                data.putString(OrbData.MODIFIED_TAG, "false");
            } else {
                data.putBoolean(OrbData.MODIFIED_TAG, false);
            }
            helper.assertTrue(OrbData.isSealed(malformed), "Presence of the marker must lock editing regardless of value/type");
            rejected(helper, () -> OrbData.seal(malformed, "say replacement", "2", ""));
            helper.assertTrue(OrbData.read(malformed).isEmpty(), "Malformed sealed data must not execute");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void editingRequiresCreativeAndAdministrator(GameTestHelper helper) {
        TestPlayer player = player(helper, 0, GameType.CREATIVE);
        helper.assertTrue(!OrbEditorMenu.canEdit(player), "A creative player without OP must not edit");
        player.permissionLevel = 2;
        helper.assertTrue(OrbEditorMenu.canEdit(player), "Creative level-2 administrator must be allowed to edit");
        player.setGameMode(GameType.SURVIVAL);
        helper.assertTrue(!OrbEditorMenu.canEdit(player), "A survival administrator must not edit");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void saveRejectsItemReplacementAndPermissionEscalation(GameTestHelper helper) {
        TestPlayer player = player(helper, 2, GameType.CREATIVE);
        ItemStack original = orb();
        player.setItemInHand(InteractionHand.MAIN_HAND, original);
        OrbEditorMenu menu = menu(player, InteractionHand.MAIN_HAND);
        helper.assertTrue(menu.stillValid(player), "Original held orb must keep the menu valid");
        rejected(helper, () -> menu.save(player, "say test", "3", ""));
        helper.assertTrue(!OrbData.isSealed(original), "A level-2 administrator must not grant level-3 command permission");
        ItemStack replacement = original.copy();
        player.setItemInHand(InteractionHand.MAIN_HAND, replacement);
        helper.assertTrue(!menu.stillValid(player), "Replacing the held item must invalidate the editor");
        rejected(helper, () -> menu.save(player, "say test", "2", ""));
        helper.assertTrue(!OrbData.isSealed(original) && !OrbData.isSealed(replacement), "A stale menu must not modify either item");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void switchingSlotOrLosingPrivilegesInvalidatesEditor(GameTestHelper helper) {
        TestPlayer player = player(helper, 2, GameType.CREATIVE);
        player.setItemInHand(InteractionHand.MAIN_HAND, orb());
        OrbEditorMenu main = menu(player, InteractionHand.MAIN_HAND);
        player.getInventory().selected = 1;
        helper.assertTrue(!main.stillValid(player), "Changing the selected main-hand slot must invalidate editing");
        player.setItemInHand(InteractionHand.OFF_HAND, orb());
        OrbEditorMenu off = menu(player, InteractionHand.OFF_HAND);
        helper.assertTrue(off.stillValid(player), "Off-hand editing must be supported");
        player.permissionLevel = 0;
        helper.assertTrue(!off.stillValid(player), "Losing administrator permissions must invalidate editing");
        rejected(helper, () -> off.save(player, "say test", "2", ""));
        helper.assertTrue(!OrbData.isSealed(player.getOffhandItem()), "Rejected off-hand save must not seal the item");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void survivalHolderExecutesAsSelfAtStoredPermission(GameTestHelper helper) {
        TestPlayer editor = player(helper, 4, GameType.CREATIVE);
        ItemStack stack = orb();
        editor.setItemInHand(InteractionHand.MAIN_HAND, stack);
        menu(editor, InteractionHand.MAIN_HAND).save(editor, "tag @s add sealed_word_orb_used", "2", "测试");
        TestPlayer holder = player(helper, 0, GameType.SURVIVAL);
        holder.setItemInHand(InteractionHand.OFF_HAND, stack.copy());
        SealedWordOrbItem.activate(holder, InteractionHand.OFF_HAND);
        helper.assertTrue(holder.getTags().contains("sealed_word_orb_used"), "A survival non-OP must execute the level-2 command and @s must select that holder");
        helper.assertTrue(!editor.getTags().contains("sealed_word_orb_used"), "The command must not target the original editor");
        helper.assertTrue(holder.containerMenu == holder.inventoryMenu, "A sealed orb must execute without reopening its editor");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void lowStoredPermissionCannotBorrowHolderPermissions(GameTestHelper helper) {
        TestPlayer administrator = player(helper, 4, GameType.CREATIVE);
        administrator.setItemInHand(InteractionHand.MAIN_HAND, orb());
        menu(administrator, InteractionHand.MAIN_HAND).save(administrator, "tag @s add forbidden_permission", "0", "");
        SealedWordOrbItem.activate(administrator, InteractionHand.MAIN_HAND);
        helper.assertTrue(!administrator.getTags().contains("forbidden_permission"), "A level-0 orb must not inherit the administrator holder's level-4 permission");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void entityInteractionEventsExecuteOnceAndConsumeInteraction(GameTestHelper helper) {
        Entity target = EntityType.VILLAGER.create(helper.getLevel());
        helper.assertTrue(target != null, "Villager target must exist");
        for (boolean specific : new boolean[]{false, true}) {
            TestPlayer holder = player(helper, 0, GameType.SURVIVAL);
            ItemStack stack = orb();
            OrbData.seal(stack, "give @s minecraft:stone 1", "2", "");
            holder.setItemInHand(InteractionHand.OFF_HAND, stack);
            PlayerInteractEvent event = specific
                    ? new PlayerInteractEvent.EntityInteractSpecific(holder, InteractionHand.OFF_HAND, target, Vec3.ZERO)
                    : new PlayerInteractEvent.EntityInteract(holder, InteractionHand.OFF_HAND, target);
            MinecraftForge.EVENT_BUS.post(event);
            helper.assertTrue(event.isCanceled(), "The orb must intercept the entity's own interaction");
            helper.assertTrue(event.getCancellationResult().consumesAction(), "Successful orb use must consume the interaction");
            helper.assertTrue(holder.getInventory().countItem(Items.STONE) == 1, "Each entity interaction event must execute the command exactly once");
            helper.assertTrue(holder.getOffhandItem().isEmpty(), "Entity use must consume the orb");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void blockUseFirstExecutesOnceAndConsumesInteraction(GameTestHelper helper) {
        TestPlayer holder = player(helper, 0, GameType.SURVIVAL);
        ItemStack stack = orb();
        OrbData.seal(stack, "give @s minecraft:stone 1", "2", "");
        holder.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos position = helper.absolutePos(BlockPos.ZERO);
        UseOnContext context = new UseOnContext(holder, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(position), Direction.UP, position, false));
        helper.assertTrue(stack.getItem().onItemUseFirst(stack, context).consumesAction(), "Orb block use must take precedence over the block's own interaction");
        helper.assertTrue(holder.getInventory().countItem(Items.STONE) == 1, "Block use must execute exactly once");
        helper.assertTrue(stack.isEmpty() && !holder.getMainHandItem().is(SealedWordOrb.ORB.get()), "Block use must consume the orb and preserve any reward placed in its slot");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void emptyDescriptionAddsNoTooltipAndMalformedDataDoesNotExecute(GameTestHelper helper) {
        TestPlayer holder = player(helper, 4, GameType.CREATIVE);
        ItemStack stack = orb();
        holder.setItemInHand(InteractionHand.MAIN_HAND, stack);
        menu(holder, InteractionHand.MAIN_HAND).save(holder, "tag @s add forbidden_invalid_data", "", "");
        helper.assertTrue(OrbData.read(stack).orElseThrow().permissionLevel() == 2, "Menu saving must apply the default permission");
        List<Component> tooltip = new ArrayList<>();
        stack.getItem().appendHoverText(stack, helper.getLevel(), tooltip, TooltipFlag.Default.NORMAL);
        helper.assertTrue(tooltip.isEmpty(), "A sealed orb with no description must add no tooltip text");

        stack.getOrCreateTagElement(OrbData.ROOT_TAG).putInt(OrbData.PERMISSION_TAG, 5);
        holder.containerMenu = holder.inventoryMenu;
        SealedWordOrbItem.activate(holder, InteractionHand.MAIN_HAND);
        helper.assertTrue(!holder.getTags().contains("forbidden_invalid_data"), "Invalid stored permission must not execute the command");
        helper.assertTrue(holder.containerMenu == holder.inventoryMenu, "Malformed sealed NBT must never reopen the editor");
        helper.assertTrue(OrbData.isSealed(stack), "Rejecting malformed data must preserve its editing lock");
        helper.assertTrue(stack.getCount() == 1, "Malformed data must not consume the orb without executing");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void absenceOfMarkerIsDefaultAndOpeningMenuDoesNotSeal(GameTestHelper helper) {
        TestPlayer editor = player(helper, 4, GameType.CREATIVE);
        ItemStack stack = orb();
        helper.assertTrue(!stack.hasTag() && !OrbData.isSealed(stack), "New creative items must start without the modified marker");
        editor.setItemInHand(InteractionHand.MAIN_HAND, stack);
        menu(editor, InteractionHand.MAIN_HAND);
        helper.assertTrue(!stack.hasTag(), "Opening the editor must not write any NBT");
        stack.getOrCreateTagElement(OrbData.ROOT_TAG).putString(OrbData.COMMAND_TAG, "say incomplete");
        helper.assertTrue(!OrbData.isSealed(stack), "Partial fields without Modified must remain in the default state");
        helper.assertTrue(menu(editor, InteractionHand.MAIN_HAND).stillValid(editor), "A missing Modified tag must allow editing");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void vanillaAirUseConsumesInCreativeAndSurvivalForBothHands(GameTestHelper helper) {
        for (GameType mode : new GameType[]{GameType.CREATIVE, GameType.SURVIVAL}) {
            for (InteractionHand hand : InteractionHand.values()) {
                TestPlayer holder = player(helper, 0, mode);
                ItemStack stack = orb();
                OrbData.seal(stack, "give @s minecraft:stone 1", "2", "");
                holder.setItemInHand(hand, stack);
                helper.assertTrue(holder.gameMode.useItem(holder, holder.level(), stack, hand).consumesAction(), "Vanilla air-use must be intercepted");
                helper.assertTrue(!holder.getItemInHand(hand).is(SealedWordOrb.ORB.get()) && stack.isEmpty(), "Even creative use must consume the orb and preserve the granted reward");
                SealedWordOrbItem.activate(holder, hand);
                helper.assertTrue(holder.getInventory().countItem(Items.STONE) == 1, "Repeating use after the orb is consumed must not repeat the reward");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void rewardCommandCanReplaceHandWithoutLosingItsItem(GameTestHelper helper) {
        for (GameType mode : new GameType[]{GameType.CREATIVE, GameType.SURVIVAL}) {
            TestPlayer holder = player(helper, 0, mode);
            ItemStack stack = orb();
            OrbData.seal(stack, "item replace entity @s weapon.mainhand with minecraft:diamond 3", "2", "");
            holder.setItemInHand(InteractionHand.MAIN_HAND, stack);
            holder.gameMode.useItem(holder, holder.level(), stack, InteractionHand.MAIN_HAND);
            helper.assertTrue(stack.isEmpty(), "The original orb must be consumed");
            helper.assertTrue(holder.getMainHandItem().is(Items.DIAMOND) && holder.getMainHandItem().getCount() == 3,
                    "Vanilla use must not overwrite the command's replacement item or restore the old count");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void serializedRewardTemplatesRemainReusableAcrossRecipients(GameTestHelper helper) throws Exception {
        // The full-item compound used by FTB Quests 1.20.1 ItemReward/NBTUtils.
        // This tests the serialization contract; it does not claim to load FTB itself.
        ItemStack template = orb();
        OrbData.seal(template, "tag @s add imported_orb_reward", "2", "整合包奖励");
        CompoundTag reward = new CompoundTag();
        reward.putString("type", "item");
        CompoundTag fullItem = template.save(new CompoundTag());
        fullItem.putInt("Count", template.getCount()); // FTB MissingItem.writeItem uses an int.
        reward.put("item", fullItem);
        reward.putInt("count", 1);
        String exported = reward.toString();
        ItemStack importedTemplate = ItemStack.of(TagParser.parseTag(exported).getCompound("item"));
        helper.assertTrue(OrbData.read(importedTemplate).equals(OrbData.read(template)), "Text export/import must preserve every sealed field");
        for (ServerLevel destination : new ServerLevel[]{helper.getLevel(), helper.getLevel().getServer().getLevel(Level.NETHER)}) {
            helper.assertTrue(destination != null, "Both test dimensions must exist");
            TestPlayer recipient = new TestPlayer(destination, 0);
            recipient.setGameMode(GameType.SURVIVAL);
            recipient.setItemInHand(InteractionHand.MAIN_HAND, importedTemplate.copy());
            SealedWordOrbItem.activate(recipient, InteractionHand.MAIN_HAND);
            helper.assertTrue(recipient.getTags().contains("imported_orb_reward"), "A newly created recipient must execute the imported reward as self");
            helper.assertTrue(recipient.getMainHandItem().isEmpty(), "Every distributed copy must be single-use");
        }
        helper.assertTrue(importedTemplate.getCount() == 1 && template.getCount() == 1, "Using a reward copy must not consume or corrupt the stored template");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void failedCommandsStillConsumeOneUse(GameTestHelper helper) {
        TestPlayer holder = player(helper, 0, GameType.SURVIVAL);
        ItemStack stack = orb();
        OrbData.seal(stack, "sealedwordorb_command_does_not_exist", "2", "");
        holder.setItemInHand(InteractionHand.MAIN_HAND, stack);
        SealedWordOrbItem.activate(holder, InteractionHand.MAIN_HAND);
        helper.assertTrue(holder.getMainHandItem().isEmpty(), "A command attempt must spend the one use even when the command fails");
        helper.succeed();
    }

    private static ItemStack orb() {
        return new ItemStack(SealedWordOrb.ORB.get());
    }

    private static TestPlayer player(GameTestHelper helper, int permission, GameType gameType) {
        TestPlayer player = new TestPlayer(helper.getLevel(), permission);
        player.setGameMode(gameType);
        return player;
    }

    private static OrbEditorMenu menu(TestPlayer player, InteractionHand hand) {
        OrbEditorMenu menu = new OrbEditorMenu(1, player.getInventory(), hand);
        player.containerMenu = menu;
        return menu;
    }

    private static void rejected(GameTestHelper helper, Runnable operation) {
        boolean rejected = false;
        try {
            operation.run();
        } catch (IllegalArgumentException | IllegalStateException expected) {
            rejected = true;
        }
        helper.assertTrue(rejected, "Invalid operation must be rejected");
    }

    private static final class TestPlayer extends FakePlayer {
        private int permissionLevel;

        private TestPlayer(ServerLevel level, int permissionLevel) {
            super(level, new GameProfile(UUID.randomUUID(), "OrbGameTest"));
            this.permissionLevel = permissionLevel;
        }

        @Override
        protected int getPermissionLevel() {
            return permissionLevel;
        }
    }
}
