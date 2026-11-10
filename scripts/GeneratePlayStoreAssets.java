import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Deterministic, anti-aliased Graphics2D Play Store screenshot & graphic asset generator
 * for WeMi Switch Control (v22.0).
 *
 * Uses the exact bundled app fonts (app/src/main/res/font/comfortaa.ttf & work_sans.ttf),
 * exact Color.kt / TvScreen.kt palette, and AutoShrinkSwitchName-style drawFittedString
 * so no text ever clips or overlaps adjacent buttons or card edges.
 */
public class GeneratePlayStoreAssets {

    private static final File ROOT = new File(".").getAbsoluteFile();
    private static Font comfortaaBase;
    private static Font workSansBase;

    // Exact Color.kt Palette
    private static final Color C_CYAN_TOP = new Color(0x06, 0xB6, 0xD4);       // #06B6D4 WemoGreenTop
    private static final Color C_TEAL_PRIMARY = new Color(0x08, 0x91, 0xB2);   // #0891B2 WemoGreenPrimary
    private static final Color C_SAPPHIRE_BOT = new Color(0x0F, 0x4C, 0x81);   // #0F4C81 WemoGreenBottom
    private static final Color C_TEAL_DARK = new Color(0x0E, 0x74, 0x90);      // #0E7490 WemoGreenDark
    private static final Color C_CYAN_SOFT = new Color(0xE0, 0xF7, 0xFA);      // #E0F7FA WemoGreenSoft
    private static final Color C_BG_GRAY = new Color(0xEE, 0xF2, 0xF6);        // #EEF2F6 WemoBackgroundGray
    private static final Color C_WHITE = new Color(0xFF, 0xFF, 0xFF);          // #FFFFFF WemoRowSurface
    private static final Color C_EXPANDED_BG = new Color(0xF4, 0xFA, 0xFC);    // #F4FAFC WemoRowExpandedSurface
    private static final Color C_DIVIDER = new Color(0xDC, 0xE4, 0xEC);        // #DCE4EC WemoDividerGray
    private static final Color C_TEXT_PRIMARY = new Color(0x0F, 0x17, 0x2A);   // #0F172A WemoTextPrimary
    private static final Color C_TEXT_SECONDARY = new Color(0x64, 0x74, 0x8B); // #64748B WemoTextSecondary

    // 3-State Hardware & Badge Colors
    private static final Color C_EMERALD = new Color(0x05, 0x96, 0x69);        // #059669 StatusActiveEmerald
    private static final Color C_EMERALD_BG = new Color(0xEC, 0xFD, 0xF5);     // #ECFDF5 StatusActiveSoftBg
    private static final Color C_OFF_RING = new Color(0x23, 0x27, 0x2F);       // #23272F PowerOffDarkGray
    private static final Color C_OFF_ICON = new Color(0x18, 0x1B, 0x20);       // #181B20 PowerOffIconDarkGray
    private static final Color C_OFF_FILL = new Color(0xEA, 0xEE, 0xF4);       // #EAEEF4
    private static final Color C_OFF_PILL_BG = new Color(0xE5, 0xE7, 0xEB);    // #E5E7EB
    private static final Color C_RED_OFFLINE = new Color(0xDC, 0x26, 0x26);    // #DC2626 StatusOfflineRed
    private static final Color C_RED_BG = new Color(0xFE, 0xF2, 0xF2);         // #FEF2F2 StatusOfflineSoftBg

    // Android TV 10-Foot Palette
    private static final Color C_TV_BG_TOP = new Color(0x07, 0x13, 0x24);      // #071324 TvDeepBackground
    private static final Color C_TV_BG_MID = new Color(0x0A, 0x1B, 0x33);      // #0A1B33
    private static final Color C_TV_BG_BOT = new Color(0x05, 0x0E, 0x1B);      // #050E1B
    private static final Color C_TV_CARD = new Color(0x0D, 0x21, 0x3B);        // #0D213B TvSurfaceCard
    private static final Color C_TV_CARD_ACTIVE = new Color(0x0E, 0x34, 0x5A); // #0E345A TvSurfaceCardActive
    private static final Color C_TV_CARD_OFFLINE = new Color(0x11, 0x1C, 0x2D);// #111C2D
    private static final Color C_TV_CYAN = new Color(0x22, 0xD3, 0xEE);        // #22D3EE TvCyanFocusBorder
    private static final Color C_TV_TEXT = new Color(0xF8, 0xFA, 0xFC);        // #F8FAFC TvTextPrimary
    private static final Color C_TV_SUBTEXT = new Color(0x94, 0xA3, 0xB8);     // #94A3B8 TvTextSecondary

    private static void initFonts() throws Exception {
        File f1 = new File(ROOT, "app/src/main/res/font/comfortaa.ttf");
        File f2 = new File(ROOT, "app/src/main/res/font/work_sans.ttf");
        comfortaaBase = f1.exists() ? Font.createFont(Font.TRUETYPE_FONT, f1) : new Font("SansSerif", Font.BOLD, 24);
        workSansBase = f2.exists() ? Font.createFont(Font.TRUETYPE_FONT, f2) : new Font("SansSerif", Font.PLAIN, 16);
    }

    private static Font fBrand(double sizePx, int style) {
        return comfortaaBase.deriveFont(style, (float) Math.max(8.0, sizePx));
    }

    private static Font fUi(double sizePx, int style) {
        return workSansBase.deriveFont(style, (float) Math.max(8.0, sizePx));
    }

    /**
     * Mirrors Compose AutoShrinkSwitchName / AutoResizingDeviceNameText: steps down font size
     * until the string fits strictly within maxWidthPx without overlapping adjacent controls.
     */
    private static int drawFittedString(Graphics2D g, String text, double x, double y, double maxWidthPx, double initialSizePx, int style, Color color) {
        g.setColor(color);
        double sz = initialSizePx;
        double minSz = Math.max(8.0, initialSizePx * 0.58);
        Font f = fUi(sz, style);
        g.setFont(f);
        while (sz > minSz && g.getFontMetrics().stringWidth(text) > maxWidthPx) {
            sz -= 0.8;
            f = fUi(sz, style);
            g.setFont(f);
        }
        g.drawString(text, (float) x, (float) y);
        return g.getFontMetrics().stringWidth(text);
    }

    private static Graphics2D createG2(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        return g;
    }

