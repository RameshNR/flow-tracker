package com.example.flowtracker.diagram;

import org.jetbrains.annotations.Nullable;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.CubicCurve2D;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Draws a {@link FlowDiagram}: the class diagram (boxes with their methods, arrows for calls) and the
 * sequence diagram, on screen and as PNG images. Plain AWT in diagram units (unscaled pixels), so the Flow
 * Diagram window, the copied images and the tests draw the same thing.
 */
public final class FlowDiagramPainter {

    /** Colors of one look: the window uses the theme's (or {@link #LIGHT} / {@link #DARK}), images LIGHT or DARK. */
    record Palette(Color background, Color boxBg, Color boxBorder, Color headerBg, Color title, Color muted,
                   Color text, Color added, Color custom, Color codeCall, Color manualCall, Color selection, Color noteBg,
                   Color noteBorder, Color lifeline) {}

    static final Palette LIGHT = new Palette(
            Color.WHITE, Color.WHITE, new Color(0xC9CCD6), new Color(0xEEF3FE), new Color(0x1E1F22),
            new Color(0x6C707E), new Color(0x1E1F22), new Color(0x208A3C), new Color(0xC77D0A), new Color(0x6C707E),
            new Color(0x3574F0), new Color(0x3574F0), new Color(0xFFF6D6), new Color(0xE0C46C),
            new Color(0xA8ADBD));

    static final Palette DARK = new Palette(
            new Color(0x1E1F22), new Color(0x2B2D30), new Color(0x4E5157), new Color(0x2E3A52), new Color(0xDFE1E5),
            new Color(0x8C8F96), new Color(0xDFE1E5), new Color(0x5FB865), new Color(0xE0A33A), new Color(0x8C8F96),
            new Color(0x548AF7), new Color(0x548AF7), new Color(0x3D3522), new Color(0x8A7432),
            new Color(0x5A5D63));

    static final double HEADER = 42, HEADER_NEW = 28, ROW = 24, PAD_BOTTOM = 8, MIN_WIDTH = 170;
    /** A line of a note, and how wide a note gets before it wraps. */
    static final double NOTE_LINE = 15, NOTE_WIDTH = 300;
    private static final double MARGIN = 24;

    private final Font font;
    private final Font bold;
    private final Font small;
    private final FontMetrics fm, boldFm, smallFm;
    /** Measures text in the fonts that formats ask for (bold, italic). */
    private final Graphics2D measure;
    private final Map<Font, FontMetrics> metrics = new HashMap<>();

    /** @param base the font to use; its size is ignored (12 units are used) */
    FlowDiagramPainter(Font base) {
        font = base.deriveFont(Font.PLAIN, 12f);
        bold = base.deriveFont(Font.BOLD, 13f);
        small = base.deriveFont(Font.PLAIN, 11f);
        BufferedImage scratch = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = scratch.createGraphics();
        fm = g.getFontMetrics(font);
        boldFm = g.getFontMetrics(bold);
        smallFm = g.getFontMetrics(small);
        measure = g; // kept for measuring formatted text
    }

    private FontMetrics metrics(Font f) {
        return metrics.computeIfAbsent(f, measure::getFontMetrics);
    }

    /** The font with a format's bold / italic / underline / strike-through, or the usual style without a format. */
    static Font styled(Font f, @Nullable FlowDiagram.Format format, int usual) {
        int style = format == null ? usual : (format.bold() ? Font.BOLD : 0) | (format.italic() ? Font.ITALIC : 0);
        Font out = style == f.getStyle() ? f : f.deriveFont(style);
        if (format != null && (format.underline() || format.strike())) {
            Map<java.awt.font.TextAttribute, Object> a = new HashMap<>();
            if (format.underline()) a.put(java.awt.font.TextAttribute.UNDERLINE, java.awt.font.TextAttribute.UNDERLINE_ON);
            if (format.strike()) a.put(java.awt.font.TextAttribute.STRIKETHROUGH, java.awt.font.TextAttribute.STRIKETHROUGH_ON);
            out = out.deriveFont(a);
        }
        return out;
    }

    /**
     * The colors for a box: on a background of its own, the text turns dark or light (and the header, border and
     * badges follow), so it stays readable whatever background is picked.
     */
    static Palette boxPalette(Palette p, @Nullable Color fill) {
        if (fill == null) return p;
        boolean dark = FlowDiagram.isDark(fill);
        Color text = dark ? new Color(0xF2F4F7) : new Color(0x1E1F22);
        return new Palette(p.background(), fill, mix(text, fill, 0.22), mix(text, fill, 0.07), text, mix(text, fill, 0.62),
                text, dark ? new Color(0x7BD88F) : new Color(0x1E7B34), dark ? new Color(0xF0B354) : new Color(0xA86500),
                p.codeCall(), p.manualCall(), p.selection(), p.noteBg(), p.noteBorder(), p.lifeline());
    }

    private static Color colored(@Nullable FlowDiagram.Format format, Color usual) {
        return format == null || format.color() == null ? usual : format.color();
    }

    Font titleFont(FlowDiagram.Box b) { return styled(bold, b.title, Font.BOLD); }
    private Font fileFont(FlowDiagram.Box b) { return styled(small, b.fileFormat, Font.PLAIN); }
    /** A method's own format, else the format its box gives all its method names. */
    private static @Nullable FlowDiagram.Format methodFormat(FlowDiagram.Box b, FlowDiagram.Method m) {
        return m.format != null ? m.format : b.methodsFormat;
    }
    private Font methodFont(FlowDiagram.Box b, FlowDiagram.Method m) { return styled(font, methodFormat(b, m), Font.PLAIN); }

    /**
     * The method section's background: its own, else a softer shade of the class section's (null: the palette's).
     */
    static @Nullable Color bodyColor(FlowDiagram.Box b) {
        if (b.fill != null) return b.fill;
        if (b.headerFill == null) return null;
        return FlowDiagram.isDark(b.headerFill) ? mix(Color.BLACK, b.headerFill, 0.22) : mix(Color.WHITE, b.headerFill, 0.55);
    }

    /** The class section's background: its own, else a stronger shade of the method section's (null: the palette's). */
    static @Nullable Color headerColor(FlowDiagram.Box b, Palette p) {
        if (b.headerFill != null) return b.headerFill;
        return b.fill == null ? null : boxPalette(p, b.fill).headerBg();
    }
    private Font noteFont(@Nullable FlowDiagram.Format f) { return styled(small, f, Font.PLAIN); }

