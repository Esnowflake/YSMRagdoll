package com.ysmragdoll.client;

import com.ysmragdoll.YsmRagdollLog;
import com.ysmragdoll.config.YsmRagdollConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 左侧分类导航、右侧设置内容的单页配置界面。 */
public final class YsmRagdollSettingsScreen extends Screen {
    private static final int PANEL_COLOR = 0xE8101114;
    private static final int NAVIGATION_COLOR = 0xD9181A1F;
    private static final int CONTENT_COLOR = 0xC8141519;
    private static final int DIVIDER_COLOR = 0xFF3B3E45;
    private static final int LABEL_COLOR = 0xFFE2E2E2;
    private static final int MUTED_COLOR = 0xFF9A9DA5;
    private static final int ERROR_COLOR = 0xFFFF6B6B;
    private static final int SUCCESS_COLOR = 0xFF70D68A;
    private static final double MIN_OFFSET = -4.0;
    private static final double MAX_OFFSET = 4.0;

    private final Screen parent;
    private Category category = Category.GENERAL;
    private Draft draft;
    private Component status = Component.empty();
    private boolean statusIsError;

    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int panelHeight;
    private int navigationWidth;
    private int contentLeft;
    private int contentWidth;
    private int contentTop;
    private int footerTop;
    private int contentBottom;
    private int contentHeight;
    private int scroll;
    private boolean draggingScrollbar;
    private double scrollbarGrabOffset;
    private final List<Label> labels = new ArrayList<>();
    private final List<PositionedWidget> contentWidgets = new ArrayList<>();
    private final Map<String, String> inputText = new HashMap<>();

    private EditBox lifetime;
    private EditBox maximum;
    private EditBox easyPush;
    private EditBox groundFriction;
    private EditBox explosionPush;
    private final EditBox[] renderOffsets = new EditBox[3];
    private final EditBox[] collisionOffsets = new EditBox[3];

