package com.ysmragdoll.client;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ysmragdoll.config.YsmRagdollConfig;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.junit.jupiter.api.Test;

import java.awt.Rectangle;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class SettingsScreenLayoutTest {
    @Test
    public void translatedLabelsNeverIntersectInputsAcrossGuiSizes() throws Exception {
        Language original = Language.getInstance();
        try {
            for (String language : List.of("zh_cn", "en_us")) {
                loadLanguage(language);
                for (int[] size : new int[][]{{320, 240}, {426, 240}, {640, 360}, {854, 480}}) {
                    for (int category = 0; category < 4; category++) {
                        YsmRagdollSettingsScreen screen = screen(size[0], size[1], category);
                        String context = language + " " + size[0] + "x" + size[1] + " " + category;
                        List<Rectangle> textBounds = new ArrayList<>();
                        Font font = (Font) field(Screen.class, "font").get(screen);
                        for (Object label : (List<?>) field(screen.getClass(), "labels").get(screen)) {
                            FormattedCharSequence text = (FormattedCharSequence) invoke(label, "text");
                            textBounds.add(new Rectangle((int) invoke(label, "x"),
                                    (int) invoke(label, "y"), font.width(text), font.lineHeight));
                        }
                        int contentLeft = (int) field(screen.getClass(), "contentLeft").get(screen);
                        int contentWidth = (int) field(screen.getClass(), "contentWidth").get(screen);
                        for (Object entry : (List<?>) field(screen.getClass(), "contentWidgets").get(screen)) {
                            AbstractWidget widget = (AbstractWidget) invoke(entry, "widget");
                            Rectangle box = new Rectangle(widget.getX() - 1, (int) invoke(entry, "y") - 1,
                                    widget.getWidth() + 2, widget.getHeight() + 2);
                            for (Rectangle text : textBounds) {
                                assertFalse(context + ": " + text + " overlaps " + box, text.intersects(box));
                            }
                            assertTrue(context, widget.getX() >= contentLeft);
                            assertTrue(context, widget.getX() + widget.getWidth() <= contentLeft + contentWidth);
                        }
                        for (int i = 0; i < textBounds.size(); i++) {
                            for (int j = i + 1; j < textBounds.size(); j++) {
                                assertFalse(context, textBounds.get(i).intersects(textBounds.get(j)));
                            }
                        }
                        assertTrue(context, (int) field(screen.getClass(), "footerTop").get(screen) + 20 <= size[1]);
                    }
                }
            }
        } finally {
            Language.inject(original);
        }
    }

    @Test
    public void tractionStrengthValidatesRangeAndPreservesUnsavedEditsOnResize() throws Exception {
        YsmRagdollSettingsScreen screen = screen(640, 360, 3);
        EditBox input = (EditBox) field(screen.getClass(), "tractionStrength").get(screen);
        assertEquals("70", input.getValue());
        input.setValue("101");
        assertEquals(false, invoke(screen, "captureVisibleValues"));
        input.setValue("0");
        assertEquals(true, invoke(screen, "captureVisibleValues"));
        input.setValue("100");
        assertEquals(true, invoke(screen, "captureVisibleValues"));
        input.setValue("42");
        invoke(screen, "clearWidgets", Screen.class);
        screen.width = 320;
        screen.init();
        assertEquals("42", ((EditBox) field(screen.getClass(), "tractionStrength").get(screen)).getValue());
        assertEquals("Editing must not save before Apply", 70, YsmRagdollConfig.TRACTION_STRENGTH.get().intValue());
    }

    @Test
    public void rebuildingForResizePreservesUnsubmittedText() throws Exception {
        YsmRagdollSettingsScreen screen = screen(640, 360, true);
        EditBox input = (EditBox) field(screen.getClass(), "easyPush").get(screen);
        input.setValue("100");
        invoke(screen, "clearWidgets", Screen.class);
        screen.width = 320;
        screen.height = 240;
        screen.init();
        EditBox rebuilt = (EditBox) field(screen.getClass(), "easyPush").get(screen);
        assertEquals("100", rebuilt.getValue());
    }

    @Test
    public void invalidInputIsVisibleAndErrorDoesNotOverlapContent() throws Exception {
        Language original = Language.getInstance();
        try {
            for (String language : List.of("zh_cn", "en_us")) {
                loadLanguage(language);
                YsmRagdollSettingsScreen screen = screen(320, 240, false);
                EditBox input = (EditBox) field(screen.getClass(), "lifetime").get(screen);
                input.setValue("9999999999");
                assertEquals(false, invoke(screen, "captureVisibleValues"));
                assertTrue(input.visible);
                assertSame(input, screen.getFocused());
                Component status = (Component) field(screen.getClass(), "status").get(screen);
                int panelWidth = (int) field(screen.getClass(), "panelWidth").get(screen);
                int footerTop = (int) field(screen.getClass(), "footerTop").get(screen);
                int contentBottom = (int) field(screen.getClass(), "contentBottom").get(screen);
                Font font = (Font) field(Screen.class, "font").get(screen);
                int errorTop = footerTop - 6 - font.split(status, panelWidth - 24).size() * font.lineHeight;
                assertTrue(contentBottom < errorTop);
            }
        } finally {
            Language.inject(original);
        }
    }

    @Test
    public void scrollbarDragReachesBothEnds() throws Exception {
        YsmRagdollSettingsScreen screen = screen(320, 240, true);
        int top = (int) field(screen.getClass(), "contentTop").get(screen);
        int bottom = (int) field(screen.getClass(), "contentBottom").get(screen);
        int x = (int) field(screen.getClass(), "panelLeft").get(screen)
                + (int) field(screen.getClass(), "panelWidth").get(screen) - 6;
        assertTrue(screen.mouseClicked(x, top + 1, 0));
        assertTrue(screen.mouseDragged(x, bottom, 0, 0, bottom - top));
        assertEquals(invoke(screen, "maximumScroll"), field(screen.getClass(), "scroll").get(screen));
        assertTrue(screen.mouseDragged(x, top, 0, 0, top - bottom));
        assertEquals(0, field(screen.getClass(), "scroll").get(screen));
        assertTrue(screen.mouseReleased(x, top, 0));
    }

    @Test
    public void spawnFeedbackLeavesControlsAndUnsavedInputsUsable() throws Exception {
        Language original = Language.getInstance();
        try {
            for (String language : List.of("zh_cn", "en_us")) {
                loadLanguage(language);
                for (int[] size : new int[][]{{320, 240}, {640, 360}}) {
                    YsmRagdollSettingsScreen screen = screen(size[0], size[1], true);
                    EditBox input = (EditBox) field(screen.getClass(), "easyPush").get(screen);
                    input.setValue("87");
                    for (String outcome : List.of("created", "static_created", "capture_failed", "disabled", "below_void")) {
                        Component status = Component.translatable("screen.ysmragdoll.spawn." + outcome, 2, 1);
                        field(screen.getClass(), "status").set(screen, status);
                        invoke(screen, "positionContent");
                        Font font = (Font) field(Screen.class, "font").get(screen);
                        int panelWidth = (int) field(screen.getClass(), "panelWidth").get(screen);
                        int footerTop = (int) field(screen.getClass(), "footerTop").get(screen);
                        int messageTop = footerTop - 6 - font.split(status, panelWidth - 24).size() * font.lineHeight;
                        int contentTop = (int) field(screen.getClass(), "contentTop").get(screen);
                        int contentBottom = (int) field(screen.getClass(), "contentBottom").get(screen);
                        assertTrue(contentBottom > contentTop);
                        assertTrue(contentBottom < messageTop);
                        for (Object entry : (List<?>) field(screen.getClass(), "contentWidgets").get(screen)) {
                            AbstractWidget widget = (AbstractWidget) invoke(entry, "widget");
                            if (widget.visible) assertTrue(widget.getY() + widget.getHeight() < messageTop);
                        }
                        assertEquals("87", input.getValue());
                    }
                }
            }
        } finally {
            Language.inject(original);
        }
    }

    private static YsmRagdollSettingsScreen screen(int width, int height, boolean advanced) throws Exception {
        return screen(width, height, advanced ? 1 : 0);
    }

    private static YsmRagdollSettingsScreen screen(int width, int height, int categoryIndex) throws Exception {
        CommentedConfig config = CommentedConfig.inMemory();
        YsmRagdollConfig.SPEC.correct(config);
        YsmRagdollConfig.SPEC.setConfig(config);
        YsmRagdollSettingsScreen screen = new YsmRagdollSettingsScreen(null);
        screen.width = width;
        screen.height = height;
        field(Screen.class, "font").set(screen, new LayoutFont());
        Field category = field(screen.getClass(), "category");
        category.set(screen, category.getType().getEnumConstants()[categoryIndex]);
        screen.init();
        return screen;
    }

    @Test
    public void managementShowsExpiryModesAndCanScrollAfterTheListShrinks() throws Exception {
        Language original = Language.getInstance();
        try {
            loadLanguage("zh_cn");
            YsmRagdollSettingsScreen screen = screen(320, 240, 2);
            Method update = screen.getClass().getDeclaredMethod("addManagementLabels", List.class);
            update.setAccessible(true);
            String uuid = "12345678-1234-1234-1234-123456789abc";
            update.invoke(screen, List.of(
                    new ClientRagdollManager.ManagementEntry(1, uuid, 1001, false),
                    new ClientRagdollManager.ManagementEntry(2, uuid, -1, true),
                    new ClientRagdollManager.ManagementEntry(3, uuid, -1, false)));
            StringBuilder text = new StringBuilder();
            for (Object label : (List<?>) field(screen.getClass(), "labels").get(screen)) {
                ((FormattedCharSequence) invoke(label, "text")).accept((index, style, codepoint) -> {
                    text.appendCodePoint(codepoint);
                    return true;
                });
            }
            assertTrue(text.toString().contains("当前世界布娃娃：3 具"));
            assertTrue(text.toString().contains("消失倒计时：2 秒"));
            assertTrue(text.toString().contains("已开启手动清除"));
            assertTrue(text.toString().contains("已设置永久存在"));
            // Applying settings on this category must not try to read absent advanced fields.
            assertEquals(true, invoke(screen, "captureVisibleValues"));
            assertTrue((int) invoke(screen, "maximumScroll") > 0);
            field(screen.getClass(), "scroll").set(screen, 10000);
            update.invoke(screen, List.of());
            invoke(screen, "positionContent");
            assertEquals(0, field(screen.getClass(), "scroll").get(screen));
        } finally {
            Language.inject(original);
        }
    }

    private static void loadLanguage(String name) throws Exception {
        try (var stream = SettingsScreenLayoutTest.class.getResourceAsStream(
                "/assets/ysmragdoll/lang/" + name + ".json")) {
            assertNotNull(stream);
            JsonObject translations = JsonParser.parseReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            Language.inject(new Language() {
                public String getOrDefault(String key, String fallback) {
                    return translations.has(key) ? translations.get(key).getAsString() : fallback;
                }
                public boolean has(String key) { return translations.has(key); }
                public boolean isDefaultRightToLeft() { return false; }
                public FormattedCharSequence getVisualOrder(FormattedText text) {
                    return FormattedCharSequence.forward(text.getString(), Style.EMPTY);
                }
            });
        }
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static Object invoke(Object target, String name) throws Exception {
        return invoke(target, name, target.getClass());
    }

    private static Object invoke(Object target, String name, Class<?> type) throws Exception {
        Method method = type.getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(target);
    }

    /** Conservative glyph advances; exercise the actual screen layout without a GPU. */
    private static final class LayoutFont extends Font {
        private final StringSplitter splitter = new StringSplitter((codepoint, style) -> codepoint >= 256 ? 9 : 6);

        private LayoutFont() { super(ignored -> null, false); }

        @Override
        public List<FormattedCharSequence> split(FormattedText text, int width) {
            return splitter.splitLines(text, width, Style.EMPTY).stream()
                    .map(Language.getInstance()::getVisualOrder).toList();
        }

        @Override
        public String plainSubstrByWidth(String text, int width) {
            return splitter.plainHeadByWidth(text, width, Style.EMPTY);
        }

        @Override
        public int width(FormattedCharSequence text) {
            return (int) splitter.stringWidth(text);
        }
    }
}