    /** A note as the lines it is drawn in: its own lines, wrapped at {@link #NOTE_WIDTH}. */
    List<String> noteLines(String note, Font f) {
        List<String> out = new ArrayList<>();
        if (note == null || note.isBlank()) return out;
        FontMetrics m = metrics(f);
        for (String para : note.strip().split("\\R")) {
            StringBuilder line = new StringBuilder();
            for (String word : para.split(" ")) {
                String next = line.isEmpty() ? word : line + " " + word;
                if (!line.isEmpty() && m.stringWidth(next) > NOTE_WIDTH) {
                    out.add(line.toString());
                    line = new StringBuilder(word);
                } else {
                    line = new StringBuilder(next);
                }
            }
            out.add(line.toString());
        }
        return out;
    }

    private List<String> boxNote(FlowDiagram.Box b) { return noteLines(b.note, noteFont(b.noteFormat)); }
    private List<String> methodNote(FlowDiagram.Method m) { return noteLines(m.note, noteFont(m.noteFormat)); }

    // ---------- geometry ----------

    /** The class name and file name part, without the note. */
    private static double titleHeight(FlowDiagram.Box b) {
        return b.subtitle().isEmpty() ? HEADER_NEW : HEADER;
    }

    /** The class name, file name and the note under them. */
    double headerHeight(FlowDiagram.Box b) {
        int notes = boxNote(b).size();
        return titleHeight(b) + (notes == 0 ? 0 : notes * NOTE_LINE + 4);
    }

    /** A method's row: its name, and its note under it. */
    double rowHeight(FlowDiagram.Method m) {
        int notes = methodNote(m).size();
        return ROW + (notes == 0 ? 0 : notes * NOTE_LINE + 2);
    }

    double width(FlowDiagram.Box b) {
        String tag = boxTag(b);
        double title = metrics(titleFont(b)).stringWidth(b.name) + (tag.isEmpty() ? 0 : 16 + smallFm.stringWidth(tag));
        double w = Math.max(title, metrics(fileFont(b)).stringWidth(b.subtitle())) + 28;
        FontMetrics bn = metrics(noteFont(b.noteFormat));
        for (String line : boxNote(b)) w = Math.max(w, bn.stringWidth(line) + 28);
        // row: dot and name from x+24, a gap, the badge, 12 to the edge; the note from x+24
        for (FlowDiagram.Method m : b.methods) {
            w = Math.max(w, 24 + metrics(methodFont(b, m)).stringWidth(m.name) + 16 + smallFm.stringWidth(badge(m)) + 12);
            FontMetrics mn = metrics(noteFont(m.noteFormat));
            for (String line : methodNote(m)) w = Math.max(w, 24 + mn.stringWidth(line) + 12);
        }
        return Math.max(MIN_WIDTH, Math.ceil(w));
    }

    /** "custom" for a class that isn't in the code, "added" for one picked from the code in this diagram. */
    private static String boxTag(FlowDiagram.Box b) {
        return b.isUnknown() ? "custom" : b.picked ? "added" : "";
    }

    /** True when the point is on the class name part of the box (not its methods). */
    boolean inHeader(FlowDiagram.Box b, Point2D p) {
        return bounds(b).contains(p) && p.getY() < b.y + headerHeight(b);
    }

    /** True for a call to or from a custom method: drawn in the custom color. */
    private static boolean touchesCustom(FlowDiagram d, FlowDiagram.Link l) {
        FlowDiagram.Method a = d.method(l.from()), b = d.method(l.to());
        return a != null && a.unknown && a.picked.isEmpty() || b != null && b.unknown && b.picked.isEmpty();
    }

    double height(FlowDiagram.Box b) {
        double rows = 0;
        for (FlowDiagram.Method m : b.methods) rows += rowHeight(m);
        return headerHeight(b) + (b.methods.isEmpty() ? ROW : rows) + PAD_BOTTOM;
    }

    Rectangle2D bounds(FlowDiagram.Box b) {
        return new Rectangle2D.Double(b.x, b.y, width(b), height(b));
    }

    /** A method's row (with its note); for a box without methods, the "no methods" row. */
    Rectangle2D rowBounds(FlowDiagram.Box b, int index) {
        double y = b.y + headerHeight(b);
        for (int i = 0; i < index && i < b.methods.size(); i++) y += rowHeight(b.methods.get(i));
        double h = index < b.methods.size() ? rowHeight(b.methods.get(index)) : ROW;
        return new Rectangle2D.Double(b.x, y, width(b), h);
    }

    /** The part of a row with the method's name (its arrows start and end there). */
    private static Rectangle2D nameLine(Rectangle2D row) {
        return new Rectangle2D.Double(row.getX(), row.getY(), row.getWidth(), ROW);
    }

    FlowDiagram.Measure measure() {
        return new FlowDiagram.Measure() {
            @Override public double width(FlowDiagram.Box b) { return FlowDiagramPainter.this.width(b); }
            @Override public double height(FlowDiagram.Box b) { return FlowDiagramPainter.this.height(b); }
        };
    }

    void autoLayout(FlowDiagram d) {
        d.autoLayout(measure(), 90, 30, MARGIN);
    }

    /** Where the method's arrows start and end: the middle of its row's left and right edge. */
    private Point2D anchor(FlowDiagram d, String methodId, boolean right) {
        FlowDiagram.Box b = d.boxOf(methodId);
        int i = 0;
        for (; i < b.methods.size(); i++) if (b.methods.get(i).id.equals(methodId)) break;
        Rectangle2D r = nameLine(rowBounds(b, i));
        return new Point2D.Double(right ? r.getMaxX() : r.getX(), r.getCenterY());
    }

    /** A call's line: its path, its end, and a point just before the end (the arrow head points from it). */
    private record Route(Shape path, Point2D tip, Point2D before) {}

    /** The arrow of a call: from the caller's row to the callee's row, around the boxes, in the line's shape. */
    Shape linkPath(FlowDiagram d, FlowDiagram.Link l) {
        return route(d, l).path();
    }

