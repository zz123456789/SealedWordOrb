package com.sealedwordorb.client;

import com.sealedwordorb.item.OrbData;
import com.sealedwordorb.menu.OrbEditorMenu;
import com.sealedwordorb.network.OrbNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public final class OrbEditorScreen extends AbstractContainerScreen<OrbEditorMenu> {
    private static final int PANEL_HEIGHT = 224;
    private static final int PADDING = 12;
    private static final int LABEL_COLOR = 0xDCD8EE;
    private static final int MUTED_COLOR = 0xB0A8C7;
    private static final int ERROR_COLOR = 0xFFB3AC;

    private EditBox command;
    private EditBox permission;
    private EditBox description;
    private Button saveButton;
    private Button cancelButton;
    private Component validationMessage = Component.empty();
    private boolean valid;
    private boolean saving;

    public OrbEditorScreen(OrbEditorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void init() {
        // Minecraft rebuilds widgets when the window or GUI scale changes.
        String oldCommand = command == null ? "" : command.getValue();
        String oldPermission = permission == null ? "" : permission.getValue();
        String oldDescription = description == null ? "" : description.getValue();
        int focusedField = permission != null && permission.isFocused() ? 1
                : description != null && description.isFocused() ? 2 : 0;

        imageWidth = Math.min(420, width - 16);
        imageHeight = PANEL_HEIGHT;
        super.init();

        int fieldX = leftPos + PADDING;
        int fieldWidth = imageWidth - PADDING * 2;
        command = addRenderableWidget(new EditBox(font, fieldX, topPos + 55, fieldWidth, 20,
                text("command")));
        command.setMaxLength(OrbData.MAX_COMMAND_LENGTH);
        command.setHint(text("command_hint"));
        command.setValue(oldCommand);

        permission = addRenderableWidget(new EditBox(font, fieldX, topPos + 94, fieldWidth, 20,
                text("permission")));
        permission.setMaxLength(32);
        permission.setHint(Component.literal(Integer.toString(OrbData.DEFAULT_PERMISSION)));
        permission.setValue(oldPermission);

        description = addRenderableWidget(new EditBox(font, fieldX, topPos + 133, fieldWidth, 20,
                text("description")));
        description.setMaxLength(OrbData.MAX_DESCRIPTION_LENGTH);
        description.setValue(oldDescription);

        int buttonWidth = (fieldWidth - 8) / 2;
        saveButton = addRenderableWidget(Button.builder(text("save"), button -> save())
                .bounds(fieldX, topPos + 192, buttonWidth, 20).build());
        cancelButton = addRenderableWidget(Button.builder(text("cancel"), button -> onClose())
                .bounds(fieldX + buttonWidth + 8, topPos + 192, fieldWidth - buttonWidth - 8, 20).build());

        command.setResponder(value -> validateInput());
        permission.setResponder(value -> validateInput());
        description.setResponder(value -> validateInput());
        validateInput();
        updateEditableState();
        if (!saving) {
            setInitialFocus(focusedField == 1 ? permission : focusedField == 2 ? description : command);
        }
    }

    private static Component text(String key) {
        return Component.translatable("screen.sealedwordorb." + key);
    }

    private void validateInput() {
        valid = false;
        try {
            if (OrbData.normalizeCommand(command.getValue()).isEmpty()) {
                validationMessage = text("command_required");
                saveButton.active = false;
                return;
            }
        } catch (IllegalArgumentException exception) {
            validationMessage = Component.translatable("error.sealedwordorb.invalid_command");
            saveButton.active = false;
            return;
        }

        try {
            OrbData.parsePermission(permission.getValue());
        } catch (IllegalArgumentException exception) {
            validationMessage = Component.translatable("error.sealedwordorb.invalid_permission");
            saveButton.active = false;
            return;
        }

        valid = true;
        validationMessage = text("ready");
        saveButton.active = !saving;
    }

    private void save() {
        if (saving) {
            return;
        }
        validateInput();
        if (!valid) {
            return;
        }

        saving = true;
        updateEditableState();
        // The server validates the held item and closes this menu after handling the request.
        OrbNetwork.save(menu.containerId, command.getValue(), permission.getValue(), description.getValue());
    }

    private void updateEditableState() {
        command.setEditable(!saving);
        permission.setEditable(!saving);
        description.setEditable(!saving);
        saveButton.active = valid && !saving;
        cancelButton.active = !saving;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        command.tick();
        permission.tick();
        description.tick();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        // Let Screen handle Tab navigation, but consume inventory/hotbar keys while typing.
        if (keyCode != GLFW.GLFW_KEY_TAB && getFocused() instanceof EditBox editBox && editBox.canConsumeInput()) {
            editBox.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        if (!saving) {
            super.onClose();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos - 1, topPos - 1, leftPos + imageWidth + 1, topPos + imageHeight + 1, 0xFF8C7BAA);
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF211B30);
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + 2, 0xFFCCB6F4);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        int contentWidth = imageWidth - PADDING * 2;
        drawFitted(graphics, text("title"), 12, 0xFFFFFF, contentWidth);
        drawFitted(graphics, text("notice"), 27, MUTED_COLOR, contentWidth);
        drawFitted(graphics, text("command"), 43, LABEL_COLOR, contentWidth);
        drawFitted(graphics, text("permission"), 82, LABEL_COLOR, contentWidth);
        drawFitted(graphics, text("description"), 121, LABEL_COLOR, contentWidth);

        Component status = saving ? text("saving") : validationMessage;
        List<FormattedCharSequence> lines = font.split(status, contentWidth);
        for (int i = 0; i < Math.min(2, lines.size()); i++) {
            graphics.drawString(font, lines.get(i), PADDING, 161 + i * (font.lineHeight + 1),
                    saving || valid ? MUTED_COLOR : ERROR_COLOR, false);
        }
    }

    private void drawFitted(GuiGraphics graphics, Component component, int y, int color, int maxWidth) {
        String value = component.getString();
        if (font.width(value) > maxWidth) {
            String ellipsis = "...";
            value = font.plainSubstrByWidth(value, Math.max(0, maxWidth - font.width(ellipsis))) + ellipsis;
        }
        graphics.drawString(font, value, PADDING, y, color, false);
    }
}
