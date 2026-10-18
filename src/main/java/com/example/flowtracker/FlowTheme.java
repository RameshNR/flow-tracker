package com.example.flowtracker;

import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorFontType;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.JBColor;
import com.intellij.util.IconUtil;
import com.intellij.util.ui.JBUI;

import javax.swing.Icon;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/** Colors, fonts and icons shared by the Flow Tracker tool window. Light value first, dark value second. */
final class FlowTheme {
    private FlowTheme() {}

    // ----- surfaces -----
    static final Color PANEL_BG = new JBColor(0xF2F3F5, 0x151A23);
    static final Color CARD_BG = new JBColor(0xFFFFFF, 0x19202B);
    static final Color CARD_BORDER = new JBColor(0xDFE1E5, 0x283041);

    // ----- buttons -----
    static final Color ACCENT = new JBColor(0x3574F0, 0x2F6FED);
    static final Color ACCENT_HOVER = new JBColor(0x4682FA, 0x4581F5);
    static final Color ACCENT_PRESSED = new JBColor(0x2A62D4, 0x265FD0);
    static final Color BTN_BG = new JBColor(0xFFFFFF, 0x1E2532);
    static final Color BTN_HOVER = new JBColor(0xEDEEF2, 0x273042);
    static final Color BTN_PRESSED = new JBColor(0xE1E3E8, 0x2F3A4F);
    static final Color BTN_BORDER = new JBColor(0xD3D5DB, 0x323C50);
    static final Color BTN_BORDER_DISABLED = new JBColor(0xE4E5E9, 0x252C3A);
    static final Color SEGMENT_BG = new JBColor(0xE9EBEF, 0x1C2230);
    static final Color FG = new JBColor(0x1E1F22, 0xDFE3EA);
    static final Color FG_MUTED = new JBColor(0x6C707E, 0x8B93A3);
    static final Color FG_DISABLED = new JBColor(0xA8ADBD, 0x515A6B);
    static final Color DIVIDER = new JBColor(0xD3D5DB, 0x2C3546);

    // ----- tree -----
    static final Color GUIDE = new JBColor(0xD3D5DB, 0x2E3748);
    static final Color CODE_BG = new JBColor(0xF7F8FA, 0x161C26);
    static final Color CODE_BORDER = new JBColor(0xE1E3E8, 0x283142);
    static final Color ROW_HOVER = new JBColor(new Color(0, 0, 0, 12), new Color(255, 255, 255, 12));
    static final Color SELECTION = new JBColor(0xDCE6FB, 0x173564);
    static final Color SELECTION_BAR = new JBColor(0x3574F0, 0x3B82F6);
    // ----- move up / down buttons on the hovered row -----
    static final Color ARROWS_BG = new JBColor(0xFFFFFF, 0x232B3A);
    static final Color ARROWS_BORDER = new JBColor(0xC9CCD6, 0x3A4558);
    static final Color ARROWS_SHADOW = new JBColor(new Color(0, 0, 0, 28), new Color(0, 0, 0, 90));
    static final Color ARROW_FG = new JBColor(0x1E1F22, 0xFFFFFF);
    static final Color ARROW_FG_DISABLED = new JBColor(0xB4B8C4, 0x566072);
    static final Color ARROW_HOVER = ACCENT;
    static final Color DANGER = new JBColor(0xE55765, 0xD9534F);
    /** Saved line that no longer matches the code. */
    static final Color WARNING = new JBColor(0xC77D0A, 0xE0A33A);
    /** Recording with the debugger. */
    static final Color RECORDING = new JBColor(0xD64550, 0xE5636D);
    static final Color ARROW_FG_HOVER = new JBColor(0xFFFFFF, 0xFFFFFF);

    // ----- project tag next to file names -----
    static final Color TAG_BG = new JBColor(0xE6EDFC, 0x1E2C46);
    static final Color TAG_FG = new JBColor(0x2F5BB7, 0xA4BFF5);
    static final Color DROP_LINE = new JBColor(0x3574F0, 0x548AF7);