    public YsmRagdollSettingsScreen(Screen parent) {
        super(Component.translatable("screen.ysmragdoll.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        if (draft == null) {
            draft = Draft.load();
        }
        resetWidgetReferences();
        draggingScrollbar = false;
        labels.clear();
        contentWidgets.clear();
        panelWidth = Math.min(620, width - 16);
        panelHeight = Math.min(390, height - 16);
        panelLeft = (width - panelWidth) / 2;
        panelTop = (height - panelHeight) / 2;
        navigationWidth = Math.min(112, Math.max(72, panelWidth / 5));
        contentLeft = panelLeft + navigationWidth + 12;
        contentWidth = panelWidth - navigationWidth - 28;
        contentTop = panelTop + 48;
        footerTop = panelTop + panelHeight - 28;
        contentBottom = footerTop - 30;

        addCategoryButton(Category.GENERAL, panelTop + 48);
        addCategoryButton(Category.ADVANCED, panelTop + 74);
        addCategoryButton(Category.MANAGEMENT, panelTop + 100);
        addCategoryButton(Category.TESTING, panelTop + 126);
        if (category == Category.GENERAL) {
            addGeneralControls();
        } else if (category == Category.ADVANCED) {
            addAdvancedControls();
        } else if (category == Category.MANAGEMENT) {
            addManagementLabels(ClientRagdollManager.managementEntries());
        } else {
            addTestingControls();
        }
        addFooterButtons();
        positionContent();
    }

    private void addCategoryButton(Category target, int y) {
        Button button = addRenderableWidget(Button.builder(target.title, ignored -> switchTo(target))
                .bounds(panelLeft + 10, y, navigationWidth - 20, 20).build());
        button.active = category != target;
    }

    private void addGeneralControls() {
        int fieldWidth = Math.min(260, contentWidth);
        int y = label("screen.ysmragdoll.general.lifetime", contentLeft, 0, fieldWidth);
        lifetime = integerField(contentLeft, y, fieldWidth,
                draft.lifetimeSeconds, 10, "screen.ysmragdoll.general.lifetime");
        y = checkbox("screen.ysmragdoll.general.manual_removal", contentLeft, y + 34,
                fieldWidth, draft.manualRemoval, selected -> draft.manualRemoval = selected);
        y = label("screen.ysmragdoll.general.maximum", contentLeft, y, fieldWidth);
        maximum = integerField(contentLeft, y, fieldWidth,
                draft.maximumRagdolls, 2, "screen.ysmragdoll.general.maximum");
        contentHeight = y + 34;
    }

    private void addTestingControls() {
        int y = label("screen.ysmragdoll.testing.gravity_hint", contentLeft, 0, contentWidth);
        contentWidget(Button.builder(gravityGunLabel(), button -> {
            boolean enabled = !YsmRagdollConfig.GRAVITY_GUN_MODE.get();
            YsmRagdollConfig.GRAVITY_GUN_MODE.set(enabled);
            YsmRagdollConfig.SPEC.save();
            if (!enabled) GravityGunController.release();
            button.setMessage(gravityGunLabel());
        }).bounds(contentLeft, 0, Math.min(260, contentWidth), 20).build(), y);
        contentHeight = y + 34;
    }

    private Component gravityGunLabel() {
        return Component.translatable("screen.ysmragdoll.testing.gravity_gun",
                Component.translatable(YsmRagdollConfig.GRAVITY_GUN_MODE.get()
                        ? "options.ysmragdoll.enabled" : "options.ysmragdoll.disabled"));
    }

    private void addManagementLabels(List<ClientRagdollManager.ManagementEntry> entries) {
        labels.clear();
        int y = label(Component.translatable("screen.ysmragdoll.management.count", entries.size()),
                contentLeft, 0, contentWidth);
        y = label("screen.ysmragdoll.management.hint", contentLeft, y + 4, contentWidth);
        if (entries.isEmpty()) {
            y = label("screen.ysmragdoll.management.empty", contentLeft, y + 8, contentWidth);
        }
        for (ClientRagdollManager.ManagementEntry entry : entries) {
            y = label(Component.translatable("screen.ysmragdoll.management.entry", entry.id(),
                    entry.playerId().substring(0, 8)), contentLeft, y + 8, contentWidth);
            Component expiry = entry.manualRemoval()
                    ? Component.translatable("screen.ysmragdoll.management.manual")
                    : entry.remainingMillis() < 0
                    ? Component.translatable("screen.ysmragdoll.management.permanent")
                    : Component.translatable("screen.ysmragdoll.management.countdown",
                            (entry.remainingMillis() + 999L) / 1000L);
            y = label(expiry, contentLeft, y, contentWidth);
        }
        contentHeight = y + 8;
    }

    @Override
    public void tick() {
        super.tick();
        if (category == Category.MANAGEMENT) {
            addManagementLabels(ClientRagdollManager.managementEntries());
            positionContent();
        }
    }

    private void addAdvancedControls() {
        int controlsTop = label("screen.ysmragdoll.advanced.create_hint", contentLeft, 0, contentWidth);
        contentWidget(Button.builder(Component.translatable("screen.ysmragdoll.advanced.create"),
                ignored -> createTestRagdoll()).bounds(contentLeft, 0,
                Math.min(260, contentWidth), 20).build(), controlsTop);
        controlsTop += 34;
        boolean twoColumns = contentWidth >= 420;
        int gap = 20;
        int fieldWidth = twoColumns ? (contentWidth - gap) / 2 : contentWidth;
        int rightColumnLeft = twoColumns ? contentLeft + fieldWidth + gap : contentLeft;
        int y = label("screen.ysmragdoll.advanced.easy_push", contentLeft, controlsTop, fieldWidth);
        easyPush = integerField(contentLeft, y, fieldWidth,
                draft.easyPushIndex, 3, "screen.ysmragdoll.advanced.easy_push");
        y = label("screen.ysmragdoll.advanced.friction", contentLeft, y + 34, fieldWidth);
        groundFriction = integerField(contentLeft, y, fieldWidth,
                draft.groundFriction, 3, "screen.ysmragdoll.advanced.friction");
        y = label("screen.ysmragdoll.advanced.explosion_push", contentLeft, y + 34, fieldWidth);
        explosionPush = integerField(contentLeft, y, fieldWidth,
                draft.explosionPushIndex, 3, "screen.ysmragdoll.advanced.explosion_push");
        int leftHeight = checkbox("screen.ysmragdoll.advanced.collision_boxes", contentLeft,
                y + 34, fieldWidth, draft.showCollisionBoxes,
                selected -> draft.showCollisionBoxes = selected);
        leftHeight = checkbox("screen.ysmragdoll.advanced.intensive_test", contentLeft,
                leftHeight, fieldWidth, draft.intensiveTest, selected -> draft.intensiveTest = selected);
        y = offsetRow("screen.ysmragdoll.advanced.render_offset", rightColumnLeft,
                twoColumns ? controlsTop : leftHeight, fieldWidth, draft.renderOffsets, renderOffsets);
        y = offsetRow("screen.ysmragdoll.advanced.collision_offset", rightColumnLeft,
                y, fieldWidth, draft.collisionOffsets, collisionOffsets);
        contentHeight = Math.max(leftHeight, y);
    }

    private void createTestRagdoll() {
        try {
            ClientRagdollManager.TestSpawnResult result = ClientRagdollManager.createFromCurrentPlayer();
            String key = switch (result) {
                case CREATED -> "created";
                case STATIC_CREATED -> "static_created";
                case NO_PLAYER -> "no_player";
                case DISABLED -> "disabled";
                case BELOW_VOID -> "below_void";
                case CAPTURE_FAILED -> "capture_failed";
            };
            status = Component.translatable("screen.ysmragdoll.spawn." + key,
                    ClientRagdollManager.ragdollCount(), ClientRagdollManager.physicsRagdollCount());
            statusIsError = result != ClientRagdollManager.TestSpawnResult.CREATED;
        } catch (RuntimeException | LinkageError exception) {
            YsmRagdollLog.warn("手动创建测试布娃娃失败", exception);
            status = Component.translatable("screen.ysmragdoll.spawn.capture_failed");
            statusIsError = true;
        }
        positionContent();
    }

    /** Keep singleplayer simulation running while repeatedly creating performance samples. */
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int label(String key, int x, int y, int availableWidth) {
        return label(Component.translatable(key), x, y, availableWidth);
    }

    private int label(Component text, int x, int y, int availableWidth) {
        List<FormattedCharSequence> lines = font.split(text, availableWidth);
        for (FormattedCharSequence line : lines) {
            labels.add(new Label(line, x, y));
            y += font.lineHeight;
        }
        return y + 6;
    }

    private int checkbox(String key, int x, int y, int availableWidth, boolean selected,
                         java.util.function.Consumer<Boolean> changed) {
        int bottom = label(key, x + 28, y + 5, availableWidth - 28);
        Checkbox box = new Checkbox(x, 0, 20, 20, Component.translatable(key), selected, false) {
            @Override
            public void onPress() {
                super.onPress();
                changed.accept(selected());
                clearStatus();
            }
        };
        contentWidget(box, y);
        return Math.max(y + 20, bottom) + 14;
    }

    private int offsetRow(String key, int x, int y, int availableWidth,
                          double[] values, EditBox[] fields) {
        int top = label(key, x, y, availableWidth);
        int axisWidth = (availableWidth - 16) / 3;
        for (int axis = 0; axis < 3; axis++) {
            int axisX = x + axis * (axisWidth + 8);
            String axisName = "XYZ".substring(axis, axis + 1);
            labels.add(new Label(Component.literal(axisName).getVisualOrderText(), axisX, top));
            fields[axis] = decimalField(axisX, top + font.lineHeight + 6, axisWidth,
                    values[axis], key, axisName);
        }
        return top + font.lineHeight + 40;
    }

    private <T extends AbstractWidget> T contentWidget(T widget, int relativeY) {
        contentWidgets.add(new PositionedWidget(widget, relativeY));
        return addWidget(widget);
    }

    private void positionContent() {
        int statusHeight = status.getString().isEmpty() ? 0
                : font.split(status, panelWidth - 24).size() * font.lineHeight + 12;
        contentBottom = footerTop - Math.max(30, statusHeight);
        scroll = Math.max(0, Math.min(scroll, maximumScroll()));
        for (PositionedWidget entry : contentWidgets) {
            AbstractWidget widget = entry.widget();
            widget.setY(contentTop + entry.y() - scroll);
            widget.visible = widget.getY() >= contentTop
                    && widget.getY() + widget.getHeight() <= contentBottom;
            if (!widget.visible && getFocused() == widget) {
                setFocused(null);
            }
        }
    }

    private int maximumScroll() {
        return Math.max(0, contentHeight - (contentBottom - contentTop));
    }

    private int scrollbarThumbHeight() {
        int trackHeight = contentBottom - contentTop;
        return Math.max(12, trackHeight * trackHeight / contentHeight);
    }

    private int scrollbarThumbTop() {
        return contentTop + (contentBottom - contentTop - scrollbarThumbHeight())
                * scroll / maximumScroll();
    }

    private void dragScrollbar(double mouseY) {
        int travel = contentBottom - contentTop - scrollbarThumbHeight();
        scroll = travel <= 0 ? 0 : (int) Math.round(
                (mouseY - contentTop - scrollbarGrabOffset) * maximumScroll() / travel);
        positionContent();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && maximumScroll() > 0
                && mouseX >= panelLeft + panelWidth - 11 && mouseX <= panelLeft + panelWidth - 2
                && mouseY >= contentTop && mouseY <= contentBottom) {
            int thumbTop = scrollbarThumbTop();
            int thumbHeight = scrollbarThumbHeight();
            scrollbarGrabOffset = mouseY >= thumbTop && mouseY <= thumbTop + thumbHeight
                    ? mouseY - thumbTop : thumbHeight / 2.0;
            draggingScrollbar = true;
            dragScrollbar(mouseY);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (button == 0 && draggingScrollbar) {
            dragScrollbar(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX >= contentLeft && mouseX <= panelLeft + panelWidth
                && mouseY >= contentTop && mouseY <= contentBottom) {
            scroll -= (int) (delta * 24);
            positionContent();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_PAGE_DOWN || keyCode == GLFW.GLFW_KEY_PAGE_UP) {
            scroll += (keyCode == GLFW.GLFW_KEY_PAGE_DOWN ? 1 : -1)
                    * Math.max(24, contentBottom - contentTop - 24);
            positionContent();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void addFooterButtons() {
        int gap = 6;
        int available = panelWidth - 24;
        int buttonWidth = Math.min(92, (available - gap * 2) / 3);
        int buttonsWidth = buttonWidth * 3 + gap * 2;
        int x = panelLeft + (panelWidth - buttonsWidth) / 2;
        addRenderableWidget(Button.builder(Component.translatable("gui.ysmragdoll.confirm"),
                ignored -> saveAndClose()).bounds(x, footerTop, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.ysmragdoll.apply"),
                ignored -> apply()).bounds(x + buttonWidth + gap, footerTop, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.ysmragdoll.cancel"),
                ignored -> cancel()).bounds(x + (buttonWidth + gap) * 2, footerTop,
                buttonWidth, 20).build());
    }

    private EditBox integerField(int x, int y, int fieldWidth, int value,
                                 int maximumLength, String narrationKey) {
        EditBox field = new EditBox(font, x, y, fieldWidth, 20,
                Component.translatable(narrationKey));
        field.setMaxLength(maximumLength);
        field.setFilter(text -> text.isEmpty() || text.chars().allMatch(Character::isDigit));
        field.setValue(inputText.getOrDefault(narrationKey, Integer.toString(value)));
        field.setResponder(text -> {
            inputText.put(narrationKey, text);
            clearStatus();
        });
        return contentWidget(field, y);
    }

    private EditBox decimalField(int x, int y, int fieldWidth, double value,
                                  String narrationKey, String axis) {
        EditBox field = new EditBox(font, x, y, fieldWidth, 20,
                Component.translatable(narrationKey).append(" " + axis));
        field.setMaxLength(16);
        field.setFilter(YsmRagdollSettingsScreen::isPotentialDecimal);
        String key = narrationKey + "." + axis;
        field.setValue(inputText.getOrDefault(key, formatOffset(value)));
        field.setResponder(text -> {
            inputText.put(key, text);
            clearStatus();
        });
        return contentWidget(field, y);
    }

    private void switchTo(Category target) {
        if (target == category || !captureVisibleValues()) {
            return;
        }
        category = target;
        scroll = 0;
        clearStatus();
        clearWidgets();
        init();
    }

    private boolean captureVisibleValues() {
        resetInputColors();
        try {
            if (category == Category.GENERAL) {
                draft.lifetimeSeconds = readInteger(lifetime, 0, Integer.MAX_VALUE,
                        "screen.ysmragdoll.general.lifetime");
                draft.maximumRagdolls = readInteger(maximum, 0, YsmRagdollConfig.RAGDOLL_LIMIT,
                        "screen.ysmragdoll.general.maximum");
            } else if (category == Category.ADVANCED) {
                draft.easyPushIndex = readInteger(easyPush, 0, 100,
                        "screen.ysmragdoll.advanced.easy_push");
                draft.groundFriction = readInteger(groundFriction, 0, 100,
                        "screen.ysmragdoll.advanced.friction");
                draft.explosionPushIndex = readInteger(explosionPush, 0, 100,
                        "screen.ysmragdoll.advanced.explosion_push");
                for (int axis = 0; axis < 3; axis++) {
                    draft.renderOffsets[axis] = readOffset(renderOffsets[axis]);
                    draft.collisionOffsets[axis] = readOffset(collisionOffsets[axis]);
                }
            }
            return true;
        } catch (InvalidValue ignored) {
            return false;
        }
    }

    private int readInteger(EditBox field, int minimum, int maximum, String labelKey)
            throws InvalidValue {
        try {
            int value = Integer.parseInt(field.getValue());
            if (value < minimum || value > maximum) {
                throw reject(field, labelKey, minimum + " - " + maximum);
            }
            return value;
        } catch (NumberFormatException exception) {
            throw reject(field, labelKey, minimum + " - " + maximum);
        }
    }

    private double readOffset(EditBox field) throws InvalidValue {
        try {
            double value = Double.parseDouble(field.getValue());
            if (!Double.isFinite(value) || value < MIN_OFFSET || value > MAX_OFFSET) {
                throw reject(field, "screen.ysmragdoll.offset", "-4.0 - 4.0");
            }
            return value;
        } catch (NumberFormatException exception) {
            throw reject(field, "screen.ysmragdoll.offset", "-4.0 - 4.0");
        }
    }

    private InvalidValue reject(EditBox field, String labelKey, String range) {
        status = Component.translatable("screen.ysmragdoll.invalid",
                Component.translatable(labelKey), range);
        statusIsError = true;
        for (PositionedWidget entry : contentWidgets) {
            if (entry.widget() == field) {
                scroll = entry.y() - font.lineHeight - 6;
                positionContent();
                break;
            }
        }
        field.setTextColor(ERROR_COLOR);
        setFocused(field);
        return new InvalidValue();
    }

    private void saveAndClose() {
        if (save()) {
            cancel();
        }
    }

    private void apply() {
        if (save()) {
            status = Component.translatable("screen.ysmragdoll.applied");
            statusIsError = false;
            positionContent();
        }
    }

    private boolean save() {
        if (!captureVisibleValues()) {
            return false;
        }
        YsmRagdollConfig.LIFETIME_SECONDS.set(draft.lifetimeSeconds);
        YsmRagdollConfig.MANUAL_REMOVAL.set(draft.manualRemoval);
        YsmRagdollConfig.MAX_RAGDOLLS.set(draft.maximumRagdolls);
        YsmRagdollConfig.EASY_PUSH_INDEX.set(draft.easyPushIndex);
        YsmRagdollConfig.GROUND_FRICTION.set(draft.groundFriction);
        YsmRagdollConfig.EXPLOSION_IMPACT_INDEX.set(draft.explosionPushIndex);
        YsmRagdollConfig.SHOW_COLLISION_BOXES.set(draft.showCollisionBoxes);
        YsmRagdollConfig.INTENSIVE_TEST.set(draft.intensiveTest);
        YsmRagdollConfig.RENDER_OFFSET_X.set(draft.renderOffsets[0]);
        YsmRagdollConfig.RENDER_OFFSET_Y.set(draft.renderOffsets[1]);
        YsmRagdollConfig.RENDER_OFFSET_Z.set(draft.renderOffsets[2]);
        YsmRagdollConfig.COLLISION_OFFSET_X.set(draft.collisionOffsets[0]);
        YsmRagdollConfig.COLLISION_OFFSET_Y.set(draft.collisionOffsets[1]);
        YsmRagdollConfig.COLLISION_OFFSET_Z.set(draft.collisionOffsets[2]);
        YsmRagdollConfig.SPEC.save();
        ClientIntensiveLogger.update();
        YsmRagdollLog.info("保存设置: 存在时间=" + draft.lifetimeSeconds
                + ", 手动清除=" + draft.manualRemoval
                + ", 数量上限=" + draft.maximumRagdolls
                + ", 易推动=" + draft.easyPushIndex
                + ", 地面摩擦=" + draft.groundFriction
                + ", 爆炸推动=" + draft.explosionPushIndex
                + ", 显示碰撞箱=" + draft.showCollisionBoxes
                + ", 密集测试=" + draft.intensiveTest);
        return true;
    }

    private void resetWidgetReferences() {
        lifetime = null;
        maximum = null;
        easyPush = null;
        groundFriction = null;
        explosionPush = null;
        for (int axis = 0; axis < 3; axis++) {
            renderOffsets[axis] = null;
            collisionOffsets[axis] = null;
        }
    }

    private void resetInputColors() {
        for (EditBox field : new EditBox[]{lifetime, maximum, easyPush, groundFriction,
                explosionPush, renderOffsets[0], renderOffsets[1], renderOffsets[2],
                collisionOffsets[0], collisionOffsets[1], collisionOffsets[2]}) {
            if (field != null) {
                field.setTextColor(0xFFE0E0E0);
            }
        }
    }

    private void clearStatus() {
        status = Component.empty();
        statusIsError = false;
        positionContent();
    }

    private void cancel() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public void onClose() {
        cancel();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.fill(panelLeft, panelTop, panelLeft + panelWidth, panelTop + panelHeight,
                PANEL_COLOR);
        graphics.fill(panelLeft, panelTop + 38, panelLeft + navigationWidth,
                panelTop + panelHeight - 38, NAVIGATION_COLOR);
        graphics.fill(panelLeft + navigationWidth, panelTop + 38, panelLeft + panelWidth,
                panelTop + panelHeight - 38, CONTENT_COLOR);
        graphics.fill(panelLeft + navigationWidth, panelTop + 38,
                panelLeft + navigationWidth + 1, panelTop + panelHeight - 38, DIVIDER_COLOR);
        graphics.drawCenteredString(font, title, panelLeft + panelWidth / 2,
                panelTop + 14, 0xFFFFFFFF);
        graphics.drawString(font, category.title, contentLeft, panelTop + 31,
                LABEL_COLOR, false);

        graphics.enableScissor(contentLeft - 1, contentTop - 1,
                panelLeft + panelWidth - 12, contentBottom + 1);
        for (Label label : labels) {
            graphics.drawString(font, label.text(), label.x(),
                    contentTop + label.y() - scroll, LABEL_COLOR, false);
        }
        for (PositionedWidget entry : contentWidgets) {
            entry.widget().render(graphics, mouseX, mouseY, partialTick);
        }
        graphics.disableScissor();
        if (maximumScroll() > 0) {
            int thumbHeight = scrollbarThumbHeight();
            int thumbTop = scrollbarThumbTop();
            int trackLeft = panelLeft + panelWidth - 8;
            graphics.fill(trackLeft, contentTop, trackLeft + 3, contentBottom, DIVIDER_COLOR);
            graphics.fill(trackLeft, thumbTop, trackLeft + 3, thumbTop + thumbHeight, MUTED_COLOR);
        }
        if (!status.getString().isEmpty()) {
            List<FormattedCharSequence> lines = font.split(status, panelWidth - 24);
            int y = footerTop - 6 - lines.size() * font.lineHeight;
            for (FormattedCharSequence line : lines) {
                graphics.drawString(font, line, panelLeft + 12, y,
                        statusIsError ? ERROR_COLOR : SUCCESS_COLOR, false);
                y += font.lineHeight;
            }
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private record Label(FormattedCharSequence text, int x, int y) {
    }

    private record PositionedWidget(AbstractWidget widget, int y) {
    }

    private static boolean isPotentialDecimal(String text) {
        if (text.isEmpty() || "-".equals(text)) {
            return true;
        }
        try {
            Double.parseDouble(text);
            return text.indexOf('e') < 0 && text.indexOf('E') < 0;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static String formatOffset(double value) {
        return Double.toString(value == -0.0 ? 0.0 : value);
    }

    private enum Category {
        GENERAL(Component.translatable("screen.ysmragdoll.category.general")),
        ADVANCED(Component.translatable("screen.ysmragdoll.category.advanced")),
        MANAGEMENT(Component.translatable("screen.ysmragdoll.category.management")),
        TESTING(Component.translatable("screen.ysmragdoll.category.testing"));

        private final Component title;

        Category(Component title) {
            this.title = title;
        }
    }

    private static final class Draft {
        private int lifetimeSeconds;
        private boolean manualRemoval;
        private int maximumRagdolls;
        private int easyPushIndex;
        private int groundFriction;
        private int explosionPushIndex;
        private boolean showCollisionBoxes;
        private boolean intensiveTest;
        private final double[] renderOffsets = new double[3];
        private final double[] collisionOffsets = new double[3];

        private static Draft load() {
            Draft value = new Draft();
            value.lifetimeSeconds = YsmRagdollConfig.LIFETIME_SECONDS.get();
            value.manualRemoval = YsmRagdollConfig.MANUAL_REMOVAL.get();
            value.maximumRagdolls = YsmRagdollConfig.MAX_RAGDOLLS.get();
            value.easyPushIndex = YsmRagdollConfig.EASY_PUSH_INDEX.get();
            value.groundFriction = YsmRagdollConfig.GROUND_FRICTION.get();
            value.explosionPushIndex = YsmRagdollConfig.EXPLOSION_IMPACT_INDEX.get();
            value.showCollisionBoxes = YsmRagdollConfig.SHOW_COLLISION_BOXES.get();
            value.intensiveTest = YsmRagdollConfig.INTENSIVE_TEST.get();
            value.renderOffsets[0] = YsmRagdollConfig.RENDER_OFFSET_X.get();
            value.renderOffsets[1] = YsmRagdollConfig.RENDER_OFFSET_Y.get();
            value.renderOffsets[2] = YsmRagdollConfig.RENDER_OFFSET_Z.get();
            value.collisionOffsets[0] = YsmRagdollConfig.COLLISION_OFFSET_X.get();
            value.collisionOffsets[1] = YsmRagdollConfig.COLLISION_OFFSET_Y.get();
            value.collisionOffsets[2] = YsmRagdollConfig.COLLISION_OFFSET_Z.get();
            return value;
        }
    }

    private static final class InvalidValue extends Exception {
    }
}