    private static void fillRoundRect(Graphics2D g, double x, double y, double w, double h, double r, Color fill, Color border, double borderW) {
        RoundRectangle2D rr = new RoundRectangle2D.Double(x, y, w, h, r * 2, r * 2);
        if (fill != null) {
            g.setColor(fill);
            g.fill(rr);
        }
        if (border != null && borderW > 0) {
            g.setColor(border);
            g.setStroke(new BasicStroke((float) borderW, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(rr);
        }
    }

    private static void drawIecPowerSymbol(Graphics2D g, double cx, double cy, double r, Color color, double strokeW) {
        g.setColor(color);
        g.setStroke(new BasicStroke((float) strokeW, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Arc2D arc = new Arc2D.Double(cx - r, cy - r + r * 0.08, r * 2, r * 2, 142, 256, Arc2D.OPEN);
        g.draw(arc);
        g.draw(new Line2D.Double(cx, cy - r - r * 0.16, cx, cy + r * 0.06));
    }

    private static void drawDisconnectedSymbol(Graphics2D g, double cx, double cy, double r, Color arcColor, Color slashColor, double strokeW) {
        g.setStroke(new BasicStroke((float) strokeW, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(arcColor);
        g.draw(new Arc2D.Double(cx - r, cy - r, r * 2, r * 2, 20, 140, Arc2D.OPEN));
        g.draw(new Arc2D.Double(cx - r, cy - r, r * 2, r * 2, 200, 140, Arc2D.OPEN));
        g.setColor(slashColor);
        double d = r * 0.92;
        g.draw(new Line2D.Double(cx - d, cy - d, cx + d, cy + d));
    }

    private static void drawPowerRingButton(Graphics2D g, double cx, double cy, double r, boolean isOn, boolean isReachable, double s, boolean darkSurfaceBg) {
        double sw = Math.max(2.0, 3.4 * s);
        Ellipse2D circle = new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2);
        if (!isReachable) {
            g.setColor(C_RED_BG);
            g.fill(circle);
            float[] dash = new float[]{(float) (6.0 * s), (float) (4.0 * s)};
            g.setColor(new Color(0xDC, 0x26, 0x26, 185));
            g.setStroke(new BasicStroke((float) (3.0 * s), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f, dash, 0f));
            g.draw(circle);
            drawDisconnectedSymbol(g, cx, cy, r * 0.40, C_TEXT_SECONDARY, C_RED_OFFLINE, 2.8 * s);
            return;
        }
        if (isOn) {
            if (darkSurfaceBg) {
                g.setColor(new Color(0xE0, 0xF7, 0xFA));
                g.fill(circle);
            } else {
                RadialGradientPaint rg = new RadialGradientPaint(
                    new Point2D.Double(cx, cy),
                    (float) r,
                    new float[]{0f, 1f},
                    new Color[]{new Color(0x06, 0xB6, 0xD4, 64), new Color(0x08, 0x91, 0xB2, 18)}
                );
                g.setPaint(rg);
                g.fill(circle);
            }

            LinearGradientPaint lg = new LinearGradientPaint(
                new Point2D.Double(cx - r, cy - r),
                new Point2D.Double(cx + r, cy + r),
                new float[]{0f, 0.5f, 1f},
                new Color[]{C_CYAN_TOP, C_TEAL_PRIMARY, C_SAPPHIRE_BOT}
            );
            g.setPaint(lg);
            g.setStroke(new BasicStroke((float) sw, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(circle);
            drawIecPowerSymbol(g, cx, cy, r * 0.40, C_TEAL_DARK, 3.0 * s);
        } else {
            g.setColor(C_OFF_FILL);
            g.fill(circle);
            g.setColor(C_OFF_RING);
            g.setStroke(new BasicStroke((float) sw, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(circle);
            drawIecPowerSymbol(g, cx, cy, r * 0.40, C_OFF_ICON, 3.0 * s);
        }
    }

    private static void drawCategoryIllustration(Graphics2D g, double x, double y, double w, double h, String category, boolean isOn, boolean isReachable, double s) {
        double cx = x + w * 0.46;
        double cy = y + h * 0.48;
        Color baseFill = !isReachable ? new Color(0xE2, 0xE8, 0xF0) : (isOn ? C_CYAN_SOFT : new Color(0x2D, 0x33, 0x3F));
        Color accent = !isReachable ? new Color(0x94, 0xA3, 0xB8) : (isOn ? C_TEAL_PRIMARY : new Color(0x94, 0xA3, 0xB8));

        if ("Tree Lights".equals(category)) {
            if (isReachable && isOn) {
                g.setColor(new Color(0xDC, 0xFC, 0xE7, 170));
                g.fill(new Ellipse2D.Double(cx - w * 0.44, cy - h * 0.44, w * 0.88, h * 0.84));
            }
            Color treeCol = (isReachable && isOn) ? new Color(0x16, 0xA3, 0x4A) : (isReachable ? new Color(0x33, 0x41, 0x55) : new Color(0x94, 0xA3, 0xB8));
            g.setColor(new Color(0x78, 0x35, 0x0F));
            g.fill(new Rectangle2D.Double(cx - w * 0.08, y + h * 0.72, w * 0.16, h * 0.16));

            g.setColor(treeCol);
            double[][] tiers = {
                {y + h * 0.14, y + h * 0.42, w * 0.26},
                {y + h * 0.30, y + h * 0.58, w * 0.34},
                {y + h * 0.46, y + h * 0.75, w * 0.40}
            };
            for (double[] t : tiers) {
                Path2D p = new Path2D.Double();
                p.moveTo(cx, t[0]);
                p.lineTo(cx - t[2], t[1]);
                p.lineTo(cx + t[2], t[1]);
                p.closePath();
                g.fill(p);
            }
            g.setColor((isReachable && isOn) ? new Color(0xFA, 0xCC, 0x15) : new Color(0x94, 0xA3, 0xB8));
            double sr = w * 0.09;
            g.fill(new Ellipse2D.Double(cx - sr, y + h * 0.05, sr * 2, sr * 2));
            if (isReachable && isOn) {
                Color[] ornCols = {new Color(0xEF, 0x44, 0x44), new Color(0xFA, 0xCC, 0x15), new Color(0x38, 0xBD, 0xF8), new Color(0xF4, 0x72, 0xB6)};
                double[][] ornPts = {{cx - w * 0.12, y + h * 0.38}, {cx + w * 0.14, y + h * 0.50}, {cx - w * 0.16, y + h * 0.64}, {cx + w * 0.12, y + h * 0.66}};
                for (int i = 0; i < ornCols.length; i++) {
                    g.setColor(ornCols[i]);
                    double rOrn = 2.8 * s;
                    g.fill(new Ellipse2D.Double(ornPts[i][0] - rOrn, ornPts[i][1] - rOrn, rOrn * 2, rOrn * 2));
                }
            }
        } else if ("Fan / Vent".equals(category)) {
            double rCage = Math.min(w, h) * 0.38;
            g.setColor(baseFill);
            g.fill(new Ellipse2D.Double(cx - rCage, cy - rCage, rCage * 2, rCage * 2));
            g.setColor(accent);
            g.setStroke(new BasicStroke((float) (2.0 * s)));
            g.draw(new Ellipse2D.Double(cx - rCage, cy - rCage, rCage * 2, rCage * 2));
            Color bladeCol = (isReachable && isOn) ? C_TEAL_PRIMARY : (isReachable ? new Color(0xCB, 0xD5, 0xE1) : new Color(0x94, 0xA3, 0xB8));
            g.setColor(bladeCol);
            for (int deg : new int[]{25, 115, 205, 295}) {
                double rad = Math.toRadians(deg);
                double bx = cx + Math.cos(rad) * rCage * 0.52;
                double by = cy + Math.sin(rad) * rCage * 0.52;
                double br = rCage * 0.25;
                g.fill(new Ellipse2D.Double(bx - br, by - br, br * 2, br * 2));
            }
            double hr = rCage * 0.20;
            g.setColor(C_WHITE);
            g.fill(new Ellipse2D.Double(cx - hr, cy - hr, hr * 2, hr * 2));
        } else if ("Lightbulb".equals(category)) {
            double rBulb = Math.min(w, h) * 0.31;
            double by = y + h * 0.38;
            if (isReachable && isOn) {
                g.setColor(new Color(0xFE, 0xF0, 0x8A, 175));
                double gr = rBulb * 1.4;
                g.fill(new Ellipse2D.Double(cx - gr, by - gr, gr * 2, gr * 2));
            }
            g.setColor((isReachable && isOn) ? new Color(0xFD, 0xE0, 0x47) : baseFill);
            g.fill(new Ellipse2D.Double(cx - rBulb, by - rBulb, rBulb * 2, rBulb * 2));
            g.setColor((isReachable && isOn) ? new Color(0xD9, 0x77, 0x06) : accent);
            g.setStroke(new BasicStroke((float) (2.0 * s)));
            g.draw(new Ellipse2D.Double(cx - rBulb, by - rBulb, rBulb * 2, rBulb * 2));
            fillRoundRect(g, cx - rBulb * 0.55, by + rBulb * 0.72, rBulb * 1.1, rBulb * 0.68, 3 * s, new Color(0x94, 0xA3, 0xB8), null, 0);
        } else {
            fillRoundRect(g, cx - w * 0.32, y + h * 0.12, w * 0.64, h * 0.72, 7 * s, baseFill, accent, 2.0 * s);
            g.setColor((isReachable && isOn) ? C_EMERALD : (isReachable ? C_CYAN_TOP : C_RED_OFFLINE));
            double lr = 3.5 * s;
            g.fill(new Ellipse2D.Double(cx - lr, cy - lr, lr * 2, lr * 2));
        }

        // Corner 3-State Badge
        double bx = x + w * 0.80;
        double by = y + h * 0.78;
        double br = 7.5 * s;
        g.setColor(C_WHITE);
        g.fill(new Ellipse2D.Double(bx - br * 1.2, by - br * 1.2, br * 2.4, br * 2.4));
        if (!isReachable) {
            g.setColor(C_RED_OFFLINE);
            g.fill(new Ellipse2D.Double(bx - br, by - br, br * 2, br * 2));
            drawDisconnectedSymbol(g, bx, by, br * 0.48, C_WHITE, C_WHITE, 1.5 * s);
        } else if (isOn) {
            g.setColor(C_EMERALD);
            g.fill(new Ellipse2D.Double(bx - br, by - br, br * 2, br * 2));
            drawIecPowerSymbol(g, bx, by, br * 0.48, C_WHITE, 1.5 * s);
        } else {
            g.setColor(C_OFF_RING);
            g.fill(new Ellipse2D.Double(bx - br, by - br, br * 2, br * 2));
            drawIecPowerSymbol(g, bx, by, br * 0.48, C_WHITE, 1.5 * s);
        }
    }

    private static int drawTopHeader(Graphics2D g, int w, double s, boolean isEdit) {
        int headerH = (int) (104 * s);
        LinearGradientPaint lg = new LinearGradientPaint(
            new Point2D.Double(0, 0),
            new Point2D.Double(w * 0.9, headerH),
            new float[]{0f, 0.5f, 1f},
            new Color[]{C_CYAN_TOP, C_TEAL_PRIMARY, C_SAPPHIRE_BOT}
        );
        g.setPaint(lg);
        g.fillRect(0, 0, w, headerH);

        RadialGradientPaint rg = new RadialGradientPaint(
            new Point2D.Double(w * 0.85, headerH * 0.22),
            (float) (w * 0.50),
            new float[]{0f, 1f},
            new Color[]{new Color(0x22, 0xD3, 0xEE, 68), new Color(0x22, 0xD3, 0xEE, 0)}
        );
        g.setPaint(rg);
        g.fillRect(0, 0, w, headerH);

        // Status bar
        g.setColor(C_WHITE);
        g.setFont(fUi(11.5 * s, Font.BOLD));
        g.drawString("9:41", (int) (18 * s), (int) (17 * s));
        fillRoundRect(g, w - 38 * s, 7 * s, 20 * s, 10 * s, 2 * s, null, C_WHITE, 1.4 * s);
        fillRoundRect(g, w - 36 * s, 9 * s, 14 * s, 6 * s, 1 * s, C_WHITE, null, 0);

        // Row 1: Left Refresh circle button (48dp), Right Edit pill (NO TV toggle button!)
        double yR1 = 22 * s;
        double btnR = 17 * s;
        double cxRef = 16 * s + btnR;
        double cyRef = yR1 + btnR;
        g.setColor(new Color(255, 255, 255, 38));
        g.fill(new Ellipse2D.Double(cxRef - btnR, cyRef - btnR, btnR * 2, btnR * 2));
        g.setColor(C_WHITE);
        g.setStroke(new BasicStroke((float) (2.2 * s), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        double ar = 8.0 * s;
        g.draw(new Arc2D.Double(cxRef - ar, cyRef - ar, ar * 2, ar * 2, 40, 280, Arc2D.OPEN));

        // Right Edit pill
        double editW = 64 * s;
        double editH = 30 * s;
        double ex0 = w - 16 * s - editW;
        double ey0 = cyRef - editH / 2.0;
        fillRoundRect(g, ex0, ey0, editW, editH, 15 * s, isEdit ? C_WHITE : new Color(255, 255, 255, 42), null, 0);
        g.setColor(isEdit ? C_SAPPHIRE_BOT : C_WHITE);
        g.setFont(fUi(14.0 * s, Font.BOLD));
        g.drawString(isEdit ? "Done" : "Edit", (int) (ex0 + 18 * s), (int) (ey0 + 20 * s));

        // Row 2: 'wemi' wordmark + 'LAN SWITCHES' pill on left, white '+' button on right
        double yR2 = 90 * s;
        g.setColor(C_WHITE);
        g.setFont(fBrand(32 * s, Font.BOLD));
        g.drawString("wemi", (int) (20 * s), (int) yR2);

        double px0 = 122 * s;
        double py0 = yR2 - 19 * s;
        fillRoundRect(g, px0, py0, 96 * s, 20 * s, 6 * s, new Color(0x22, 0xD3, 0xEE, 58), null, 0);
        g.setColor(C_CYAN_SOFT);
        g.setFont(fUi(9.5 * s, Font.BOLD));
        g.drawString("LAN SWITCHES", (int) (px0 + 8 * s), (int) (py0 + 14 * s));

        // White '+' button on right
        double addSz = 31 * s;
        double ax0 = w - 18 * s - addSz;
        double ay0 = yR2 - 25 * s;
        fillRoundRect(g, ax0, ay0, addSz, addSz, 10 * s, C_WHITE, null, 0);
        g.setColor(C_TEAL_DARK);
        g.setStroke(new BasicStroke((float) (2.5 * s), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        double acx = ax0 + addSz / 2.0;
        double acy = ay0 + addSz / 2.0;
        double arm = 7.2 * s;
        g.draw(new Line2D.Double(acx - arm, acy, acx + arm, acy));
        g.draw(new Line2D.Double(acx, acy - arm, acx, acy + arm));

        return headerH;
    }

    private static void drawBottomNav(Graphics2D g, int w, int h, String selectedTab, double s) {
        int navH = (int) (56 * s);
        int y0 = h - navH;
        g.setColor(C_WHITE);
        g.fillRect(0, y0, w, navH);
        g.setColor(C_TEAL_PRIMARY);
        g.fillRect(0, y0, w, Math.max(2, (int) (2.2 * s)));

        String[] tabs = {"Devices", "Rules", "More"};
        int colW = w / 3;
        for (int i = 0; i < tabs.length; i++) {
            String tab = tabs[i];
            boolean active = tab.equals(selectedTab);
            double cx = i * colW + colW / 2.0;
            double cyIcon = y0 + 18 * s;
            Color tint = active ? C_TEAL_DARK : C_TEXT_SECONDARY;
            if (active) {
                fillRoundRect(g, cx - 28 * s, cyIcon - 12 * s, 56 * s, 24 * s, 12 * s, C_CYAN_SOFT, null, 0);
            }
            g.setColor(tint);
            g.setStroke(new BasicStroke((float) (2.0 * s), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            if ("Devices".equals(tab)) {
                fillRoundRect(g, cx - 8 * s, cyIcon - 7 * s, 12 * s, 14 * s, 4 * s, tint, null, 0);
                g.draw(new Line2D.Double(cx - 14 * s, cyIcon, cx - 8 * s, cyIcon));
                g.draw(new Line2D.Double(cx + 4 * s, cyIcon - 4 * s, cx + 11 * s, cyIcon - 4 * s));
                g.draw(new Line2D.Double(cx + 4 * s, cyIcon + 4 * s, cx + 11 * s, cyIcon + 4 * s));
            } else if ("Rules".equals(tab)) {
                fillRoundRect(g, cx - 9 * s, cyIcon - 9 * s, 18 * s, 18 * s, 3 * s, null, tint, 2.0 * s);
                g.draw(new Line2D.Double(cx - 9 * s, cyIcon - 3 * s, cx + 9 * s, cyIcon - 3 * s));
            } else {
                for (int dx : new int[]{-8, 0, 8}) {
                    double dr = 2.6 * s;
                    g.fill(new Ellipse2D.Double(cx + dx * s - dr, cyIcon - dr, dr * 2, dr * 2));
                }
            }
            g.setFont(fUi(11.0 * s, active ? Font.BOLD : Font.PLAIN));
            FontMetrics fm = g.getFontMetrics();
            int tw = fm.stringWidth(tab);
            g.drawString(tab, (int) (cx - tw / 2.0), (int) (y0 + 46 * s));
        }
    }

    private static int drawWifiHomeBar(Graphics2D g, int x0, int y0, int x1, double s) {
        int h = (int) (72 * s);
        fillRoundRect(g, x0, y0, x1 - x0, h, 12 * s, C_WHITE, new Color(0x08, 0x91, 0xB2, 95), 1.2 * s);

        double ir = 14 * s;
        double icx = x0 + 10 * s + ir;
        double icy = y0 + 7 * s + ir;
        g.setColor(C_CYAN_SOFT);
        g.fill(new Ellipse2D.Double(icx - ir, icy - ir, ir * 2, ir * 2));
        g.setColor(C_TEAL_DARK);
        g.setStroke(new BasicStroke((float) (1.9 * s), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Arc2D.Double(icx - 7.5 * s, icy - 5.5 * s, 15 * s, 15 * s, 45, 90, Arc2D.OPEN));
        g.draw(new Arc2D.Double(icx - 3.8 * s, icy - 1.8 * s, 7.6 * s, 7.6 * s, 45, 90, Arc2D.OPEN));

        double bw = 88 * s;
        double bh = 28 * s;
        double bx0 = x1 - 10 * s - bw;
        double by0 = y0 + 8 * s;

        double tx = icx + ir + 8 * s;
        double maxTextW = bx0 - tx - 8 * s;
        drawFittedString(g, "Main Home", tx, y0 + 19 * s, maxTextW, 14.0 * s, Font.BOLD, C_TEXT_PRIMARY);
        drawFittedString(g, "17/18 Active • 1 Offline • 192.168.1.0/24", tx, y0 + 34 * s, maxTextW, 10.5 * s, Font.PLAIN, C_TEXT_SECONDARY);

        fillRoundRect(g, bx0, by0, bw, bh, 8 * s, C_TEAL_PRIMARY, null, 0);
        drawFittedString(g, "Reconnect", bx0 + 13 * s, by0 + 18 * s, bw - 20 * s, 11.0 * s, Font.BOLD, C_WHITE);

        // Bottom chips
        double cy0 = y0 + 41 * s;
        double ch = 24 * s;
        double cx = x0 + 10 * s;
        String[][] chips = {{"Main Home (18)", "1"}, {"Lake Cabin (3)", "0"}, {"+ Add Home", "0"}, {"Reset", "0"}};
        for (String[] c : chips) {
            boolean sel = "1".equals(c[1]);
            g.setFont(fUi(10.5 * s, sel ? Font.BOLD : Font.PLAIN));
            int cw = g.getFontMetrics().stringWidth(c[0]) + (int) (16 * s);
            if (cx + cw > x1 - 8 * s) break;
            fillRoundRect(g, cx, cy0, cw, ch, 7 * s, sel ? C_TEAL_PRIMARY : C_WHITE, sel ? C_TEAL_PRIMARY : C_DIVIDER, 1.1 * s);
            g.setColor(sel ? C_WHITE : C_TEXT_PRIMARY);
            g.drawString(c[0], (int) (cx + 8 * s), (int) (cy0 + 16 * s));
            cx += cw + 5 * s;
        }
        return y0 + h + (int) (4 * s);
    }

    private static int drawSsdpBanner(Graphics2D g, int x0, int y0, int x1, double s) {
        int h = (int) (42 * s);
        fillRoundRect(g, x0, y0, x1 - x0, h, 11 * s, C_WHITE, new Color(0x08, 0x91, 0xB2, 95), 1.2 * s);

        double ir = 14 * s;
        double icx = x0 + 10 * s + ir;
        double icy = y0 + h / 2.0;
        fillRoundRect(g, icx - ir, icy - ir, ir * 2, ir * 2, 7 * s, C_CYAN_SOFT, null, 0);
        g.setColor(C_TEAL_DARK);
        g.setStroke(new BasicStroke((float) (1.7 * s)));
        g.draw(new Ellipse2D.Double(icx - 7.5 * s, icy - 7.5 * s, 15 * s, 15 * s));
        g.fill(new Ellipse2D.Double(icx - 2.8 * s, icy - 2.8 * s, 5.6 * s, 5.6 * s));

        double bw = 82 * s;
        double bh = 28 * s;
        double bx0 = x1 - 10 * s - bw;
        double by0 = y0 + (h - bh) / 2.0;

        double tx = icx + ir + 8 * s;
        double maxTextW = bx0 - tx - 8 * s;
        drawFittedString(g, "SSDP Local Network Discovery", tx, y0 + 17.5 * s, maxTextW, 13.0 * s, Font.BOLD, C_TEXT_PRIMARY);
        drawFittedString(g, "UDP 239.255.255.250:1900 • 17 switches", tx, y0 + 33 * s, maxTextW, 10.0 * s, Font.PLAIN, C_TEXT_SECONDARY);

        fillRoundRect(g, bx0, by0, bw, bh, 8 * s, C_TEAL_PRIMARY, null, 0);
        drawFittedString(g, "SSDP Scan", bx0 + 11 * s, by0 + 18 * s, bw - 18 * s, 11.0 * s, Font.BOLD, C_WHITE);

        return y0 + h + (int) (4 * s);
    }

    private static int drawRoomFilterBar(Graphics2D g, int x0, int y0, int x1, double s, String activeRoom) {
        int h = (int) (28 * s);
        double cx = x0;
        String[] rooms = {"Grouped by Room", "All Rooms (9)", "Living Room", "Bedroom", "Kitchen", "Hallway & Landing"};
        for (String r : rooms) {
            boolean isGroupChip = "Grouped by Room".equals(r);
            boolean isSel = r.equals(activeRoom);
            g.setFont(fUi(11.0 * s, (isGroupChip || isSel) ? Font.BOLD : Font.PLAIN));
            int cw = g.getFontMetrics().stringWidth(r) + (int) (18 * s);
            if (cx + cw > x1) break;
            Color bg = isGroupChip ? C_TEAL_DARK : (isSel ? C_CYAN_SOFT : C_WHITE);
            Color fg = isGroupChip ? C_WHITE : (isSel ? C_TEAL_DARK : C_TEXT_PRIMARY);
            Color out = isGroupChip ? C_TEAL_DARK : (isSel ? C_TEAL_PRIMARY : C_DIVIDER);
            fillRoundRect(g, cx, y0, cw, h, 7 * s, bg, out, 1.1 * s);
            g.setColor(fg);
            g.drawString(r, (int) (cx + 9 * s), (int) (y0 + 18 * s));
            cx += cw + 5 * s;
        }
        return y0 + h + (int) (4 * s);
    }

    private static int drawRoomGroupHeader(Graphics2D g, int x0, int y0, int x1, String roomName, int onCount, int totalCount, String subtitle, boolean isAnyOn, boolean showMasterDimmer, int masterDim, double s) {
        int rowH = (int) (50 * s);
        int expH = showMasterDimmer ? (int) (52 * s) : 0;
        int totalH = rowH + expH;
        Color borderCol = isAnyOn ? new Color(0x08, 0x91, 0xB2, 145) : C_DIVIDER;

        RoundRectangle2D cardShape = new RoundRectangle2D.Double(x0, y0, x1 - x0, totalH, 22 * s, 22 * s);
        LinearGradientPaint lg = new LinearGradientPaint(
            new Point2D.Double(x0, y0),
            new Point2D.Double(x1, y0),
            new float[]{0f, 1f},
            new Color[]{isAnyOn ? new Color(0xE0, 0xF7, 0xFA, 195) : new Color(0xF8, 0xFA, 0xFC), C_WHITE}
        );
        g.setPaint(lg);
        g.fill(cardShape);
        g.setColor(borderCol);
        g.setStroke(new BasicStroke((float) (1.4 * s)));
        g.draw(cardShape);

        double ibSz = 34 * s;
        double ibX = x0 + 11 * s;
        double ibY = y0 + (rowH - ibSz) / 2.0;
        fillRoundRect(g, ibX, ibY, ibSz, ibSz, 10 * s, isAnyOn ? C_TEAL_PRIMARY : new Color(0xCB, 0xD5, 0xE1), null, 0);
        fillRoundRect(g, ibX + 8 * s, ibY + 10 * s, ibSz - 16 * s, ibSz - 20 * s, 3.5 * s, null, C_WHITE, 1.8 * s);

        double ringR = 19.5 * s;
        double rcx = x1 - 11 * s - ringR;
        double rcy = y0 + rowH / 2.0;

        double tx = ibX + ibSz + 9 * s;
        double maxTitleW = (rcx - ringR - 10 * s) - tx;
        drawFittedString(g, roomName, tx, y0 + 20 * s, maxTitleW, 15.0 * s, Font.BOLD, C_TEXT_PRIMARY);

        // Second line: [X/Y ON] pill + subtitle
        String pillTxt = onCount + "/" + totalCount + " ON";
        g.setFont(fUi(9.8 * s, Font.BOLD));
        int pw = g.getFontMetrics().stringWidth(pillTxt) + (int) (12 * s);
        int ph = (int) (17 * s);
        int py0 = y0 + (int) (26 * s);
        fillRoundRect(g, tx, py0, pw, ph, 5 * s, isAnyOn ? C_TEAL_DARK : new Color(0xE2, 0xE8, 0xF0), null, 0);
        g.setColor(isAnyOn ? C_WHITE : C_TEXT_SECONDARY);
        g.drawString(pillTxt, (int) (tx + 6 * s), (int) (py0 + 12.2 * s));

        double subX = tx + pw + 7 * s;
        double maxSubW = (rcx - ringR - 10 * s) - subX;
        drawFittedString(g, subtitle, subX, py0 + 12.2 * s, maxSubW, 10.5 * s, Font.PLAIN, C_TEXT_SECONDARY);

        drawPowerRingButton(g, rcx, rcy, ringR, isAnyOn, true, s, false);

        if (showMasterDimmer) {
            int dy0 = y0 + rowH;
            fillRoundRect(g, x0 + 2, dy0, x1 - x0 - 4, expH - 2, 12 * s, C_EXPANDED_BG, null, 0);
            g.setColor(C_DIVIDER);
            g.drawLine(x0 + 2, dy0, x1 - 2, dy0);
            double sx0 = x0 + 14 * s;
            double sx1 = x1 - 14 * s;
            drawFittedString(g, roomName + " Master Brightness", sx0, dy0 + 17 * s, (sx1 - sx0) - 56 * s, 11.5 * s, Font.BOLD, C_SAPPHIRE_BOT);
            drawFittedString(g, masterDim + "%", sx1 - 42 * s, dy0 + 17 * s, 42 * s, 12.5 * s, Font.BOLD, C_TEAL_DARK);

            double sy = dy0 + 34 * s;
            double kx = sx0 + (sx1 - sx0) * (masterDim / 100.0);
            g.setStroke(new BasicStroke((float) (3.6 * s), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(C_DIVIDER);
            g.draw(new Line2D.Double(sx0, sy, sx1, sy));
            g.setColor(C_TEAL_PRIMARY);
            g.draw(new Line2D.Double(sx0, sy, kx, sy));
            double kr = 7.0 * s;
            g.fill(new Ellipse2D.Double(kx - kr, sy - kr, kr * 2, kr * 2));
        }

        return y0 + totalH + (int) (3.5 * s);
    }

    private static int drawDeviceRow(
        Graphics2D g, int x0, int y0, int x1,
        String name, String category, boolean isOn, boolean isReachable,
        int latencyMs, String ip, Integer brightness,
        boolean expanded, boolean nightMode, String serial, String sku, double s
    ) {
        int baseH = (int) (66 * s);
        int expH = expanded ? (int) (162 * s) : 0;
        int totalH = baseH + expH;

        Color borderCol = !isReachable
            ? new Color(0xDC, 0x26, 0x26, 85)
            : (isOn ? new Color(0x08, 0x91, 0xB2, 90) : C_DIVIDER);
        fillRoundRect(g, x0, y0, x1 - x0, totalH, 12 * s, C_WHITE, borderCol, 1.2 * s);

        // Line 1: Full-width unobstructed AutoShrinkSwitchName
        Color nameCol = isReachable ? C_TEXT_PRIMARY : new Color(0x0F, 0x17, 0x2A, 165);
        drawFittedString(g, name, x0 + 11 * s, y0 + 18.5 * s, (x1 - x0) - 22 * s, 14.5 * s, Font.BOLD, nameCol);

        // Line 2: Left 3-state Hardware Illustration
        double illX = x0 + 9 * s;
        double illY = y0 + 23 * s;
        double illSz = 39 * s;
        drawCategoryIllustration(g, illX, illY, illSz, illSz, category, isOn, isReachable, s);

        double ringR = 20 * s;
        double rcx = x1 - 10 * s - ringR;
        double rcy = y0 + 42 * s;

        // Middle badges
        double bx = illX + illSz + 8 * s;
        double by1 = y0 + 25 * s;
        double by2 = y0 + 45 * s;
        double ch = 17.5 * s;

        String connTxt = isReachable ? ("Active • " + latencyMs + "ms") : "Offline • Timeout";
        Color cBg = isReachable ? C_EMERALD_BG : C_RED_BG;
        Color cFg = isReachable ? C_EMERALD : C_RED_OFFLINE;
        g.setFont(fUi(9.8 * s, Font.BOLD));
        int cw = g.getFontMetrics().stringWidth(connTxt) + (int) (31 * s);
        fillRoundRect(g, bx, by1, cw, ch, 5.5 * s, cBg, new Color(cFg.getRed(), cFg.getGreen(), cFg.getBlue(), 90), 1.0 * s);

        g.setColor(cFg);
        double dr = 2.6 * s;
        g.fill(new Ellipse2D.Double(bx + 6.5 * s - dr, by1 + ch / 2.0 - dr, dr * 2, dr * 2));
        double sbX = bx + 11.5 * s;
        for (int bi = 0; bi < 4; bi++) {
            double bhBar = (2.8 + bi * 1.7) * s;
            double bxBar = sbX + bi * 2.9 * s;
            double byBar = by1 + ch - 3.2 * s - bhBar;
            g.setColor(isReachable ? cFg : new Color(cFg.getRed(), cFg.getGreen(), cFg.getBlue(), 65));
            g.fill(new Rectangle2D.Double(bxBar, byBar, 1.8 * s, bhBar));
        }
        g.setColor(cFg);
        g.drawString(connTxt, (int) (sbX + 13.5 * s), (int) (by1 + 12.5 * s));

        // PowerStatePill
        double px = bx + cw + 5 * s;
        if (isReachable) {
            String pLabel = (brightness != null)
                ? ((isOn ? "ON • " : "OFF • ") + brightness + "%")
                : (isOn ? "ON" : "OFF");
            Color pBg = isOn ? C_CYAN_SOFT : C_OFF_PILL_BG;
            Color pFg = isOn ? C_TEAL_DARK : C_OFF_ICON;
            Color pOut = isOn ? new Color(0x08, 0x91, 0xB2, 120) : new Color(0x23, 0x27, 0x2F, 140);
            int pw = g.getFontMetrics().stringWidth(pLabel) + (int) (21 * s);
            fillRoundRect(g, px, by1, pw, ch, 5.5 * s, pBg, pOut, 1.0 * s);
            drawIecPowerSymbol(g, px + 8.0 * s, by1 + ch / 2.0, 3.6 * s, pFg, 1.35 * s);
            g.setColor(pFg);
            g.drawString(pLabel, (int) (px + 14.5 * s), (int) (by1 + 12.5 * s));
        } else {
            drawFittedString(g, "Not detected", px, by1 + 12.5 * s, (rcx - ringR - 6 * s) - px, 10.0 * s, Font.PLAIN, C_TEXT_SECONDARY);
        }

        // Sub-row 2: IP + Semantic Category badge
        g.setColor(C_TEXT_SECONDARY);
        g.setFont(fUi(9.8 * s, Font.PLAIN));
        g.drawString(ip, (int) bx, (int) (by2 + 12 * s));
        int ipW = g.getFontMetrics().stringWidth(ip);

        double catX = bx + ipW + 6.5 * s;
        g.setFont(fUi(8.8 * s, Font.BOLD));
        int catW = g.getFontMetrics().stringWidth(category) + (int) (11 * s);
        fillRoundRect(g, catX, by2, catW, 16 * s, 4.5 * s, new Color(0xF1, 0xF5, 0xF9), new Color(0xCB, 0xD5, 0xE1), 0.8 * s);
        g.setColor(new Color(0x47, 0x55, 0x69));
        g.drawString(category, (int) (catX + 5.5 * s), (int) (by2 + 11.2 * s));

        drawPowerRingButton(g, rcx, rcy, ringR, isOn, isReachable, s, false);

        if (expanded) {
            int dy0 = y0 + baseH;
            fillRoundRect(g, x0 + 2, dy0, x1 - x0 - 4, expH - 2, 11 * s, C_EXPANDED_BG, null, 0);
            g.setColor(C_DIVIDER);
            g.drawLine(x0 + 2, dy0, x1 - 2, dy0);

            int lvl = brightness != null ? brightness : 75;
            double sx0 = x0 + 14 * s;
            double sx1 = x1 - 14 * s;
            drawFittedString(g, "Brightness Level", sx0, dy0 + 18 * s, (sx1 - sx0) - 55 * s, 12.5 * s, Font.BOLD, C_TEXT_PRIMARY);
            drawFittedString(g, lvl + "%", sx1 - 42 * s, dy0 + 18 * s, 42 * s, 13.5 * s, Font.BOLD, C_TEAL_DARK);

            double sy = dy0 + 32 * s;
            double kx = sx0 + (sx1 - sx0) * (lvl / 100.0);
            g.setStroke(new BasicStroke((float) (3.6 * s), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(C_DIVIDER);
            g.draw(new Line2D.Double(sx0, sy, sx1, sy));
            g.setColor(C_TEAL_PRIMARY);
            g.draw(new Line2D.Double(sx0, sy, kx, sy));
            double kr = 7.0 * s;
            g.fill(new Ellipse2D.Double(kx - kr, sy - kr, kr * 2, kr * 2));

            // Preset chips: 21%, 50%, 75%, 100%, Night Mode (20%)
            double cyChip = dy0 + 44 * s;
            double cxChip = sx0;
            double chChip = 23 * s;
            String[][] presets = {
                {"21%", lvl == 21 ? "1" : "0"},
                {"50%", lvl == 50 ? "1" : "0"},
                {"75%", lvl == 75 ? "1" : "0"},
                {"100%", lvl == 100 ? "1" : "0"},
                {"Night Mode (20%)", nightMode ? "1" : "0"}
            };
            for (String[] pr : presets) {
                boolean pSel = "1".equals(pr[1]);
                g.setFont(fUi(9.8 * s, pSel ? Font.BOLD : Font.PLAIN));
                int cwC = g.getFontMetrics().stringWidth(pr[0]) + (int) (14 * s);
                if (cxChip + cwC > sx1) break;
                fillRoundRect(g, cxChip, cyChip, cwC, chChip, 6.5 * s, pSel ? C_CYAN_SOFT : C_WHITE, pSel ? C_TEAL_PRIMARY : C_DIVIDER, 1.1 * s);
                g.setColor(pSel ? C_TEAL_DARK : C_TEXT_PRIMARY);
                g.drawString(pr[0], (int) (cxChip + 7 * s), (int) (cyChip + 15.2 * s));
                cxChip += cwC + 5 * s;
            }

            // Telemetry card inside drawer
            double ty0 = dy0 + 73 * s;
            double thBox = 42 * s;
            fillRoundRect(g, sx0, ty0, sx1 - sx0, thBox, 8 * s, C_WHITE, C_DIVIDER, 1.0 * s);

            double pwPing = 70 * s;
            double phPing = 23 * s;
            double px0Ping = sx1 - 8 * s - pwPing;
            double py0Ping = ty0 + (thBox - phPing) / 2.0;

            double maxTelW = px0Ping - (sx0 + 10 * s) - 8 * s;
            drawFittedString(g, "Connection: Active (" + latencyMs + "ms SOAP RTT)", sx0 + 10 * s, ty0 + 16 * s, maxTelW, 10.8 * s, Font.BOLD, C_TEAL_DARK);
            drawFittedString(g, "Checked: Just now • " + ip + ":49153", sx0 + 10 * s, ty0 + 32 * s, maxTelW, 9.8 * s, Font.PLAIN, C_TEXT_SECONDARY);

            fillRoundRect(g, px0Ping, py0Ping, pwPing, phPing, 6.5 * s, C_CYAN_SOFT, C_TEAL_PRIMARY, 1.0 * s);
            drawFittedString(g, "Ping RTT", px0Ping + 11 * s, py0Ping + 15.2 * s, pwPing - 16 * s, 9.8 * s, Font.BOLD, C_TEAL_DARK);

            double cfgW = 84 * s;
            double cfgH = 23 * s;
            double cfgX0 = sx1 - cfgW;
            double byMeta = ty0 + thBox + 15 * s;
            double maxMetaW = cfgX0 - sx0 - 8 * s;
            drawFittedString(g, "UPnP Endpoint: http://" + ip + ":49153", sx0, byMeta, maxMetaW, 9.8 * s, Font.PLAIN, C_TEXT_SECONDARY);
            drawFittedString(g, "SN: " + serial + " • " + sku, sx0, byMeta + 14 * s, maxMetaW, 9.2 * s, Font.PLAIN, C_TEXT_SECONDARY);

            fillRoundRect(g, cfgX0, byMeta - 8 * s, cfgW, cfgH, 7 * s, C_WHITE, C_DIVIDER, 1.0 * s);
            drawFittedString(g, "Configure IP", cfgX0 + 10 * s, byMeta + 7 * s, cfgW - 14 * s, 9.8 * s, Font.BOLD, C_TEXT_PRIMARY);
        }

        return y0 + totalH + (int) (3.5 * s);
    }

    private static BufferedImage renderPhone1Devices(int w, int h) {
        double s = w / 360.0;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = createG2(img);
        g.setColor(C_BG_GRAY);
        g.fillRect(0, 0, w, h);

        int y = drawTopHeader(g, w, s, false) + (int) (4 * s);
        int pad = (int) (10 * s);
        y = drawWifiHomeBar(g, pad, y, w - pad, s);
        y = drawSsdpBanner(g, pad, y, w - pad, s);
        y = drawRoomFilterBar(g, pad, y, w - pad, s, "All Rooms (9)");

        // Room 1: Living Room (ON switch)
        y = drawRoomGroupHeader(g, pad, y, w - pad, "Living Room", 3, 3, "3/3 Active • Avg 12ms", true, false, 80, s);
        y = drawDeviceRow(g, pad + (int) (8 * s), y, w - pad, "Christmas Tree Living", "Tree Lights", true, true, 11, "192.168.1.101", null, false, false, "", "", s);

        // Room 2: Bedroom (Accessible OFF Standby + Disconnected Offline both 100% visible!)
        y = drawRoomGroupHeader(g, pad, y, w - pad, "Bedroom", 0, 2, "1/2 Active • Avg 15ms", false, false, 100, s);
        y = drawDeviceRow(g, pad + (int) (8 * s), y, w - pad, "Bedroom Ceiling Fan", "Fan / Vent", false, true, 15, "192.168.1.114", null, false, false, "", "", s);
        y = drawDeviceRow(g, pad + (int) (8 * s), y, w - pad, "Lightbulb Bedroom", "Lightbulb", false, false, 0, "192.168.1.108", 100, false, false, "", "", s);

        drawBottomNav(g, w, h, "Devices", s);
        g.dispose();
        return img;
    }

    private static BufferedImage renderPhone2SsdpScanner(int w, int h) {
        double s = w / 360.0;
        BufferedImage img = renderPhone1Devices(w, h);
        Graphics2D g = createG2(img);
        g.setColor(new Color(15, 23, 42, 118));
        g.fillRect(0, 0, w, h);

        int sheetY0 = (int) (110 * s);
        fillRoundRect(g, 0, sheetY0, w, h - sheetY0 + 30, 24 * s, C_WHITE, null, 0);
        fillRoundRect(g, w / 2.0 - 19 * s, sheetY0 + 9 * s, 38 * s, 4 * s, 2 * s, C_DIVIDER, null, 0);

        int px = (int) (16 * s);
        int y = sheetY0 + (int) (32 * s);
        drawFittedString(g, "SSDP Network Discovery Scan", px, y, w - px * 2, 18.0 * s, Font.BOLD, C_TEXT_PRIMARY);
        drawFittedString(g, "Multicast M-SEARCH • 239.255.255.250:1900", px, y + 18 * s, w - px * 2, 11.5 * s, Font.PLAIN, C_TEXT_SECONDARY);
        y += (int) (34 * s);

        drawFittedString(g, "SSDP Search Target (ST Header):", px, y, w - px * 2, 11.0 * s, Font.BOLD, C_TEXT_SECONDARY);
        y += (int) (7 * s);
        double cx = px;
        String[][] targets = {{"Belkin BasicEvent", "1"}, {"All Smart Switches", "0"}, {"UPnP Root", "0"}};
        for (String[] t : targets) {
            boolean sel = "1".equals(t[1]);
            g.setFont(fUi(10.5 * s, sel ? Font.BOLD : Font.PLAIN));
            int cw = g.getFontMetrics().stringWidth(t[0]) + (int) (16 * s);
            if (cx + cw > w - px) break;
            fillRoundRect(g, cx, y, cw, 26 * s, 7 * s, sel ? C_CYAN_SOFT : C_WHITE, sel ? C_TEAL_PRIMARY : C_DIVIDER, 1.1 * s);
            g.setColor(sel ? C_TEAL_DARK : C_TEXT_PRIMARY);
            g.drawString(t[0], (int) (cx + 8 * s), (int) (y + 17 * s));
            cx += cw + 5 * s;
        }
        y += (int) (33 * s);

        fillRoundRect(g, px, y, w - px * 2, 42 * s, 9 * s, new Color(0xF1, 0xF5, 0xF9), null, 0);
        drawFittedString(g, "Local Interface: 192.168.1.50 (Wi-Fi)", px + 10 * s, y + 16 * s, (w - px * 2) - 115 * s, 10.5 * s, Font.BOLD, C_SAPPHIRE_BOT);
        drawFittedString(g, "Scan: 18ms", w - px - 82 * s, y + 16 * s, 74 * s, 10.5 * s, Font.BOLD, C_TEAL_DARK);
        drawFittedString(g, "M-SEARCH * HTTP/1.1 | HOST: 239.255.255.250:1900 | ST: urn:Belkin:service:basicevent:1", px + 10 * s, y + 33 * s, (w - px * 2) - 20 * s, 9.2 * s, Font.PLAIN, C_TEXT_SECONDARY);
        y += (int) (49 * s);

        double btnAdoptAllW = 80 * s;
        double btnRescanW = 70 * s;
        double btnRescanX = w - px - btnAdoptAllW - 6 * s - btnRescanW;
        drawFittedString(g, "Discovered Devices (4)", px, y + 19 * s, btnRescanX - px - 8 * s, 15.0 * s, Font.BOLD, C_TEXT_PRIMARY);

        fillRoundRect(g, btnRescanX, y, btnRescanW, 28 * s, 7 * s, C_WHITE, C_TEAL_PRIMARY, 1.1 * s);
        drawFittedString(g, "Rescan", btnRescanX + 12 * s, y + 18 * s, btnRescanW - 18 * s, 11.5 * s, Font.BOLD, C_TEAL_DARK);

        fillRoundRect(g, w - px - btnAdoptAllW, y, btnAdoptAllW, 28 * s, 7 * s, C_TEAL_PRIMARY, null, 0);
        drawFittedString(g, "Adopt All", w - px - btnAdoptAllW + 11 * s, y + 18 * s, btnAdoptAllW - 16 * s, 11.5 * s, Font.BOLD, C_WHITE);
        y += (int) (35 * s);

        Object[][] items = {
            {"Christmas Tree Living", "Tree Lights", true, "192.168.1.101:49153 • F7C063", "SN: 221517K01018AA • 11ms • /setup.xml", true},
            {"Living Room Floor Lamp", "Lightbulb", true, "192.168.1.103:49153 • WDS060", "SN: 231844K12004BB • 14ms • /setup.xml", true},
            {"Master Bath Ensuite Light", "Lightbulb", true, "192.168.1.112:49153 • F7C059", "SN: 211902K09011CC • 12ms • Room: Ensuite", true},
            {"Hallway 3-Way Switch", "Smart Switch", false, "192.168.1.118:49154 • WLS0403", "SN: 240199K33082DD • 16ms • Port Healed", false}
        };
        for (Object[] it : items) {
            int rh = (int) (66 * s);
            boolean synced = (Boolean) it[5];
            fillRoundRect(g, px, y, w - px * 2, rh, 11 * s, synced ? new Color(0xF8, 0xFA, 0xFC) : new Color(0xEB, 0xFA, 0xFC), synced ? C_DIVIDER : C_TEAL_PRIMARY, 1.1 * s);
            drawCategoryIllustration(g, px + 8 * s, y + 10 * s, 40 * s, 44 * s, (String) it[1], (Boolean) it[2], true, s);

            double bw = 68 * s;
            double bh = 26 * s;
            double bx0 = w - px - 9 * s - bw;
            double by0 = y + (rh - bh) / 2.0;

            double tx = px + 54 * s;
            double maxRowTextW = bx0 - tx - 8 * s;
            drawFittedString(g, (String) it[0], tx, y + 20 * s, maxRowTextW, 13.8 * s, Font.BOLD, C_TEXT_PRIMARY);
            drawFittedString(g, (String) it[3], tx, y + 37 * s, maxRowTextW, 10.5 * s, Font.BOLD, C_TEAL_DARK);
            drawFittedString(g, (String) it[4], tx, y + 53 * s, maxRowTextW, 9.5 * s, Font.PLAIN, C_TEXT_SECONDARY);

            fillRoundRect(g, bx0, by0, bw, bh, 7 * s, synced ? C_WHITE : C_TEAL_PRIMARY, synced ? C_TEAL_DARK : null, 1.0 * s);
            drawFittedString(g, synced ? "Adopted" : "Adopt", bx0 + (synced ? 10 : 16) * s, by0 + 17 * s, bw - 14 * s, 11.0 * s, Font.BOLD, synced ? C_TEAL_DARK : C_WHITE);
            y += rh + (int) (6 * s);
        }

        drawBottomNav(g, w, h, "Devices", s);
        g.dispose();
        return img;
    }

    private static BufferedImage renderPhone3DimmerControl(int w, int h) {
        double s = w / 360.0;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = createG2(img);
        g.setColor(C_BG_GRAY);
        g.fillRect(0, 0, w, h);

        int y = drawTopHeader(g, w, s, false) + (int) (6 * s);
        int pad = (int) (10 * s);
        y = drawRoomFilterBar(g, pad, y, w - pad, s, "Hallway & Landing");

        y = drawRoomGroupHeader(g, pad, y, w - pad, "Hallway & Landing", 2, 2, "2/2 Active • Avg 12ms", true, true, 75, s);
        y = drawDeviceRow(
            g, pad + (int) (8 * s), y, w - pad,
            "Lightbulb Landing", "Lightbulb", true, true,
            12, "192.168.1.105", 75,
            true, true, "231844K1200455", "WDS060 (Dimmer)", s
        );
        y = drawDeviceRow(
            g, pad + (int) (8 * s), y, w - pad,
            "Stairway Wall Sconce", "Lightbulb", true, true,
            13, "192.168.1.106", 50,
            false, false, "", "", s
        );

        drawBottomNav(g, w, h, "Devices", s);
        g.dispose();
        return img;
    }

    private static BufferedImage renderPhone4RulesAndMore(int w, int h) {
        double s = w / 360.0;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = createG2(img);
        g.setColor(C_BG_GRAY);
        g.fillRect(0, 0, w, h);

        int y = drawTopHeader(g, w, s, false) + (int) (8 * s);
        int pad = (int) (12 * s);

        // 1. Local Switch Rules & Timers Card
        int rH = (int) (182 * s);
        fillRoundRect(g, pad, y, w - pad * 2, rH, 12 * s, C_WHITE, C_DIVIDER, 1.0 * s);

        double newRuleW = 84 * s;
        double newRuleX = w - pad - 12 * s - newRuleW;
        double maxHeaderW = newRuleX - (pad + 14 * s) - 8 * s;
        drawFittedString(g, "Local Switch Rules & Timers", pad + 14 * s, y + 23 * s, maxHeaderW, 16.0 * s, Font.BOLD, C_TEXT_PRIMARY);
        drawFittedString(g, "Executes directly over LAN • No cloud", pad + 14 * s, y + 40 * s, maxHeaderW, 11.0 * s, Font.PLAIN, C_TEXT_SECONDARY);

        fillRoundRect(g, newRuleX, y + 12 * s, newRuleW, 28 * s, 8 * s, C_TEAL_PRIMARY, null, 0);
        drawFittedString(g, "+ New Rule", newRuleX + 11 * s, y + 30 * s, newRuleW - 16 * s, 11.0 * s, Font.BOLD, C_WHITE);

        String[][] rules = {
            {"Evening Holiday Lights", "Daily at 5:30 PM • Sunset", "Turn ON Living Room Tree & Candle"},
            {"Bedroom Motion Nightlight", "Motion Sensed • Hallway Sensor", "Set Lightbulb Landing to 21% (Night Mode)"}
        };
        int ry = y + (int) (52 * s);
        for (String[] rl : rules) {
            g.setColor(C_DIVIDER);
            g.drawLine((int) (pad + 10 * s), ry, (int) (w - pad - 10 * s), ry);
            double ibX = pad + 12 * s;
            double ibY = ry + 11 * s;
            double ibSz = 36 * s;
            fillRoundRect(g, ibX, ibY, ibSz, ibSz, 10 * s, C_CYAN_SOFT, null, 0);
            // Clock icon inside rule box
            g.setColor(C_TEAL_DARK);
            g.setStroke(new BasicStroke((float) (1.8 * s), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            double rClock = 9 * s;
            double ccx = ibX + ibSz / 2.0, ccy = ibY + ibSz / 2.0;
            g.draw(new Ellipse2D.Double(ccx - rClock, ccy - rClock, rClock * 2, rClock * 2));
            g.draw(new Line2D.Double(ccx, ccy - 5 * s, ccx, ccy));
            g.draw(new Line2D.Double(ccx, ccy, ccx + 4 * s, ccy + 2 * s));

            double swX0 = w - pad - 56 * s;
            double tx = pad + 56 * s;
            double maxRuleW = swX0 - tx - 8 * s;
            drawFittedString(g, rl[0], tx, ry + 21 * s, maxRuleW, 13.8 * s, Font.BOLD, C_TEXT_PRIMARY);
            drawFittedString(g, rl[1], tx, ry + 37 * s, maxRuleW, 11.0 * s, Font.PLAIN, C_TEXT_SECONDARY);
            drawFittedString(g, rl[2], tx, ry + 52 * s, maxRuleW, 10.5 * s, Font.BOLD, C_TEAL_DARK);

            fillRoundRect(g, swX0, ry + 18 * s, 44 * s, 24 * s, 12 * s, C_TEAL_PRIMARY, null, 0);
            g.setColor(C_WHITE);
            g.fill(new Ellipse2D.Double(w - pad - 33 * s, ry + 21 * s, 18 * s, 18 * s));
            ry += (int) (62 * s);
        }
        y += rH + (int) (8 * s);

        // 2. UPnP / SOAP Packet Log & Telemetry Exporter Card
        int logH = (int) (170 * s);
        fillRoundRect(g, pad, y, w - pad * 2, logH, 12 * s, C_WHITE, C_DIVIDER, 1.0 * s);
        double saveBtnX = w - pad - 102 * s;
        drawFittedString(g, "UPnP / SOAP Packet Log (24)", pad + 14 * s, y + 24 * s, saveBtnX - (pad + 14 * s) - 8 * s, 15.0 * s, Font.BOLD, C_TEXT_PRIMARY);
        drawFittedString(g, "Save/Share Log", saveBtnX, y + 24 * s, 92 * s, 11.0 * s, Font.BOLD, C_TEAL_DARK);

        String[][] logs = {
            {"Christmas Tree Living • SetBinaryState (1)", "http://192.168.1.101:49153/upnp/control/basicevent1", "HTTP 200 OK (11ms) • GENA Push Subscribed", "10:42:18"},
            {"Lightbulb Landing • SetBinaryState (75%)", "http://192.168.1.105:49153/upnp/control/basicevent1", "HTTP 200 OK (12ms) • ConfigureNightMode=1", "10:42:15"}
        };
        int ly = y + (int) (35 * s);
        for (String[] lgEntry : logs) {
            int lh = (int) (59 * s);
            fillRoundRect(g, pad + 10 * s, ly, w - pad * 2 - 20 * s, lh, 8 * s, new Color(0xF3, 0xF6, 0xF8), null, 0);
            double tsX = w - pad - 62 * s;
            drawFittedString(g, lgEntry[0], pad + 18 * s, ly + 17 * s, tsX - (pad + 18 * s) - 6 * s, 11.0 * s, Font.BOLD, C_TEXT_PRIMARY);
            drawFittedString(g, lgEntry[3], tsX, ly + 17 * s, 48 * s, 10.0 * s, Font.PLAIN, C_TEXT_SECONDARY);
            drawFittedString(g, lgEntry[1], pad + 18 * s, ly + 34 * s, w - pad * 2 - 36 * s, 9.8 * s, Font.BOLD, C_TEAL_DARK);
            drawFittedString(g, lgEntry[2], pad + 18 * s, ly + 49 * s, w - pad * 2 - 36 * s, 9.8 * s, Font.PLAIN, C_TEXT_SECONDARY);
            ly += lh + (int) (6 * s);
        }
        y += logH + (int) (8 * s);

        // 3. Open Source License (MIT License & Third-Party Notices) Card
        int licH = (int) (72 * s);
        fillRoundRect(g, pad, y, w - pad * 2, licH, 12 * s, C_WHITE, C_DIVIDER, 1.0 * s);
        double viewLicX = w - pad - 88 * s;
        drawFittedString(g, "Open Source License (MIT)", pad + 14 * s, y + 26 * s, viewLicX - (pad + 14 * s) - 8 * s, 14.5 * s, Font.BOLD, C_TEXT_PRIMARY);
        drawFittedString(g, "MIT License • Copyright (c) 2026 Michael R. Head", pad + 14 * s, y + 46 * s, viewLicX - (pad + 14 * s) - 8 * s, 10.5 * s, Font.PLAIN, C_TEXT_SECONDARY);
        drawFittedString(g, "View License", viewLicX, y + 36 * s, 78 * s, 11.0 * s, Font.BOLD, C_TEAL_DARK);

        drawBottomNav(g, w, h, "More", s);
        g.dispose();
        return img;
    }

    private static BufferedImage renderTabletDashboard(int w, int h) {
        double s = w / 600.0;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = createG2(img);
        g.setColor(C_BG_GRAY);
        g.fillRect(0, 0, w, h);

        int y = drawTopHeader(g, w, s, false) + (int) (8 * s);
        int pad = (int) (16 * s);
        y = drawWifiHomeBar(g, pad, y, w - pad, s);
        y = drawSsdpBanner(g, pad, y, w - pad, s);
        y = drawRoomFilterBar(g, pad, y, w - pad, s, "All Rooms (9)");

        int gap = (int) (12 * s);
        int colW = (w - pad * 2 - gap) / 2;
        int lx0 = pad, lx1 = pad + colW;
        int rx0 = lx1 + gap, rx1 = w - pad;

        int yl = y;
        yl = drawRoomGroupHeader(g, lx0, yl, lx1, "Living Room", 3, 3, "3/3 Active • Avg 12ms", true, false, 80, s * 0.88);
        yl = drawDeviceRow(g, lx0 + (int) (6 * s), yl, lx1, "Christmas Tree Living", "Tree Lights", true, true, 11, "192.168.1.101", null, false, false, "", "", s * 0.88);
        yl = drawDeviceRow(g, lx0 + (int) (6 * s), yl, lx1, "Christmas Candle", "Lightbulb", true, true, 13, "192.168.1.102", null, false, false, "", "", s * 0.88);
        yl = drawDeviceRow(g, lx0 + (int) (6 * s), yl, lx1, "Living Room Floor Lamp", "Lightbulb", true, true, 14, "192.168.1.103", 80, true, true, "231844K1200455", "WDS060 (Dimmer)", s * 0.88);

        int yr = y;
        yr = drawRoomGroupHeader(g, rx0, yr, rx1, "Bedroom", 1, 2, "2/3 Active • Avg 15ms", true, false, 100, s * 0.88);
        yr = drawDeviceRow(g, rx0 + (int) (6 * s), yr, rx1, "Master Bath Ensuite Light", "Lightbulb", true, true, 12, "192.168.1.112", 65, false, false, "", "", s * 0.88);
        yr = drawDeviceRow(g, rx0 + (int) (6 * s), yr, rx1, "Bedroom Ceiling Fan", "Fan / Vent", false, true, 15, "192.168.1.114", null, false, false, "", "", s * 0.88);
        yr = drawDeviceRow(g, rx0 + (int) (6 * s), yr, rx1, "Lightbulb Bedroom", "Lightbulb", false, false, 0, "192.168.1.108", 100, false, false, "", "", s * 0.88);

        yr = drawRoomGroupHeader(g, rx0, yr, rx1, "Kitchen", 2, 2, "2/2 Active • Avg 11ms", true, false, 100, s * 0.88);
        yr = drawDeviceRow(g, rx0 + (int) (6 * s), yr, rx1, "Christmas Tree Kitchen", "Tree Lights", true, true, 11, "192.168.1.104", null, false, false, "", "", s * 0.88);

        drawBottomNav(g, w, h, "Devices", s);
        g.dispose();
        return img;
    }

    private static BufferedImage renderTabletSsdpAndRules(int w, int h) {
        double s = w / 600.0;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = createG2(img);
        g.setColor(C_BG_GRAY);
        g.fillRect(0, 0, w, h);

        int y = drawTopHeader(g, w, s, false) + (int) (10 * s);
        int pad = (int) (16 * s);
        int gap = (int) (14 * s);
        int colW = (w - pad * 2 - gap) / 2;
        int lx0 = pad, lx1 = pad + colW;
        int rx0 = lx1 + gap, rx1 = w - pad;
        double innerW = (lx1 - lx0) - 28 * s;

        // Left Pane: 10.22.22.1 Zero-Cloud Switch Setup AP Wizard + Screenshot Debug Panel
        int cardH = (int) (680 * s);
        fillRoundRect(g, lx0, y, lx1 - lx0, cardH, 14 * s, C_WHITE, C_TEAL_PRIMARY, 1.5 * s);
        drawFittedString(g, "Setup Factory-Reset Switch AP", lx0 + 14 * s, y + 26 * s, innerW, 15.5 * s, Font.BOLD, C_TEXT_PRIMARY);
        drawFittedString(g, "Zero-Cloud 10.22.22.1 WiFiSetup1 Provisioning", lx0 + 14 * s, y + 44 * s, innerW, 11.0 * s, Font.BOLD, C_TEAL_DARK);

        int sy = y + (int) (56 * s);
        fillRoundRect(g, lx0 + 12 * s, sy, lx1 - lx0 - 24 * s, 52 * s, 10 * s, C_EMERALD_BG, C_EMERALD, 1.0 * s);
        drawFittedString(g, "Connected to Switch AP: 10.22.22.1:49153", lx0 + 20 * s, sy + 20 * s, innerW - 16 * s, 11.0 * s, Font.BOLD, C_EMERALD);
        drawFittedString(g, "Model: WLS0403 (3-Way) • Auth: AES-128 Method 2", lx0 + 20 * s, sy + 38 * s, innerW - 16 * s, 10.0 * s, Font.PLAIN, C_TEXT_SECONDARY);
        sy += (int) (64 * s);

        String[][] fields = {
            {"Switch Setup IP", "10.22.22.1"},
            {"Home 2.4 GHz Wi-Fi SSID (Ch 6 • -44 dBm)", "WeMi-Home-2.4G"},
            {"Custom Switch Name (Room Inferred: Porch)", "Porch Christmas Tree"},
            {"Icon Category Preview", "Tree Lights (Auto-Classified)"}
        };
        for (String[] fld : fields) {
            drawFittedString(g, fld[0], lx0 + 14 * s, sy + 12 * s, innerW, 10.0 * s, Font.BOLD, C_TEXT_SECONDARY);
            sy += (int) (16 * s);
            fillRoundRect(g, lx0 + 14 * s, sy, lx1 - lx0 - 28 * s, 32 * s, 8 * s, new Color(0xF8, 0xFA, 0xFC), C_DIVIDER, 1.0 * s);
            drawFittedString(g, fld[1], lx0 + 22 * s, sy + 21 * s, innerW - 16 * s, 11.5 * s, Font.BOLD, C_TEXT_PRIMARY);
            sy += (int) (40 * s);
        }

        // SETUP DEBUG DETAILS (SCREENSHOT) Box
        fillRoundRect(g, lx0 + 14 * s, sy, lx1 - lx0 - 28 * s, 180 * s, 10 * s, new Color(15, 23, 42), null, 0);
        drawFittedString(g, "SETUP DEBUG DETAILS (SCREENSHOT)", lx0 + 22 * s, sy + 21 * s, innerW - 16 * s, 10.5 * s, Font.BOLD, C_TV_CYAN);
        String[] dbg = {
            "AP Endpoint: http://10.22.22.1:49153/setup.xml",
            "HW SKU: WLS0403 • FW: WeMo_WW_2.00.11563",
            "Target SSID: WeMi-Home-2.4G (Ch 6, WPA2/AES)",
            "Crypto: Method 2 (RTOS) • LenSuffix=true",
            "GetNetworkStatus: 1 (Connected) • CloseSetup=OK",
            "File: wemi-telemetry-latest.txt [Export/Share]"
        };
        int dyDbg = sy + (int) (41 * s);
        for (String dl : dbg) {
            drawFittedString(g, dl, lx0 + 22 * s, dyDbg, innerW - 16 * s, 9.8 * s, Font.PLAIN, new Color(226, 232, 240));
            dyDbg += (int) (21 * s);
        }

        // Right Pane: Local Rules + Open Source MIT License + Live SOAP Log
        int ry = y;
        fillRoundRect(g, rx0, ry, rx1 - rx0, 290 * s, 14 * s, C_WHITE, C_DIVIDER, 1.0 * s);
        drawFittedString(g, "Local Switch Rules & Schedules", rx0 + 14 * s, ry + 26 * s, innerW, 15.5 * s, Font.BOLD, C_TEXT_PRIMARY);
        String[][] rItems = {
            {"Evening Holiday Lights", "5:30 PM Daily • Living Room ON"},
            {"Bedroom Motion Nightlight", "Motion Sensed • Landing 21%"},
            {"Candle Auto-Off Timer", "120m Countdown • Christmas Candle"}
        };
        int riy = ry + (int) (42 * s);
        for (String[] ri : rItems) {
            fillRoundRect(g, rx0 + 12 * s, riy, rx1 - rx0 - 24 * s, 68 * s, 10 * s, new Color(0xF8, 0xFA, 0xFC), C_DIVIDER, 1.0 * s);
            drawFittedString(g, ri[0], rx0 + 20 * s, riy + 25 * s, innerW - 16 * s, 13.0 * s, Font.BOLD, C_TEXT_PRIMARY);
            drawFittedString(g, ri[1], rx0 + 20 * s, riy + 47 * s, innerW - 16 * s, 10.8 * s, Font.BOLD, C_TEAL_DARK);
            riy += (int) (76 * s);
        }

        ry += (int) (304 * s);
        fillRoundRect(g, rx0, ry, rx1 - rx0, 376 * s, 14 * s, C_WHITE, C_DIVIDER, 1.0 * s);
        drawFittedString(g, "UPnP / SOAP Log & Open Source License", rx0 + 14 * s, ry + 25 * s, innerW, 14.5 * s, Font.BOLD, C_TEXT_PRIMARY);
        drawFittedString(g, "MIT License • Copyright (c) 2026 Michael R. Head", rx0 + 14 * s, ry + 43 * s, innerW, 10.5 * s, Font.BOLD, C_TEAL_DARK);
        String[][] lItems = {
            {"SetBinaryState (1) • Christmas Tree Living", "POST /upnp/control/basicevent1 -> 200 OK (11ms)"},
            {"SUBSCRIBE • GENA Push Eventing", "CALLBACK: <http://192.168.1.50:39412/upnp/event>"},
            {"ConfigureNightMode (20%) • Lightbulb Landing", "POST /upnp/control/basicevent1 -> 200 OK (12ms)"},
            {"Open Source Notices: AndroidX, Compose, Room", "pywemo MIT Protocol Reference (Clean-Room Kotlin)"}
        };
        int liy = ry + (int) (58 * s);
        for (String[] li : lItems) {
            fillRoundRect(g, rx0 + 12 * s, liy, rx1 - rx0 - 24 * s, 68 * s, 8 * s, new Color(0xF1, 0xF5, 0xF9), null, 0);
            drawFittedString(g, li[0], rx0 + 20 * s, liy + 24 * s, innerW - 16 * s, 11.0 * s, Font.BOLD, C_TEXT_PRIMARY);
            drawFittedString(g, li[1], rx0 + 20 * s, liy + 46 * s, innerW - 16 * s, 10.0 * s, Font.PLAIN, C_TEXT_SECONDARY);
            liy += (int) (75 * s);
        }

        drawBottomNav(g, w, h, "More", s);
        g.dispose();
        return img;
    }

    private static BufferedImage renderTvScreen(int w, int h, String mode) {
        double s = w / 960.0;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = createG2(img);

        LinearGradientPaint bgPaint = new LinearGradientPaint(
            new Point2D.Double(0, 0),
            new Point2D.Double(0, h),
            new float[]{0f, 0.5f, 1f},
            new Color[]{C_TV_BG_TOP, C_TV_BG_MID, C_TV_BG_BOT}
        );
        g.setPaint(bgPaint);
        g.fillRect(0, 0, w, h);

        int padX = (int) (22 * s);
        int y = (int) (16 * s);

        // 1. 10-Foot Android TV Top Header Bar (NO Exit TV button!)
        double bwLogo = 132 * s;
        double bhLogo = 42 * s;
        fillRoundRect(g, padX, y, bwLogo, bhLogo, 12 * s, new Color(0x08, 0x91, 0xB2, 65), C_TV_CYAN, 1.8 * s);
        g.setColor(C_WHITE);
        g.setFont(fBrand(20 * s, Font.BOLD));
        g.drawString("wemi TV", (int) (padX + 20 * s), (int) (y + 28 * s));

        int tx = (int) (padX + bwLogo + 14 * s);
        g.setColor(C_TV_TEXT);
        g.setFont(fUi(16 * s, Font.BOLD));
        g.drawString("10-Foot Smart Switch Remote Control", tx, (int) (y + 18 * s));
        g.setColor(C_TV_CYAN);
        g.setFont(fUi(12 * s, Font.PLAIN));
        g.drawString("14/18 Switches ON • UDP 239.255.255.250:1900 • GENA Real-Time Push", tx, (int) (y + 36 * s));

        // Right button: 'Hide Remote Pad' ONLY
        double rbW = 142 * s;
        double rbH = 36 * s;
        fillRoundRect(g, w - padX - rbW, y + 3 * s, rbW, rbH, 10 * s, new Color(0x08, 0x91, 0xB2, 65), C_TV_CYAN, 1.2 * s);
        drawFittedString(g, "Hide Remote Pad", w - padX - rbW + 18 * s, y + 25 * s, rbW - 24 * s, 12.0 * s, Font.BOLD, C_WHITE);
        y += (int) (bhLogo + 12 * s);

        // 2. One-Click TV Remote Scenes Strip
        String[][] scenes = {
            {"Movie Mode (20%)", "scenes".equals(mode) ? "1" : "0"},
            {"Holiday Lights ON", "focus".equals(mode) ? "1" : "0"},
            {"Bedtime All OFF", "0"},
            {"SSDP Scan", "dimmer".equals(mode) ? "1" : "0"}
        };
        double cx = padX;
        double sh = 36 * s;
        for (String[] sc : scenes) {
            boolean scFocus = "1".equals(sc[1]);
            g.setFont(fUi(13 * s, Font.BOLD));
            int sw = g.getFontMetrics().stringWidth(sc[0]) + (int) (36 * s);
            fillRoundRect(g, cx, y, sw, sh, 12 * s, scFocus ? C_TEAL_PRIMARY : new Color(0x10, 0x2A, 0x4C), scFocus ? C_WHITE : new Color(0x22, 0xD3, 0xEE, 120), (scFocus ? 2.0 : 1.0) * s);
            g.setColor(C_WHITE);
            g.drawString(sc[0], (int) (cx + 18 * s), (int) (y + 23 * s));
            cx += sw + 10 * s;
        }
        y += (int) (sh + 10 * s);

        // 3. Room Filter Rail + Master Room Toggle
        String[][] rooms = {
            {"All Rooms (18)", !"scenes".equals(mode) ? "1" : "0"},
            {"Living Room (3/3)", "scenes".equals(mode) ? "1" : "0"},
            {"Bedroom (1/3)", "0"},
            {"Kitchen (2/2)", "0"},
            {"Hallway & Landing (2/2)", "0"}
        };
        cx = padX;
        double rhPill = 34 * s;
        for (String[] rm : rooms) {
            boolean rSel = "1".equals(rm[1]);
            g.setFont(fUi(12.5 * s, rSel ? Font.BOLD : Font.PLAIN));
            int rw = g.getFontMetrics().stringWidth(rm[0]) + (int) (28 * s);
            fillRoundRect(g, cx, y, rw, rhPill, 17 * s, rSel ? C_TV_CYAN : new Color(0x0F, 0x25, 0x40), rSel ? C_WHITE : new Color(0x22, 0xD3, 0xEE, 95), 1.0 * s);
            g.setColor(rSel ? C_TV_BG_TOP : C_WHITE);
            g.drawString(rm[0], (int) (cx + 14 * s), (int) (y + 22 * s));
            cx += rw + 8 * s;
        }
        String mtLbl = "Toggle All in Living Room";
        g.setFont(fUi(12.5 * s, Font.BOLD));
        int mtW = g.getFontMetrics().stringWidth(mtLbl) + (int) (30 * s);
        fillRoundRect(g, cx, y, mtW, rhPill, 10 * s, C_TEAL_PRIMARY, null, 0);
        g.setColor(C_WHITE);
        g.drawString(mtLbl, (int) (cx + 15 * s), (int) (y + 22 * s));
        y += (int) (rhPill + 10 * s);

        // 4. Interactive On-Screen Virtual TV Remote D-Pad Bar
        double vbH = 78 * s;
        fillRoundRect(g, padX, y, w - padX * 2, vbH, 14 * s, new Color(0x0E, 0x27, 0x44), new Color(0x22, 0xD3, 0xEE, 185), 1.5 * s);
        String focusTitle = "dimmer".equals(mode)
            ? "TV Remote Focus (2/18): Living Room Floor Lamp"
            : "TV Remote Focus (1/18): Christmas Tree Living";
        String focusSub = "dimmer".equals(mode)
            ? "Living Room • 192.168.1.103:49153 • ON (80% Brightness)"
            : "Living Room • 192.168.1.101:49153 • Switch ON";
        drawFittedString(g, focusTitle, padX + 16 * s, y + 22 * s, w - padX * 2 - 32 * s, 14.0 * s, Font.BOLD, C_WHITE);
        drawFittedString(g, focusSub, padX + 16 * s, y + 39 * s, w - padX * 2 - 32 * s, 12.0 * s, Font.PLAIN, C_TV_CYAN);

        String[][] dpadBtns = {
            {"Prev", "0"},
            {"Next", "0"},
            {"OK: Toggle Switch", "1"},
            {"< -10%", "dimmer".equals(mode) ? "1" : "0"},
            {"+10% >", "dimmer".equals(mode) ? "1" : "0"},
            {"Room Toggle (Living Room)", "0"}
        };
        double bx = padX + 16 * s;
        double byDpad = y + 46 * s;
        double bhDpad = 25 * s;
        for (String[] db : dpadBtns) {
            boolean bHi = "1".equals(db[1]);
            g.setFont(fUi(11.5 * s, Font.BOLD));
            int bwD = g.getFontMetrics().stringWidth(db[0]) + (int) (24 * s);
            fillRoundRect(g, bx, byDpad, bwD, bhDpad, 7 * s, bHi ? C_CYAN_TOP : new Color(0x12, 0x31, 0x54), C_TV_CYAN, 1.0 * s);
            g.setColor(bHi ? C_TV_BG_TOP : C_WHITE);
            g.drawString(db[0], (int) (bx + 12 * s), (int) (byDpad + 17 * s));
            bx += bwD + 8 * s;
        }
        y += (int) (vbH + 12 * s);

        // 5. 3-Column 10-Foot Focusable Smart Switch Grid (6 visible cards)
        Object[][] cards = {
            {"Christmas Tree Living", "Tree Lights", true, true, "Active • 11ms • ON (192.168.1.101)", null, !"dimmer".equals(mode)},
            {"Living Room Floor Lamp", "Lightbulb", true, true, "Active • 14ms • ON (80%)", 80, "dimmer".equals(mode)},
            {"Christmas Candle", "Lightbulb", true, true, "Active • 13ms • ON (192.168.1.102)", null, false},
            {"Lightbulb Landing", "Lightbulb", true, true, "Active • 12ms • ON (75%)", 75, false},
            {"Bedroom Ceiling Fan", "Fan / Vent", false, true, "Active • 15ms • OFF (Standby)", null, false},
            {"Lightbulb Bedroom", "Lightbulb", false, false, "Disconnected • Press OK to Retry", null, false}
        };
        int cols = 3;
        int gap = (int) (12 * s);
        int cardW = (w - padX * 2 - gap * (cols - 1)) / cols;
        int cardH = (int) (126 * s);

        for (int idx = 0; idx < cards.length; idx++) {
            Object[] cd = cards[idx];
            String cName = (String) cd[0];
            String cCat = (String) cd[1];
            boolean cOn = (Boolean) cd[2];
            boolean cReach = (Boolean) cd[3];
            String cStat = (String) cd[4];
            Integer cDim = (Integer) cd[5];
            boolean cFocused = (Boolean) cd[6];

            int col = idx % cols;
            int row = idx / cols;
            int cx0 = padX + col * (cardW + gap);
            int cy0 = y + row * (cardH + gap);

            Color bgCard = !cReach ? C_TV_CARD_OFFLINE : (cOn ? C_TV_CARD_ACTIVE : C_TV_CARD);
            Color outCard = cFocused ? C_TV_CYAN : (cReach && cOn ? new Color(0x08, 0x91, 0xB2, 165) : new Color(0x1E, 0x29, 0x3B));
            fillRoundRect(g, cx0, cy0, cardW, cardH, 16 * s, bgCard, outCard, (cFocused ? 3.2 : 1.4) * s);

            drawFittedString(g, cName, cx0 + 14 * s, cy0 + 26 * s, cardW - 28 * s, 16.0 * s, Font.BOLD, C_TV_TEXT);

            drawCategoryIllustration(g, cx0 + 12 * s, cy0 + 36 * s, 38 * s, 44 * s, cCat, cOn, cReach, s);

            double pr = 19 * s;
            double pcx = cx0 + cardW - 14 * s - pr;
            double pcy = cy0 + 58 * s;

            double mtx = cx0 + 58 * s;
            double maxCardStatW = (pcx - pr - 8 * s) - mtx;
            g.setFont(fUi(10.5 * s, Font.BOLD));
            int catPw = g.getFontMetrics().stringWidth(cCat) + (int) (16 * s);
            fillRoundRect(g, mtx, cy0 + 38 * s, catPw, 20 * s, 6 * s, cFocused ? new Color(0x22, 0xD3, 0xEE, 56) : new Color(255, 255, 255, 22), null, 0);
            g.setColor(C_TV_CYAN);
            g.drawString(cCat, (int) (mtx + 8 * s), (int) (cy0 + 52 * s));

            Color statCol = !cReach ? new Color(0xF8, 0x71, 0x71) : (cOn ? C_TV_CYAN : new Color(0xFB, 0xBF, 0x24));
            drawFittedString(g, cStat, mtx, cy0 + 75 * s, maxCardStatW, 11.2 * s, Font.BOLD, statCol);

            if (!cReach) {
                g.setColor(new Color(0x45, 0x0A, 0x0A));
                g.fill(new Ellipse2D.Double(pcx - pr, pcy - pr, pr * 2, pr * 2));
                g.setColor(new Color(0xEF, 0x44, 0x44));
                g.setStroke(new BasicStroke((float) (2.0 * s)));
                g.draw(new Ellipse2D.Double(pcx - pr, pcy - pr, pr * 2, pr * 2));
                drawDisconnectedSymbol(g, pcx, pcy, pr * 0.45, new Color(0xFC, 0xA5, 0xA5), new Color(0xFC, 0xA5, 0xA5), 2.0 * s);
            } else if (cOn) {
                g.setColor(C_CYAN_TOP);
                g.fill(new Ellipse2D.Double(pcx - pr, pcy - pr, pr * 2, pr * 2));
                g.setColor(C_WHITE);
                g.setStroke(new BasicStroke((float) (2.0 * s)));
                g.draw(new Ellipse2D.Double(pcx - pr, pcy - pr, pr * 2, pr * 2));
                drawIecPowerSymbol(g, pcx, pcy, pr * 0.45, C_TV_BG_TOP, 2.2 * s);
            } else {
                g.setColor(C_OFF_RING);
                g.fill(new Ellipse2D.Double(pcx - pr, pcy - pr, pr * 2, pr * 2));
                g.setColor(new Color(0x47, 0x55, 0x69));
                g.setStroke(new BasicStroke((float) (2.0 * s)));
                g.draw(new Ellipse2D.Double(pcx - pr, pcy - pr, pr * 2, pr * 2));
                drawIecPowerSymbol(g, pcx, pcy, pr * 0.45, C_TV_SUBTEXT, 2.2 * s);
            }

            if (cDim != null && cReach) {
                double byBar = cy0 + cardH - 22 * s;
                double bx0B = cx0 + 14 * s;
                double bx1B = cx0 + cardW - 14 * s;
                fillRoundRect(g, bx0B, byBar, bx1B - bx0B, 8 * s, 4 * s, new Color(255, 255, 255, 38), null, 0);
                fillRoundRect(g, bx0B, byBar, (bx1B - bx0B) * (cDim / 100.0), 8 * s, 4 * s, C_TV_CYAN, null, 0);
            }
        }

        // 6. Bottom TV Remote Shortcut Legend & Live UPnP Status Ticker
        double legH = 38 * s;
        double ly0 = h - 14 * s - legH;
        fillRoundRect(g, padX, ly0, w - padX * 2, legH, 12 * s, new Color(0x0A, 0x19, 0x2F), new Color(0x1E, 0x3A, 0x5F), 1.0 * s);
        drawFittedString(g, "[OK / Select] Toggle Light   [Left / Right] Dimmer -/+10%   [Play] Room Master Toggle", padX + 14 * s, ly0 + 23 * s, (w - padX * 2) * 0.56, 11.5 * s, Font.BOLD, C_TV_CYAN);
        drawFittedString(g, "SetBinaryState (1) • http://192.168.1.101:49153/upnp/control/basicevent1", w - padX - 385 * s, ly0 + 23 * s, 370 * s, 10.0 * s, Font.PLAIN, C_TV_SUBTEXT);

        g.dispose();
        return img;
    }

    private static BufferedImage renderPlayStoreIcon(int sz) {
        double s = sz / 512.0;
        BufferedImage img = new BufferedImage(sz, sz, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = createG2(img);

        LinearGradientPaint bg = new LinearGradientPaint(
            new Point2D.Double(0, 0),
            new Point2D.Double(sz, sz),
            new float[]{0f, 0.5f, 1f},
            new Color[]{C_CYAN_TOP, C_TEAL_PRIMARY, C_SAPPHIRE_BOT}
        );
        g.setPaint(bg);
        g.fillRect(0, 0, sz, sz);

        double cx = sz / 2.0;
        double wy = 138 * s;
        g.setColor(new Color(0xBA, 0xF8, 0xFF));
        g.setStroke(new BasicStroke((float) (10 * s), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (double rArc : new double[]{38 * s, 64 * s, 90 * s}) {
            g.draw(new Arc2D.Double(cx - rArc, wy - rArc, rArc * 2, rArc * 2, 40, 100, Arc2D.OPEN));
        }

        fillRoundRect(g, 146 * s, 148 * s, 220 * s, 294 * s, 36 * s, new Color(0xF8, 0xFA, 0xFC), new Color(0xCB, 0xD5, 0xE1), 4 * s);
        fillRoundRect(g, 186 * s, 192 * s, 140 * s, 206 * s, 22 * s, C_WHITE, new Color(0xB4, 0xC4, 0xD4), 3 * s);
        drawPowerRingButton(g, cx, 295 * s, 46 * s, true, true, s * 1.6, false);

        g.dispose();
        return img;
    }

    private static BufferedImage renderFeatureGraphic(int w, int h) {
        double s = h / 500.0;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = createG2(img);

        LinearGradientPaint bg = new LinearGradientPaint(
            new Point2D.Double(0, 0),
            new Point2D.Double(w, h),
            new float[]{0f, 0.55f, 1f},
            new Color[]{new Color(8, 47, 73), C_SAPPHIRE_BOT, C_TV_BG_TOP}
        );
        g.setPaint(bg);
        g.fillRect(0, 0, w, h);

        int lx = (int) (42 * s);
        double maxLeftW = 500 * s;
        g.setColor(C_WHITE);
        g.setFont(fBrand(64 * s, Font.BOLD));
        g.drawString("wemi", lx, (int) (100 * s));

        fillRoundRect(g, lx + 200 * s, 66 * s, 162 * s, 34 * s, 10 * s, C_TEAL_DARK, C_TV_CYAN, 1.8 * s);
        drawFittedString(g, "LAN + ANDROID TV", lx + 215 * s, 88 * s, 134 * s, 13.0 * s, Font.BOLD, C_WHITE);

        drawFittedString(g, "WeMi Switch Control", lx, 150 * s, maxLeftW, 28.0 * s, Font.BOLD, C_TV_CYAN);
        drawFittedString(g, "Local UPnP / SSDP / SOAP / GENA Smart Switch Controller", lx, 180 * s, maxLeftW, 14.0 * s, Font.PLAIN, new Color(226, 232, 240));

        String[] pills = {"Zero-Cloud 10.22.22.1 Setup", "9-Port Self-Healing", "Auto Room Grouping", "10-Foot TV D-Pad"};
        double py = 208 * s;
        double px = lx;
        for (String p : pills) {
            g.setFont(fUi(12.0 * s, Font.BOLD));
            int pw = g.getFontMetrics().stringWidth(p) + (int) (22 * s);
            if (px + pw > lx + maxLeftW) {
                px = lx;
                py += 38 * s;
            }
            fillRoundRect(g, px, py, pw, 30 * s, 8 * s, new Color(15, 37, 64), C_TV_CYAN, 1.2 * s);
            g.setColor(C_WHITE);
            g.drawString(p, (int) (px + 11 * s), (int) (py + 20 * s));
            px += pw + 8 * s;
        }

        // Bottom left showcase of 3-State Power Rings
        double ry = 385 * s;
        Object[][] ringStates = {{"Reachable ON", true, true}, {"Standby OFF", false, true}, {"Offline Retry", false, false}};
        for (int i = 0; i < ringStates.length; i++) {
            double rcx = lx + 30 * s + i * 164 * s;
            drawPowerRingButton(g, rcx, ry, 26 * s, (Boolean) ringStates[i][1], (Boolean) ringStates[i][2], s * 1.05, true);
            drawFittedString(g, (String) ringStates[i][0], rcx + 33 * s, ry + 5 * s, 100 * s, 12.5 * s, Font.BOLD, C_WHITE);
        }

        // Right: Live UI Card Preview matching actual app look & feel
        int cx0 = (int) (556 * s), cy0 = (int) (26 * s), cx1 = (int) (992 * s), cy1 = (int) (474 * s);
        fillRoundRect(g, cx0, cy0, cx1 - cx0, cy1 - cy0, 20 * s, C_BG_GRAY, C_TV_CYAN, 2.0 * s);
        fillRoundRect(g, cx0 + 2, cy0 + 2, cx1 - cx0 - 4, 52 * s, 18 * s, C_TEAL_PRIMARY, null, 0);
        g.setColor(C_WHITE);
        g.setFont(fBrand(24 * s, Font.BOLD));
        g.drawString("wemi", (int) (cx0 + 18 * s), (int) (cy0 + 36 * s));
        drawFittedString(g, "17/18 Active • LAN", cx1 - 135 * s, cy0 + 34 * s, 120 * s, 12.0 * s, Font.BOLD, C_CYAN_SOFT);

        int cyRow = cy0 + (int) (60 * s);
        cyRow = drawRoomGroupHeader(g, cx0 + (int) (10 * s), cyRow, cx1 - (int) (10 * s), "Living Room", 3, 3, "3/3 Active • Avg 12ms", true, false, 80, s * 0.78);
        cyRow = drawDeviceRow(g, cx0 + (int) (16 * s), cyRow, cx1 - (int) (10 * s), "Christmas Tree Living", "Tree Lights", true, true, 11, "192.168.1.101", null, false, false, "", "", s * 0.78);
        cyRow = drawDeviceRow(g, cx0 + (int) (16 * s), cyRow, cx1 - (int) (10 * s), "Living Room Floor Lamp", "Lightbulb", true, true, 14, "192.168.1.103", 80, false, false, "", "", s * 0.78);
        cyRow = drawDeviceRow(g, cx0 + (int) (16 * s), cyRow, cx1 - (int) (10 * s), "Bedroom Ceiling Fan", "Fan / Vent", false, true, 15, "192.168.1.114", null, false, false, "", "", s * 0.78);
        cyRow = drawDeviceRow(g, cx0 + (int) (16 * s), cyRow, cx1 - (int) (10 * s), "Lightbulb Bedroom", "Lightbulb", false, false, 0, "192.168.1.108", 100, false, false, "", "", s * 0.78);

        g.dispose();
        return img;
    }

    private static BufferedImage renderTvBanner(int w, int h) {
        double s = w / 1280.0;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = createG2(img);

        LinearGradientPaint bg = new LinearGradientPaint(
            new Point2D.Double(0, 0),
            new Point2D.Double(w, h),
            new float[]{0f, 0.5f, 1f},
            new Color[]{C_TV_BG_TOP, C_SAPPHIRE_BOT, C_TV_BG_BOT}
        );
        g.setPaint(bg);
        g.fillRect(0, 0, w, h);

        double rcx = 200 * s;
        double rcy = h / 2.0;
        double rr = 108 * s;
        g.setColor(new Color(12, 74, 110, 140));
        g.fill(new Ellipse2D.Double(rcx - rr - 18 * s, rcy - rr - 18 * s, (rr + 18 * s) * 2, (rr + 18 * s) * 2));
        g.setColor(C_TV_CARD_ACTIVE);
        g.fill(new Ellipse2D.Double(rcx - rr, rcy - rr, rr * 2, rr * 2));
        g.setColor(C_TV_CYAN);
        g.setStroke(new BasicStroke((float) Math.max(2.5, 8.0 * s)));
        g.draw(new Ellipse2D.Double(rcx - rr, rcy - rr, rr * 2, rr * 2));
        drawIecPowerSymbol(g, rcx, rcy, rr * 0.44, C_TV_CYAN, Math.max(2.5, 10.0 * s));

        int tx = (int) (360 * s);
        double maxBannerW = w - tx - 40 * s;
        g.setColor(C_WHITE);
        g.setFont(fBrand(80 * s, Font.BOLD));
        g.drawString("wemi TV", tx, (int) (215 * s));

        drawFittedString(g, "WeMi Switch Control", tx, 290 * s, maxBannerW, 42 * s, Font.BOLD, C_TV_CYAN);
        drawFittedString(g, "10-Foot Smart Switch & Dimmer Remote for Android TV", tx, 340 * s, maxBannerW, 21 * s, Font.PLAIN, new Color(226, 232, 240));

        double sy = 388 * s;
        double sx = tx;
        for (String chip : new String[]{"Movie Mode (20%)", "Holiday Lights ON", "Bedtime All OFF", "SSDP Local LAN"}) {
            g.setFont(fUi(16 * s, Font.BOLD));
            int cw = g.getFontMetrics().stringWidth(chip) + (int) (30 * s);
            if (sx + cw > w - 36 * s) break;
            fillRoundRect(g, sx, sy, cw, 46 * s, 12 * s, new Color(16, 42, 76), C_TV_CYAN, Math.max(1.0, 2.0 * s));
            g.setColor(C_WHITE);
            g.drawString(chip, (int) (sx + 15 * s), (int) (sy + 29 * s));
            sx += cw + 12 * s;
        }

        g.dispose();
        return img;
    }

    private static void saveImage(BufferedImage img, String relPath, String format) throws Exception {
        File out = new File(ROOT, relPath);
        out.getParentFile().mkdirs();
        ImageIO.write(img, format, out);
        System.out.println("Saved " + relPath + " (" + img.getWidth() + "x" + img.getHeight() + ")");
    }

    public static void main(String[] args) throws Exception {
        initFonts();

        BufferedImage icon512 = renderPlayStoreIcon(512);
        saveImage(icon512, "play_store_assets/play_store_icon_512x512.png", "PNG");
        saveImage(icon512, "app/src/main/res/drawable/img_wemi_teal_icon_1794086800772.jpg", "JPG");

        BufferedImage feat1024 = renderFeatureGraphic(1024, 500);
        saveImage(feat1024, "play_store_assets/feature_graphic_1024x500.png", "PNG");
        saveImage(feat1024, "app/src/main/res/drawable/img_wemi_teal_feature_1794086803999.jpg", "JPG");

        BufferedImage tv1280 = renderTvBanner(1280, 720);
        saveImage(tv1280, "play_store_assets/tv/tv_banner_1280x720.png", "PNG");
        BufferedImage tv320 = renderTvBanner(320, 180);
        saveImage(tv320, "play_store_assets/tv/tv_banner_320x180.png", "PNG");
        saveImage(tv320, "app/src/main/res/drawable-xhdpi/tv_banner.png", "PNG");

        BufferedImage p1 = renderPhone1Devices(1080, 1920);
        saveImage(p1, "play_store_assets/screenshots/phone/phone_1_devices_and_power_rings.png", "PNG");
        saveImage(p1, "play_store_assets/screenshots/screenshot_1_devices_teal.png", "PNG");

        BufferedImage p2 = renderPhone2SsdpScanner(1080, 1920);
        saveImage(p2, "play_store_assets/screenshots/phone/phone_2_ssdp_network_scanner.png", "PNG");

        BufferedImage p3 = renderPhone3DimmerControl(1080, 1920);
        saveImage(p3, "play_store_assets/screenshots/phone/phone_3_inline_dimmer_control.png", "PNG");
        saveImage(p3, "play_store_assets/screenshots/screenshot_2_dimmer_control.png", "PNG");

        BufferedImage p4 = renderPhone4RulesAndMore(1080, 1920);
        saveImage(p4, "play_store_assets/screenshots/phone/phone_4_local_rules_and_upnp_log.png", "PNG");
        saveImage(p4, "play_store_assets/screenshots/screenshot_3_rules_and_upnp.png", "PNG");

        BufferedImage t7_1 = renderTabletDashboard(1200, 1920);
        saveImage(t7_1, "play_store_assets/screenshots/tablet_7_inch/tablet7_1_devices_dashboard.png", "PNG");
        BufferedImage t7_2 = renderTabletSsdpAndRules(1200, 1920);
        saveImage(t7_2, "play_store_assets/screenshots/tablet_7_inch/tablet7_2_ssdp_and_rules.png", "PNG");

        BufferedImage t10_1 = renderTabletDashboard(1600, 2560);
        saveImage(t10_1, "play_store_assets/screenshots/tablet_10_inch/tablet10_1_devices_dashboard.png", "PNG");
        BufferedImage t10_2 = renderTabletSsdpAndRules(1600, 2560);
        saveImage(t10_2, "play_store_assets/screenshots/tablet_10_inch/tablet10_2_ssdp_and_rules.png", "PNG");

        BufferedImage tv1 = renderTvScreen(1920, 1080, "focus");
        saveImage(tv1, "play_store_assets/screenshots/android_tv/tv_1_room_groups_and_remote_focus.png", "PNG");
        BufferedImage tv2 = renderTvScreen(1920, 1080, "dimmer");
        saveImage(tv2, "play_store_assets/screenshots/android_tv/tv_2_dimmer_and_ssdp_discovery.png", "PNG");
        BufferedImage tv3 = renderTvScreen(1920, 1080, "scenes");
        saveImage(tv3, "play_store_assets/screenshots/android_tv/tv_3_living_room_scenes_and_rules.png", "PNG");
    }
}
