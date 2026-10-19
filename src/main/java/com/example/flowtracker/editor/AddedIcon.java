package com.example.flowtracker.editor;

import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.JBUI;

import javax.swing.Icon;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;

/**
 * Green rounded badge on lines that are already added: the line's step number in the flow (in the order
 * the panel shows it), or a white plus when there is no number. Wider for 2- and 3-digit steps.
 */
final class AddedIcon implements Icon {
    static final Icon INSTANCE = new AddedIcon(0);

    private static final Color GREEN = new Color(0x2E, 0xA0, 0x43);

    /** 1-based step, or 0 for the plain plus. */
    private final int step;

    AddedIcon(int step) {
        this.step = step;
    }

    private static Font font() {
        return JBUI.Fonts.label().deriveFont(Font.BOLD, (float) JBUI.scale(9));
    }

    private int boxWidth() {
        if (step <= 0) return JBUI.scale(12);
        FontMetrics fm = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).getGraphics().getFontMetrics(font());
        return Math.max(JBUI.scale(12), fm.stringWidth(String.valueOf(step)) + JBUI.scale(5));
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

            int w = boxWidth();
            int h = JBUI.scale(12);
            int bx = x + (getIconWidth() - w) / 2;
            int by = y + (getIconHeight() - h) / 2;
            g2.setColor(GREEN);
            g2.fillRoundRect(bx, by, w, h, JBUI.scale(4), JBUI.scale(4));
            g2.setColor(Color.WHITE);

            if (step > 0) {
                g2.setFont(font());
                FontMetrics fm = g2.getFontMetrics();
                String s = String.valueOf(step);
                g2.drawString(s, bx + (w - fm.stringWidth(s)) / 2f, by + (h - fm.getHeight()) / 2f + fm.getAscent());
                return;
            }
            g2.setStroke(new BasicStroke(JBUIScale.scale(1.6f), BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
            float cx = x + getIconWidth() / 2f;
            float cy = y + getIconHeight() / 2f;
            float half = h * 0.28f;
            g2.draw(new Line2D.Float(cx - half, cy, cx + half, cy));
            g2.draw(new Line2D.Float(cx, cy - half, cx, cy + half));
        } finally {
            g2.dispose();
        }
    }

    @Override public int getIconWidth() { return Math.max(JBUI.scale(16), boxWidth() + JBUI.scale(2)); }
    @Override public int getIconHeight() { return JBUI.scale(16); }
}