    /** Space kept between a line that goes around boxes and the boxes. */
    private static final double CLEARANCE = 12;
    /** Detours found lately, by link and the places of all boxes (dragging a box repaints often). */
    private final Map<String, Route> detours = new LinkedHashMap<>(64, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Route> e) { return size() > 500; }
    };

    /**
     * A call's line: the direct one (a curve, a straight line or an elbow) when it misses the other boxes, else a
     * detour around them with right angles (rounded for a curved line).
     */
    private Route route(FlowDiagram d, FlowDiagram.Link l) {
        Route direct = direct(d, l);
        FlowDiagram.Box from = d.boxOf(l.from()), to = d.boxOf(l.to());
        List<Rectangle2D> others = new ArrayList<>();
        StringBuilder key = new StringBuilder(l.from()).append('>').append(l.to()).append('|').append(d.line(l).shape());
        for (FlowDiagram.Box b : d.boxes) {
            Rectangle2D r = bounds(b);
            key.append('|').append(Math.round(r.getX())).append(',').append(Math.round(r.getY())).append(',')
                    .append(Math.round(r.getWidth())).append(',').append(Math.round(r.getHeight()));
            if (b != from && b != to) others.add(r);
        }
        if (!crosses(direct.path(), others)) return direct;
        return detours.computeIfAbsent(key.toString(), k -> {
            Route around = detour(d, l, direct);
            return around != null ? around : direct;
        });
    }

    /** True when the line runs through one of the boxes. */
    private static boolean crosses(Shape path, List<Rectangle2D> boxes) {
        double[] c = new double[6];
        double lx = 0, ly = 0;
        for (PathIterator it = path.getPathIterator(null, 1.0); !it.isDone(); it.next()) {
            int type = it.currentSegment(c);
            if (type == PathIterator.SEG_LINETO) {
                for (Rectangle2D r : boxes) {
                    if (new Rectangle2D.Double(r.getX() + 1, r.getY() + 1, r.getWidth() - 2, r.getHeight() - 2)
                            .intersectsLine(lx, ly, c[0], c[1])) {
                        return true;
                    }
                }
            }
            lx = c[0];
            ly = c[1];
        }
        return false;
    }

    /**
     * A line around the boxes, from the same ends as the direct line: the shortest path with the fewest turns along
     * the lanes next to the boxes (a grid made of the boxes' edges, {@link #CLEARANCE} away from them).
     */
    private @Nullable Route detour(FlowDiagram d, FlowDiagram.Link l, Route direct) {
        FlowDiagram.Box from = d.boxOf(l.from()), to = d.boxOf(l.to());
        Rectangle2D fb = bounds(from), tb = bounds(to);
        double[] start = new double[2];
        direct.path().getPathIterator(null).currentSegment(start);
        Point2D a = new Point2D.Double(start[0], start[1]), b = direct.tip();
        int out = Math.abs(a.getX() - fb.getMaxX()) < 0.5 ? 0 : 1;   // 0 = leaves to the right, 1 = to the left
        int in = Math.abs(b.getX() - tb.getX()) < 0.5 ? 0 : 1;       // 0 = enters moving right, 1 = moving left
        Point2D a2 = new Point2D.Double(a.getX() + (out == 0 ? CLEARANCE : -CLEARANCE), a.getY());
        Point2D b2 = new Point2D.Double(b.getX() + (in == 0 ? -CLEARANCE : CLEARANCE), b.getY());

        List<Rectangle2D> walls = new ArrayList<>();
        for (FlowDiagram.Box box : d.boxes) {
            Rectangle2D r = bounds(box);
            walls.add(new Rectangle2D.Double(r.getX() - CLEARANCE, r.getY() - CLEARANCE,
                    r.getWidth() + 2 * CLEARANCE, r.getHeight() + 2 * CLEARANCE));
        }
        java.util.Set<Double> xMiddles = new java.util.HashSet<>(), yMiddles = new java.util.HashSet<>();
        double[] xs = lanes(walls, a2.getX(), b2.getX(), true, xMiddles), ys = lanes(walls, a2.getY(), b2.getY(), false, yMiddles);
        // Lines keep to the middle of the gaps between boxes: running along a box's edge, or along the row the line
        // starts or ends on (where other lines of that method run), costs more.
        double[] xCost = laneCosts(xs, a2.getX(), b2.getX(), xMiddles), yCost = laneCosts(ys, a2.getY(), b2.getY(), yMiddles);
        int nx = xs.length, ny = ys.length;
        int si = Arrays.binarySearch(xs, a2.getX()), sj = Arrays.binarySearch(ys, a2.getY());
        int ti = Arrays.binarySearch(xs, b2.getX()), tj = Arrays.binarySearch(ys, b2.getY());
        if (inside(walls, a2.getX(), a2.getY()) || inside(walls, b2.getX(), b2.getY())) return null;

        // Dijkstra over (grid point, direction): 0 right, 1 left, 2 down, 3 up. A turn costs as much as 40 units.
        int[] dx = {1, -1, 0, 0}, dy = {0, 0, 1, -1};
        double turn = 40;
        double[] cost = new double[nx * ny * 4];
        int[] prev = new int[cost.length];
        Arrays.fill(cost, Double.MAX_VALUE);
        Arrays.fill(prev, -1);
        PriorityQueue<double[]> queue = new PriorityQueue<>((p, q) -> Double.compare(p[0], q[0]));
        int first = (sj * nx + si) * 4 + out;
        cost[first] = 0;
        queue.add(new double[]{0, first});
        int best = -1;
        double bestCost = Double.MAX_VALUE;
        while (!queue.isEmpty()) {
            double[] top = queue.poll();
            int s = (int) top[1];
            if (top[0] > cost[s] || top[0] >= bestCost) continue;
            int dir = s % 4, node = s / 4, i = node % nx, j = node / nx;
            if (i == ti && j == tj) {
                double c = top[0] + (dir == in ? 0 : turn);
                if (c < bestCost) { bestCost = c; best = s; }
                continue;
            }
            for (int nd = 0; nd < 4; nd++) {
                if ((dir ^ 1) == nd) continue; // no U-turns
                int ni = i + dx[nd], nj = j + dy[nd];
                if (ni < 0 || nj < 0 || ni >= nx || nj >= ny) continue;
                if (blocked(walls, xs[i], ys[j], xs[ni], ys[nj])) continue;
                double c = top[0] + Math.abs(xs[ni] - xs[i]) * yCost[j] + Math.abs(ys[nj] - ys[j]) * xCost[i]
                        + (nd == dir ? 0 : turn);
                int ns = (nj * nx + ni) * 4 + nd;
                if (c < cost[ns]) {
                    cost[ns] = c;
                    prev[ns] = s;
                    queue.add(new double[]{c, ns});
                }
            }
        }
        if (best < 0) return null;

        List<Point2D> points = new ArrayList<>();
        points.add(b);
        for (int s = best; s >= 0; s = prev[s]) {
            int node = s / 4;
            points.add(new Point2D.Double(xs[node % nx], ys[node / nx]));
        }
        points.add(a);
        java.util.Collections.reverse(points);
        // Keep the corners only.
        List<Point2D> corners = new ArrayList<>();
        for (Point2D p : points) {
            int n = corners.size();
            if (n > 0 && corners.get(n - 1).distance(p) < 0.01) continue;
            if (n >= 2) {
                Point2D u = corners.get(n - 2), v = corners.get(n - 1);
                boolean straight = Math.abs(u.getX() - v.getX()) < 0.01 && Math.abs(v.getX() - p.getX()) < 0.01
                        || Math.abs(u.getY() - v.getY()) < 0.01 && Math.abs(v.getY() - p.getY()) < 0.01;
                if (straight) corners.remove(n - 1);
            }
            corners.add(p);
        }
        boolean round = d.line(l).shape() == FlowDiagram.LineShape.CURVED;
        return new Route(polyline(corners, round ? 10 : 0), b, corners.get(corners.size() - 2));
    }

    /** The grid's lines in one direction: the walls' edges, the line's two ends, and the middles between them, sorted. */
    private static double[] lanes(List<Rectangle2D> walls, double from, double to, boolean x, java.util.Set<Double> middles) {
        java.util.TreeSet<Double> set = new java.util.TreeSet<>();
        set.add(from);
        set.add(to);
        for (Rectangle2D r : walls) {
            set.add(x ? r.getMinX() : r.getMinY());
            set.add(x ? r.getMaxX() : r.getMaxY());
        }
        Double last = null;
        for (double v : set) {
            if (last != null && v - last > 2) middles.add((last + v) / 2);
            last = v;
        }
        middles.removeAll(set);
        set.addAll(middles);
        double[] out = new double[set.size()];
        int k = 0;
        for (double v : set) out[k++] = v;
        return out;
    }

    /** What a unit of line costs along each lane: the middles of gaps 1, the line's ends 1.1, a box's edge 1.3. */
    private static double[] laneCosts(double[] lanes, double from, double to, java.util.Set<Double> middles) {
        double[] cost = new double[lanes.length];
        for (int k = 0; k < lanes.length; k++) {
            cost[k] = lanes[k] == from || lanes[k] == to ? 1.1 : middles.contains(lanes[k]) ? 1 : 1.3;
        }
        return cost;
    }

    private static boolean inside(List<Rectangle2D> walls, double x, double y) {
        for (Rectangle2D r : walls) {
            if (x > r.getMinX() + 0.01 && x < r.getMaxX() - 0.01 && y > r.getMinY() + 0.01 && y < r.getMaxY() - 0.01) return true;
        }
        return false;
    }

    /** True when a horizontal or vertical step between two neighboring grid points runs inside a wall. */
    private static boolean blocked(List<Rectangle2D> walls, double x1, double y1, double x2, double y2) {
        return inside(walls, (x1 + x2) / 2, (y1 + y2) / 2) || inside(walls, x2, y2);
    }

    /** Lines through the points, with corners rounded by {@code radius} (0 = sharp). */
    private static Path2D polyline(List<Point2D> p, double radius) {
        Path2D path = new Path2D.Double();
        path.moveTo(p.get(0).getX(), p.get(0).getY());
        for (int k = 1; k < p.size() - 1; k++) {
            Point2D u = p.get(k - 1), v = p.get(k), w = p.get(k + 1);
            double r = Math.min(radius, Math.min(u.distance(v), v.distance(w)) / 2);
            if (r <= 0) {
                path.lineTo(v.getX(), v.getY());
                continue;
            }
            double inX = v.getX() + (u.getX() - v.getX()) * r / u.distance(v), inY = v.getY() + (u.getY() - v.getY()) * r / u.distance(v);
            double outX = v.getX() + (w.getX() - v.getX()) * r / v.distance(w), outY = v.getY() + (w.getY() - v.getY()) * r / v.distance(w);
            path.lineTo(inX, inY);
            path.quadTo(v.getX(), v.getY(), outX, outY);
        }
        Point2D last = p.get(p.size() - 1);
        path.lineTo(last.getX(), last.getY());
        return path;
    }

    private Route direct(FlowDiagram d, FlowDiagram.Link l) {
        FlowDiagram.Box from = d.boxOf(l.from()), to = d.boxOf(l.to());
        Rectangle2D fb = bounds(from), tb = bounds(to);
        Point2D a, b;
        double c1, c2;
        boolean loop = false;
        if (tb.getX() >= fb.getMaxX()) {          // callee to the right
            a = anchor(d, l.from(), true);
            b = anchor(d, l.to(), false);
            double dx = Math.max(40, (b.getX() - a.getX()) / 2);
            c1 = a.getX() + dx;
            c2 = b.getX() - dx;
        } else if (tb.getMaxX() <= fb.getX()) {   // callee to the left
            a = anchor(d, l.from(), false);
            b = anchor(d, l.to(), true);
            double dx = Math.max(40, (a.getX() - b.getX()) / 2);
            c1 = a.getX() - dx;
            c2 = b.getX() + dx;
        } else {                                  // same column (or the same box): loop around the right side
            a = anchor(d, l.from(), true);
            b = anchor(d, l.to(), true);
            double out = Math.max(a.getX(), b.getX()) + 50;
            c1 = out;
            c2 = out;
            loop = true;
        }
        FlowDiagram.LineShape shape = d.line(l).shape();
        if (shape == FlowDiagram.LineShape.CURVED) {
            return new Route(new CubicCurve2D.Double(a.getX(), a.getY(), c1, a.getY(), c2, b.getY(), b.getX(), b.getY()),
                    b, new Point2D.Double(c2, b.getY()));
        }
        if (shape == FlowDiagram.LineShape.STRAIGHT && !loop) {
            return new Route(new java.awt.geom.Line2D.Double(a, b), b, a);
        }
        // Elbow (and a straight line that has to go around its column): right angles through the middle.
        double mx = loop ? c1 : (a.getX() + b.getX()) / 2;
        Path2D path = new Path2D.Double();
        path.moveTo(a.getX(), a.getY());
        path.lineTo(mx, a.getY());
        path.lineTo(mx, b.getY());
        path.lineTo(b.getX(), b.getY());
        return new Route(path, b, new Point2D.Double(mx, b.getY()));
    }

    // ---------- hit testing (window) ----------

    /** What is under a point: a link, a method row, a class header, or nothing. */
    record Hit(@Nullable FlowDiagram.Box box, @Nullable FlowDiagram.Method method, @Nullable FlowDiagram.Link link) {
        static final Hit NONE = new Hit(null, null, null);
    }

    Hit hit(FlowDiagram d, Point2D p) {
        for (int i = d.boxes.size() - 1; i >= 0; i--) { // topmost (last drawn) first
            FlowDiagram.Box b = d.boxes.get(i);
            if (!bounds(b).contains(p)) continue;
            for (int r = 0; r < b.methods.size(); r++) {  // a row includes its note
                if (rowBounds(b, r).contains(p)) return new Hit(b, b.methods.get(r), null);
            }
            return new Hit(b, null, null);
        }
        for (FlowDiagram.Link l : d.links) {
            if (distance(linkPath(d, l), p) <= 5) return new Hit(null, null, l);
        }
        return Hit.NONE;
    }

    private static double distance(Shape curve, Point2D p) {
        double best = Double.MAX_VALUE;
        double[] c = new double[6];
        double lx = 0, ly = 0;
        for (PathIterator it = curve.getPathIterator(null, 1.0); !it.isDone(); it.next()) {
            int type = it.currentSegment(c);
            if (type == PathIterator.SEG_LINETO) {
                best = Math.min(best, java.awt.geom.Line2D.ptSegDist(lx, ly, c[0], c[1], p.getX(), p.getY()));
            }
            lx = c[0];
            ly = c[1];
        }
        return best;
    }

    // ---------- class diagram ----------

    /** What the window highlights. */
    record Selection(@Nullable String boxId, @Nullable String methodId, @Nullable FlowDiagram.Link link) {
        static final Selection NONE = new Selection(null, null, null);
    }

    void paint(Graphics2D g, FlowDiagram d, Palette p, Selection sel) {
        hints(g);
        for (FlowDiagram.Link l : d.links) paintLink(g, d, l, p, l.equals(sel.link()));
        for (FlowDiagram.Box b : d.boxes) paintBox(g, b, p, sel);
    }

    private void paintBox(Graphics2D g, FlowDiagram.Box b, Palette palette, Selection sel) {
        // The method section and the class section each get text that is readable on their own background.
        Palette p = boxPalette(palette, bodyColor(b));
        Color headerColor = headerColor(b, palette);
        Palette hp = b.headerFill != null ? boxPalette(palette, b.headerFill) : p;
        Rectangle2D r = bounds(b);
        boolean selected = b.id.equals(sel.boxId()) && sel.methodId() == null;
        Shape card = new RoundRectangle2D.Double(r.getX(), r.getY(), r.getWidth(), r.getHeight(), 12, 12);
        g.setColor(new Color(0, 0, 0, 22));
        g.fill(new RoundRectangle2D.Double(r.getX() + 1, r.getY() + 2, r.getWidth(), r.getHeight(), 12, 12));
        Color fill = p.boxBg();
        g.setColor(fill);
        g.fill(card);

        // header: a tint of the box's own color, or the palette's header color
        double hh = headerHeight(b);
        Shape old = g.getClip();
        g.clip(card);
        g.setColor(headerColor != null ? headerColor : b.isUnknown() ? mix(p.custom(), fill, 0.18) : p.headerBg());
        g.fill(new Rectangle2D.Double(r.getX(), r.getY(), r.getWidth(), hh));
        g.setClip(old);
        Color border = b.border != null ? b.border : b.isUnknown() ? p.custom() : b.headerFill != null ? hp.boxBorder() : p.boxBorder();
        g.setColor(b.border != null ? border : b.headerFill != null ? hp.boxBorder() : p.boxBorder());
        g.draw(new java.awt.geom.Line2D.Double(r.getX(), r.getY() + hh, r.getMaxX(), r.getY() + hh));

        Font tf = titleFont(b);
        FontMetrics tm = metrics(tf);
        g.setFont(tf);
        g.setColor(colored(b.title, hp.title()));
        double titleY = b.subtitle().isEmpty() ? r.getY() + (titleHeight(b) + tm.getAscent() - tm.getDescent()) / 2
                : r.getY() + 8 + tm.getAscent();
        g.drawString(b.name, (float) (r.getX() + 14), (float) titleY);
        String tag = boxTag(b);
        if (!tag.isEmpty()) {
            g.setFont(small);
            g.setColor(b.isUnknown() ? hp.custom() : hp.added());
            g.drawString(tag, (float) (r.getMaxX() - 12 - smallFm.stringWidth(tag)), (float) titleY);
        }
        if (!b.subtitle().isEmpty()) {
            g.setFont(fileFont(b));
            g.setColor(colored(b.fileFormat, hp.muted()));
            g.drawString(b.subtitle(), (float) (r.getX() + 14), (float) (titleY + 4 + smallFm.getAscent()));
        }
        paintNote(g, boxNote(b), b.noteFormat, r.getX() + 14, r.getY() + titleHeight(b) - 4, hp);

        // methods
        if (b.methods.isEmpty()) {
            Rectangle2D row = rowBounds(b, 0);
            g.setFont(font);
            g.setColor(p.muted());
            g.drawString("no methods", (float) (row.getX() + 14), (float) baseline(row, fm));
        }
        for (int i = 0; i < b.methods.size(); i++) {
            FlowDiagram.Method m = b.methods.get(i);
            Rectangle2D row = rowBounds(b, i);
            Rectangle2D name = nameLine(row);
            if (m.id.equals(sel.methodId())) {
                g.setColor(mix(p.selection(), fill, 0.16));
                g.fill(new Rectangle2D.Double(row.getX() + 3, row.getY() + 1, row.getWidth() - 6, row.getHeight() - 2));
            }
            Color text = colored(methodFormat(b, m), isCustom(m) ? p.custom() : p.text());
            g.setColor(text);
            g.fill(new java.awt.geom.Ellipse2D.Double(name.getX() + 12, name.getCenterY() - 2.5, 5, 5));
            Font mf = methodFont(b, m);
            g.setFont(mf);
            g.drawString(m.name, (float) (name.getX() + 24), (float) baseline(name, metrics(mf)));
            paintBadge(g, m, name, p);
            paintNote(g, methodNote(m), m.noteFormat, name.getX() + 24, name.getMaxY() - 5, p);
        }

        g.setColor(selected ? p.selection() : border);
        Stroke s = g.getStroke();
        g.setStroke(new BasicStroke(selected || b.isUnknown() || b.border != null ? 2f : 1f));
        g.draw(card);
        g.setStroke(s);
    }

    /** A note's lines from the given top (the usual note color: muted). */
    private void paintNote(Graphics2D g, List<String> lines, @Nullable FlowDiagram.Format format, double x, double top, Palette p) {
        if (lines.isEmpty()) return;
        Font f = noteFont(format);
        FontMetrics m = metrics(f);
        g.setFont(f);
        g.setColor(colored(format, p.muted()));
        for (int i = 0; i < lines.size(); i++) {
            g.drawString(lines.get(i), (float) x, (float) (top + i * NOTE_LINE + m.getAscent()));
        }
    }

    /** A method that isn't in the code (it has no lines). */
    private static boolean isCustom(FlowDiagram.Method m) {
        return m.unknown && m.picked.isEmpty();
    }

    /** "custom" for a method that isn't in the code, else its line count ("+2" for lines picked here, not in the flow yet). */
    private static String badge(FlowDiagram.Method m) {
        if (isCustom(m)) return "custom";
        int n = m.lineCount();
        String count = n == 1 ? "1 line" : n + " lines";
        return m.picked.isEmpty() ? count : count + " (+" + m.picked.size() + ")";
    }

    private void paintBadge(Graphics2D g, FlowDiagram.Method m, Rectangle2D row, Palette p) {
        String text = badge(m);
        g.setFont(small);
        g.setColor(isCustom(m) ? p.custom() : !m.picked.isEmpty() ? p.added() : p.muted());
        g.drawString(text, (float) (row.getMaxX() - 12 - smallFm.stringWidth(text)), (float) baseline(row, smallFm));
    }

    private void paintLink(Graphics2D g, FlowDiagram d, FlowDiagram.Link l, Palette p, boolean selected) {
        Route route = route(d, l);
        FlowDiagram.Line look = d.line(l);
        Color color = selected ? p.selection() : look.color() != null ? look.color()
                : touchesCustom(d, l) ? p.custom() : l.manual() ? p.manualCall() : p.codeCall();
        g.setColor(color);
        float width = look.width().width + (selected ? 1f : 0f);
        drawLine(g, route.path(), look.pattern(), l.manual(), width, p.background());
        arrowHead(g, route.before(), route.tip(), look.arrow(), width);
        g.setStroke(new BasicStroke(1f));
    }

    /**
     * Draws a line's path in a pattern. A double pattern draws a wide line and then its middle again in the
     * background color, which leaves two thin parallel lines with open ends.
     */
    static void drawLine(Graphics2D g, Shape path, FlowDiagram.LinePattern pattern, boolean manual, float width, Color background) {
        if (pattern == FlowDiagram.LinePattern.DOUBLE || pattern == FlowDiagram.LinePattern.DOUBLE_DASHED) {
            float thin = Math.max(0.9f, width * 0.55f), gap = Math.max(1.6f, width * 0.9f);
            float[] dash = pattern == FlowDiagram.LinePattern.DOUBLE_DASHED ? new float[]{8, 5} : null;
            Color line = g.getColor();
            g.setStroke(new BasicStroke(gap + 2 * thin, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 10, dash, 0));
            g.draw(path);
            g.setColor(background);
            g.setStroke(new BasicStroke(gap, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 10, dash, 0));
            g.draw(path);
            g.setColor(line);
            return;
        }
        g.setStroke(stroke(pattern, manual, width));
        g.draw(path);
    }

    /** The end of a call's line, pointing from {@code from} to {@code tip}, sized to the line. */
    static void arrowHead(Graphics2D g, Point2D from, Point2D tip, FlowDiagram.ArrowHead kind, float width) {
        if (kind == FlowDiagram.ArrowHead.NONE) return;
        double angle = Math.atan2(tip.getY() - from.getY(), tip.getX() - from.getX());
        double len = 9 + width * 1.6, tx = tip.getX(), ty = tip.getY();
        double cos = Math.cos(angle), sin = Math.sin(angle);
        // A point "back" along the line and "side" across it, seen from the tip.
        java.util.function.BiFunction<Double, Double, Point2D> at = (back, side) ->
                new Point2D.Double(tx - back * cos - side * sin, ty - back * sin + side * cos);
        g.setStroke(new BasicStroke(Math.max(1.1f, width * 0.8f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D head = new Path2D.Double();
        switch (kind) {
            case FILLED -> { // a sharp arrow with a notch at the back
                Point2D l = at.apply(len, len * 0.42), r = at.apply(len, -len * 0.42), notch = at.apply(len * 0.7, 0.0);
                head.moveTo(tx, ty);
                head.lineTo(l.getX(), l.getY());
                head.lineTo(notch.getX(), notch.getY());
                head.lineTo(r.getX(), r.getY());
                head.closePath();
                g.fill(head);
            }
            case TRIANGLE -> {
                Point2D l = at.apply(len, len * 0.45), r = at.apply(len, -len * 0.45);
                head.moveTo(tx, ty);
                head.lineTo(l.getX(), l.getY());
                head.lineTo(r.getX(), r.getY());
                head.closePath();
                g.fill(head);
            }
            case OPEN -> {
                Point2D l = at.apply(len, len * 0.5), r = at.apply(len, -len * 0.5);
                head.moveTo(l.getX(), l.getY());
                head.lineTo(tx, ty);
                head.lineTo(r.getX(), r.getY());
                g.draw(head);
            }
            case DIAMOND -> {
                Point2D l = at.apply(len * 0.6, len * 0.36), back = at.apply(len * 1.2, 0.0), r = at.apply(len * 0.6, -len * 0.36);
                head.moveTo(tx, ty);
                head.lineTo(l.getX(), l.getY());
                head.lineTo(back.getX(), back.getY());
                head.lineTo(r.getX(), r.getY());
                head.closePath();
                g.fill(head);
            }
            case CIRCLE -> {
                double rad = 3 + width;
                Point2D c = at.apply(rad, 0.0);
                g.fill(new java.awt.geom.Ellipse2D.Double(c.getX() - rad, c.getY() - rad, rad * 2, rad * 2));
            }
            case NONE -> {}
        }
    }

    /** The stroke of a line pattern; AUTO: dashed for calls connected by hand, solid for calls in the code. */
    static Stroke stroke(FlowDiagram.LinePattern pattern, boolean manual, float width) {
        float k = Math.max(1f, width / 1.6f); // longer dashes on thicker lines
        float[] dash = switch (pattern) {
            case AUTO -> manual ? new float[]{6, 4} : null;
            case SOLID -> null;
            case DASHED, DOUBLE_DASHED -> new float[]{6, 4};
            case SHORT_DASH -> new float[]{3, 3};
            case LONG_DASH -> new float[]{14, 6};
            case DOTTED -> new float[]{0.1f, 3.6f};
            case DASH_DOT -> new float[]{8, 4, 0.1f, 4};
            case DASH_DOT_DOT -> new float[]{8, 4, 0.1f, 3.5f, 0.1f, 4};
            case DOUBLE -> null;
        };
        if (dash == null) return new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
        for (int i = 0; i < dash.length; i++) dash[i] *= k;
        return new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10, dash, 0);
    }

    /** A rubber-band arrow while a connection is being drawn. */
    void paintDraft(Graphics2D g, Point2D from, Point2D to, Palette p) {
        hints(g);
        g.setColor(p.manualCall());
        g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10, new float[]{6, 4}, 0));
        g.draw(new java.awt.geom.Line2D.Double(from, to));
        g.setStroke(new BasicStroke(1f));
        arrowHead(g, from.getX(), from.getY(), to.getX(), to.getY(), true);
    }

    /** The size the class diagram needs (for the window's scroll area). */
    Rectangle2D extent(FlowDiagram d) {
        Rectangle2D all = null;
        for (FlowDiagram.Box b : d.boxes) {
            Rectangle2D r = bounds(b);
            all = all == null ? r : all.createUnion(r);
        }
        for (FlowDiagram.Link l : d.links) {
            Rectangle2D r = linkPath(d, l).getBounds2D();
            all = all == null ? r : all.createUnion(r);
        }
        return all == null ? new Rectangle2D.Double(0, 0, 0, 0) : all;
    }

    /** The class diagram as an image (2x for sharp text), with a title above it. */
    BufferedImage classImage(FlowDiagram d, String title, Palette p) {
        Rectangle2D e = extent(d);
        double titleH = boldFm.getHeight() + 14;
        double w = Math.max(e.getWidth(), boldFm.stringWidth(title)) + 2 * MARGIN;
        double h = e.getHeight() + 2 * MARGIN + titleH;
        return image(w, h, p, g -> {
            paintTitle(g, title, p);
            g.translate(MARGIN - e.getX(), MARGIN + titleH - e.getY());
            paint(g, d, p, Selection.NONE);
        });
    }

    // ---------- sequence diagram ----------

    private static final double SEQ_ROW = 34, SEQ_HEAD = 40, SEQ_GAP = 36;

    /** The sequence diagram as an image: participants on top and bottom, the calls in between. */
    BufferedImage sequenceImage(FlowDiagram d, String title, Palette p) {
        List<FlowDiagram.Step> steps = d.sequence();
        Map<FlowDiagram.Box, Double> centers = new LinkedHashMap<>();
        List<FlowDiagram.Box> participants = new ArrayList<>();
        for (FlowDiagram.Step s : steps) {
            switch (s) {
                case FlowDiagram.Start st -> addOnce(participants, st.box());
                case FlowDiagram.Call c -> { addOnce(participants, c.from()); addOnce(participants, c.to()); }
                case FlowDiagram.Return r -> { addOnce(participants, r.from()); addOnce(participants, r.to()); }
            }
        }
        if (participants.isEmpty()) participants.addAll(d.boxes);

        // Column width: wide enough for every participant title and every call label.
        double column = 150;
        for (FlowDiagram.Box b : participants) column = Math.max(column, participantWidth(b) + SEQ_GAP);
        int number = 0;
        for (FlowDiagram.Step s : steps) {
            if (s instanceof FlowDiagram.Call c) column = Math.max(column, fm.stringWidth(++number + ". " + c.method().name) + 40);
            if (s instanceof FlowDiagram.Start st) column = Math.max(column, smallFm.stringWidth(st.method().name) + 40);
        }
        double titleH = boldFm.getHeight() + 14;
        double x = MARGIN + column / 2;
        for (FlowDiagram.Box b : participants) {
            centers.put(b, x);
            x += column;
        }
        double w = Math.max(participants.size() * column, boldFm.stringWidth(title)) + 2 * MARGIN;
        double top = MARGIN + titleH;
        double bodyH = Math.max(1, steps.size()) * SEQ_ROW + 20;
        double h = top + SEQ_HEAD + bodyH + SEQ_HEAD + MARGIN;

        return image(w, h, p, g -> {
            paintTitle(g, title, p);
            double lifeTop = top + SEQ_HEAD, lifeBottom = top + SEQ_HEAD + bodyH;
            g.setColor(p.lifeline());
            g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{5, 4}, 0));
            for (double cx : centers.values()) g.draw(new java.awt.geom.Line2D.Double(cx, lifeTop, cx, lifeBottom));
            g.setStroke(new BasicStroke(1f));
            for (FlowDiagram.Box b : participants) {
                participant(g, b, centers.get(b), top, p);
                participant(g, b, centers.get(b), lifeBottom, p);
            }
            if (steps.isEmpty()) {
                g.setFont(font);
                g.setColor(p.muted());
                String none = "No calls between these methods yet";
                g.drawString(none, (float) (w / 2 - fm.stringWidth(none) / 2.0), (float) (lifeTop + 30));
                return;
            }
            double y = lifeTop + 24;
            int n = 0;
            for (FlowDiagram.Step s : steps) {
                switch (s) {
                    case FlowDiagram.Start st -> note(g, centers.get(st.box()), y, st.method().name, p);
                    case FlowDiagram.Call c -> message(g, centers.get(c.from()), centers.get(c.to()), y,
                            ++n + ". " + c.method().name, false, isCustom(c.method()) || c.to().isUnknown(), p);
                    case FlowDiagram.Return r -> message(g, centers.get(r.from()), centers.get(r.to()), y, "return", true,
                            r.from().isUnknown(), p);
                }
                y += SEQ_ROW;
            }
        });
    }

    private static void addOnce(List<FlowDiagram.Box> list, FlowDiagram.Box b) {
        if (b != null && !list.contains(b)) list.add(b);
    }

    private double participantWidth(FlowDiagram.Box b) {
        return Math.max(110, Math.max(metrics(titleFont(b)).stringWidth(b.name), metrics(fileFont(b)).stringWidth(b.subtitle())) + 28);
    }

    private void participant(Graphics2D g, FlowDiagram.Box b, double cx, double y, Palette p) {
        Color section = headerColor(b, p);
        p = boxPalette(p, section);
        double w = participantWidth(b);
        Shape r = new RoundRectangle2D.Double(cx - w / 2, y, w, SEQ_HEAD, 10, 10);
        g.setColor(section != null ? section : b.isUnknown() ? mix(p.custom(), p.boxBg(), 0.16) : p.headerBg());
        g.fill(r);
        g.setColor(b.border != null ? b.border : b.isUnknown() ? p.custom() : p.boxBorder());
        g.draw(r);
        boolean sub = !b.subtitle().isEmpty();
        Font tf = titleFont(b);
        FontMetrics tm = metrics(tf);
        g.setFont(tf);
        g.setColor(colored(b.title, p.title()));
        double ty = sub ? y + 6 + tm.getAscent() : y + (SEQ_HEAD + tm.getAscent() - tm.getDescent()) / 2;
        g.drawString(b.name, (float) (cx - tm.stringWidth(b.name) / 2.0), (float) ty);
        if (sub) {
            Font ff = fileFont(b);
            g.setFont(ff);
            g.setColor(colored(b.fileFormat, p.muted()));
            g.drawString(b.subtitle(), (float) (cx - metrics(ff).stringWidth(b.subtitle()) / 2.0), (float) (ty + 3 + smallFm.getAscent()));
        }
    }

    private void note(Graphics2D g, double cx, double y, String text, Palette p) {
        double w = smallFm.stringWidth(text) + 20, h = 22;
        Shape r = new RoundRectangle2D.Double(cx - w / 2, y - h / 2, w, h, 6, 6);
        g.setColor(p.noteBg());
        g.fill(r);
        g.setColor(p.noteBorder());
        g.draw(r);
        g.setFont(small);
        g.setColor(p.title());
        g.drawString(text, (float) (cx - smallFm.stringWidth(text) / 2.0), (float) (y + (smallFm.getAscent() - smallFm.getDescent()) / 2.0));
    }

    /** @param custom true for a call to (or return from) something that isn't in the code: custom color */
    private void message(Graphics2D g, double x1, double x2, double y, String label, boolean dashed, boolean custom, Palette p) {
        g.setColor(custom ? p.custom() : dashed ? p.muted() : p.title());
        g.setStroke(dashed ? new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{5, 4}, 0)
                : new BasicStroke(1.4f));
        g.setFont(dashed ? small : font);
        FontMetrics m = dashed ? smallFm : fm;
        if (x1 == x2) { // a call within the same class: a small loop
            Path2D loop = new Path2D.Double();
            loop.moveTo(x1, y - 6);
            loop.lineTo(x1 + 34, y - 6);
            loop.lineTo(x1 + 34, y + 8);
            loop.lineTo(x1 + 4, y + 8);
            g.draw(loop);
            g.setStroke(new BasicStroke(1f));
            arrowHead(g, x1 + 34, y + 8, x1, y + 8, !dashed);
            g.drawString(label, (float) (x1 + 40), (float) (y + 4));
            return;
        }
        double end = x2 + (x2 > x1 ? -1 : 1);
        g.draw(new java.awt.geom.Line2D.Double(x1, y, end, y));
        g.setStroke(new BasicStroke(1f));
        arrowHead(g, x1, y, x2, y, !dashed);
        g.drawString(label, (float) ((x1 + x2) / 2 - m.stringWidth(label) / 2.0), (float) (y - 6));
    }

    // ---------- shared ----------

    private void paintTitle(Graphics2D g, String title, Palette p) {
        g.setFont(bold);
        g.setColor(p.title());
        g.drawString(title, (float) MARGIN, (float) (MARGIN + boldFm.getAscent()));
    }

    private interface Painting { void paint(Graphics2D g); }

    private static BufferedImage image(double w, double h, Palette p, Painting painting) {
        int scale = 2;
        BufferedImage img = new BufferedImage((int) Math.ceil(w * scale), (int) Math.ceil(h * scale), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            hints(g);
            g.setColor(p.background());
            g.fillRect(0, 0, img.getWidth(), img.getHeight());
            g.scale(scale, scale);
            painting.paint(g);
        } finally {
            g.dispose();
        }
        return img;
    }

    private static void arrowHead(Graphics2D g, double fx, double fy, double tx, double ty, boolean filled) {
        arrowHead(g, fx, fy, tx, ty, filled, 9);
    }

    private static void arrowHead(Graphics2D g, double fx, double fy, double tx, double ty, boolean filled, double len) {
        double angle = Math.atan2(ty - fy, tx - fx);
        double spread = Math.toRadians(24);
        Path2D head = new Path2D.Double();
        head.moveTo(tx, ty);
        head.lineTo(tx - len * Math.cos(angle - spread), ty - len * Math.sin(angle - spread));
        if (filled) head.lineTo(tx - len * Math.cos(angle + spread), ty - len * Math.sin(angle + spread));
        else head.moveTo(tx, ty);
        if (filled) {
            head.closePath();
            g.fill(head);
        } else {
            head.lineTo(tx - len * Math.cos(angle + spread), ty - len * Math.sin(angle + spread));
            g.draw(head);
        }
    }

    private static double baseline(Rectangle2D row, FontMetrics m) {
        return row.getCenterY() + (m.getAscent() - m.getDescent()) / 2.0;
    }

    static void hints(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
    }

    /** {@code a} over {@code b} at the given strength (0 = b, 1 = a). */
    public static Color mix(Color a, Color b, double amount) {
        return new Color(
                (int) Math.round(a.getRed() * amount + b.getRed() * (1 - amount)),
                (int) Math.round(a.getGreen() * amount + b.getGreen() * (1 - amount)),
                (int) Math.round(a.getBlue() * amount + b.getBlue() * (1 - amount)));
    }
}