    // ----- code -----
    static final Color LINE_NO = new JBColor(0x3574F0, 0x6F8FD0);
    static final Color CODE_FG = new JBColor(0x1E1F22, 0xD5DAE3);
    static final Color KEYWORD = new JBColor(0x0033B3, 0x6E9BFF);
    static final Color TYPE = new JBColor(0x1F6FBF, 0x5AB0FF);
    static final Color STRING = new JBColor(0x067D17, 0xE8B562);
    static final Color NUMBER = new JBColor(0x1750EB, 0x2AACB8);
    static final Color COMMENT = new JBColor(0x8C8C8C, 0x6F7787);

    // ----- toolbar icons (single grey SVGs, recolored at paint time) -----
    static final Icon ICON_GROUPED = icon("tb_grouped");
    static final Icon ICON_LIST = icon("tb_list");
    static final Icon ICON_COPY = icon("tb_copy");
    static final Icon ICON_DELETE = icon("tb_delete");
    static final Icon ICON_CLEAR = icon("tb_clear");
    static final Icon ICON_NOTE = icon("tb_note");
    static final Icon ICON_UP = icon("tb_up");
    static final Icon ICON_DOWN = icon("tb_down");
    static final Icon ICON_SETTINGS = icon("tb_settings");
    static final Icon ICON_SYNC = icon("tb_sync");
    static final Icon ICON_MARKDOWN = icon("tb_markdown");
    static final Icon ICON_INFO = icon("tb_info");
    static final Icon ICON_UNDO = icon("tb_undo");
    static final Icon ICON_REDO = icon("tb_redo");
    static final Icon ICON_FLOWS = icon("tb_flows");
    static final Icon ICON_EXPORT = icon("tb_export");
    static final Icon ICON_IMPORT = icon("tb_import");
    static final Icon ICON_RECORD = icon("tb_record");
    static final Icon ICON_PREV = icon("tb_prev");
    static final Icon ICON_NEXT = icon("tb_next");
    static final Icon ICON_TAG = icon("tb_tag");
    static final Icon ICON_WARNING = icon("tb_warning");

    /** The grey SVG icon painted in exactly the given color (theme colors resolve at paint time). */
    static Icon tinted(Icon icon, Color color) {
        return new TintedIcon(icon, color);
    }

    /**
     * Paints the icon's shape filled with one solid color. The icon is rendered at the screen's pixel
     * density into an offscreen image, so it stays sharp on HiDPI displays.
     */
    private record TintedIcon(Icon base, Color color) implements Icon {
        private static final Map<String, BufferedImage> CACHE = new HashMap<>();

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g;
            double scale = Math.max(1.0, g2.getTransform().getScaleX());
            String key = System.identityHashCode(base) + ":" + color.getRGB() + ":" + scale;
            BufferedImage img = CACHE.computeIfAbsent(key, k -> render(c, scale));
            g2.drawImage(img, x, y, getIconWidth(), getIconHeight(), null);
        }

        private BufferedImage render(Component c, double scale) {
            Icon big = scale == 1.0 ? base : IconUtil.scale(base, null, (float) scale);
            int w = Math.max(1, big.getIconWidth()), h = Math.max(1, big.getIconHeight());
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D ig = img.createGraphics();
            try {
                big.paintIcon(c, ig, 0, 0);
                ig.setComposite(AlphaComposite.SrcIn); // keep the shape, replace its color
                ig.setColor(color);
                ig.fillRect(0, 0, w, h);
            } finally {
                ig.dispose();
            }
            return img;
        }

        @Override public int getIconWidth() { return base.getIconWidth(); }
        @Override public int getIconHeight() { return base.getIconHeight(); }
    }

    private static Icon icon(String name) {
        return IconLoader.getIcon("/icons/" + name + ".svg", FlowTheme.class);
    }

    /** Space between a toolbar button and the popup it opens (small, so the popup still reads as the button's). */
    static int popupGap() {
        return JBUI.scale(4);
    }

    /** Editor font at the size of the UI label font, so code rows line up with the rest of the tool window. */
    static Font codeFont() {
        Font editor = EditorColorsManager.getInstance().getGlobalScheme().getFont(EditorFontType.PLAIN);
        return editor.deriveFont((float) JBUI.Fonts.label().getSize());
    }
}
