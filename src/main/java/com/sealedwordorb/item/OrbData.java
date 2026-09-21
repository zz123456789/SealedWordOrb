package com.sealedwordorb.item;

import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/** Shared validation and the persistent, version-independent item payload. */
public final class OrbData {
    public static final String ROOT_TAG = "SealedWordOrb";
    public static final String COMMAND_TAG = "Command";
    public static final String PERMISSION_TAG = "PermissionLevel";
    public static final String DESCRIPTION_TAG = "Description";
    public static final String MODIFIED_TAG = "Modified";
    public static final int MAX_COMMAND_LENGTH = 32767;
    public static final int MAX_DESCRIPTION_LENGTH = 2048;
    public static final int MIN_PERMISSION = 0;
    public static final int MAX_PERMISSION = 4;
    public static final int DEFAULT_PERMISSION = 2;

    private OrbData() {}

    public record Settings(String command, int permissionLevel, String description) {}

    public static String normalizeCommand(String input) {
        String command = input.strip();
        if (command.startsWith("/")) command = command.substring(1).stripLeading();
        return command;
    }

    public static int parsePermission(String input) {
        String value = input.strip();
        if (value.isEmpty()) return DEFAULT_PERMISSION;
        if (value.length() != 1 || value.charAt(0) < '0' || value.charAt(0) > '4') {
            throw new IllegalArgumentException("error.sealedwordorb.invalid_permission");
        }
        return value.charAt(0) - '0';
    }

    public static boolean isSealed(ItemStack stack) {
        CompoundTag data = stack.getTagElement(ROOT_TAG);
        // Presence, not the boolean value, is the irreversible editing lock.
        return data != null && data.contains(MODIFIED_TAG);
    }

    public static void seal(ItemStack stack, String command, String permission, String description) {
        if (isSealed(stack)) throw new IllegalStateException("error.sealedwordorb.item_changed");
        command = normalizeCommand(command);
        validateCommand(command);
        int level = parsePermission(permission);
        if (description.length() > MAX_DESCRIPTION_LENGTH) {
            throw new IllegalArgumentException("error.sealedwordorb.invalid_description");
        }
        CompoundTag data = new CompoundTag();
        data.putString(COMMAND_TAG, command);
        data.putInt(PERMISSION_TAG, level);
        data.putString(DESCRIPTION_TAG, description.isBlank() ? "" : description);
        data.putBoolean(MODIFIED_TAG, true);
        // Commit once, after every field has passed validation.
        stack.addTagElement(ROOT_TAG, data);
    }

    public static Optional<Settings> read(ItemStack stack) {
        CompoundTag data = stack.getTagElement(ROOT_TAG);
        if (!isSealed(stack) || data == null
                || !data.contains(COMMAND_TAG, Tag.TAG_STRING)
                || !data.contains(PERMISSION_TAG, Tag.TAG_INT)
                || !data.contains(DESCRIPTION_TAG, Tag.TAG_STRING)) return Optional.empty();
        String command = data.getString(COMMAND_TAG);
        String description = data.getString(DESCRIPTION_TAG);
        int level = data.getInt(PERMISSION_TAG);
        try {
            validateCommand(command);
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
        if (level < MIN_PERMISSION || level > MAX_PERMISSION || description.length() > MAX_DESCRIPTION_LENGTH) {
            return Optional.empty();
        }
        return Optional.of(new Settings(command, level, description));
    }

    private static void validateCommand(String command) {
        if (normalizeCommand(command).isBlank() || command.length() > MAX_COMMAND_LENGTH
                || command.indexOf('\n') >= 0 || command.indexOf('\r') >= 0 || command.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("error.sealedwordorb.invalid_command");
        }
    }
}
