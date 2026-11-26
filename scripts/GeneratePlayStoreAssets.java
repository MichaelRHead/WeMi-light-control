import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;

/**
 * Deterministic, anti-aliased Graphics2D Play Store screenshot & graphic asset generator
 * for WeMi Switch Control (v33.0+).
 *
 * Uses the platform SansSerif typeface (matching FontFamily.SansSerif in Type.kt),
 * exact Color.kt / HeaderAndNav.kt / TvScreen.kt / ProvisionDebugPanel.kt palette, and
 * AutoShrinkSwitchName-style drawFittedString so no text ever clips or overlaps adjacent
 * buttons or card edges.
 */
public class GeneratePlayStoreAssets {

    private static final File ROOT = new File(".").getAbsoluteFile();
    private static Font brandBase;
    private static Font uiBase;
    private static String appVersionTag = "v33.0";

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

    private static void initFonts() {
        brandBase = new Font("SansSerif", Font.BOLD, 24);
        uiBase = new Font("SansSerif", Font.PLAIN, 16);
        try {
            File gradleFile = new File(ROOT, "app/build.gradle.kts");
            if (gradleFile.isFile()) {
                String content = new String(Files.readAllBytes(gradleFile.toPath()), StandardCharsets.UTF_8);
                Matcher m = Pattern.compile("versionName\\s*=\\s*\"([^\"]+)\"").matcher(content);
                if (m.find()) {
                    appVersionTag = "v" + m.group(1).trim();
                }
            }
        } catch (Exception ignored) {
            appVersionTag = "v33.0";
        }
    }

    private static Font fBrand(double sizePx, int style) {
        return brandBase.deriveFont(style, (float) Math.max(8.0, sizePx));
    }

    private static Font fUi(double sizePx, int style) {
        return uiBase.deriveFont(style, (float) Math.max(8.0, sizePx));
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
        int headerH = (int) (68 * s);
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

        // Single compact row: 'wemi' wordmark + subtle version pill on left;
        // All Off, Refresh, Edit/Done, and white '+' button on right (HeaderAndNav.kt v33.0)
        double cy = 44 * s;
        g.setColor(C_WHITE);
        g.setFont(fBrand(29 * s, Font.BOLD));
        int brandX = (int) (14 * s);
        g.drawString("wemi", brandX, (int) (cy + 10 * s));
        int brandW = g.getFontMetrics().stringWidth("wemi");

        // Small, low-contrast version pill tucked neatly beside "wemi"
        g.setFont(fUi(9.5 * s, Font.PLAIN));
        int verTextW = g.getFontMetrics().stringWidth(appVersionTag);
        double verW = verTextW + 9 * s;
        double verH = 14.5 * s;
        double verX = brandX + brandW + 4.5 * s;
        double verY = cy + 10 * s - verH + 1.5 * s;
        fillRoundRect(g, verX, verY, verW, verH, 5 * s, new Color(255, 255, 255, 24), null, 0);
        g.setColor(new Color(255, 255, 255, 162));
        g.drawString(appVersionTag, (int) (verX + 4.5 * s), (int) (verY + 10.6 * s));

        // White '+' button on far right
        double addSz = 30 * s;
        double ax0 = w - 13 * s - addSz;
        double ay0 = cy - addSz / 2.0;
        fillRoundRect(g, ax0, ay0, addSz, addSz, 9.5 * s, C_WHITE, null, 0);
        g.setColor(C_TEAL_DARK);
        g.setStroke(new BasicStroke((float) (2.4 * s), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        double acx = ax0 + addSz / 2.0;
        double acy = ay0 + addSz / 2.0;
        double arm = 6.8 * s;
        g.draw(new Line2D.Double(acx - arm, acy, acx + arm, acy));
        g.draw(new Line2D.Double(acx, acy - arm, acx, acy + arm));

        // Edit / Done pill to the left of '+'
        double editW = 50 * s;
        double editH = 29 * s;
        double ex0 = ax0 - 6.5 * s - editW;
        double ey0 = cy - editH / 2.0;
        fillRoundRect(g, ex0, ey0, editW, editH, 14.5 * s, isEdit ? C_WHITE : new Color(255, 255, 255, 42), null, 0);
        g.setColor(isEdit ? C_SAPPHIRE_BOT : C_WHITE);
        g.setFont(fUi(13.0 * s, Font.BOLD));
        String editLabel = isEdit ? "Done" : "Edit";
        int editTw = g.getFontMetrics().stringWidth(editLabel);
        g.drawString(editLabel, (int) (ex0 + (editW - editTw) / 2.0), (int) (ey0 + 19.2 * s));

        // Refresh circle button to the left of Edit pill
        double btnR = 15 * s;
        double cxRef = ex0 - 6.5 * s - btnR;
        double cyRef = cy;
        g.setColor(new Color(255, 255, 255, 42));
        g.fill(new Ellipse2D.Double(cxRef - btnR, cyRef - btnR, btnR * 2, btnR * 2));
        g.setColor(C_WHITE);
        g.setStroke(new BasicStroke((float) (2.0 * s), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        double ar = 7.0 * s;
        g.draw(new Arc2D.Double(cxRef - ar, cyRef - ar, ar * 2, ar * 2, 35, 285, Arc2D.OPEN));
        double ahx = cxRef + ar * Math.cos(Math.toRadians(35));
        double ahy = cyRef - ar * Math.sin(Math.toRadians(35));
        g.draw(new Line2D.Double(ahx, ahy, ahx - 3.6 * s, ahy - 0.8 * s));
        g.draw(new Line2D.Double(ahx, ahy, ahx + 0.6 * s, ahy - 3.8 * s));

        // All Off pill button with power symbol to the left of Refresh (v25.0+)
        double allOffW = 73 * s;
        double allOffH = 29 * s;
        double aox0 = cxRef - btnR - 6.5 * s - allOffW;
        double aoy0 = cy - allOffH / 2.0;
        fillRoundRect(g, aox0, aoy0, allOffW, allOffH, 14.5 * s, new Color(255, 255, 255, 58), null, 0);
        drawIecPowerSymbol(g, aox0 + 14.5 * s, cy, 4.6 * s, C_WHITE, 1.65 * s);
        g.setColor(C_WHITE);
        g.setFont(fUi(12.2 * s, Font.BOLD));
        g.drawString("All Off", (int) (aox0 + 23.5 * s), (int) (aoy0 + 19.0 * s));

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

        // Bottom chips (Reset chip moved to More tab with confirmation in v33.0)
        double cy0 = y0 + 41 * s;
        double ch = 24 * s;
        double cx = x0 + 10 * s;
        String[][] chips = {{"Main Home (18)", "1"}, {"Lake Cabin (3)", "0"}, {"+ Add Home", "0"}};
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
        int expH = expanded ? (int) (218 * s) : 0;
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

            double cfgW = 76 * s;
            double pinW = 72 * s;
            double cfgH = 23 * s;
            double cfgX0 = sx1 - cfgW;
            double pinX0 = cfgX0 - 5 * s - pinW;
            double byMeta = ty0 + thBox + 15 * s;
            double maxMetaW = pinX0 - sx0 - 7 * s;
            drawFittedString(g, "UPnP: http://" + ip + ":49153", sx0, byMeta, maxMetaW, 9.6 * s, Font.PLAIN, C_TEXT_SECONDARY);
            drawFittedString(g, "SN: " + serial + " • " + sku, sx0, byMeta + 14 * s, maxMetaW, 9.0 * s, Font.PLAIN, C_TEXT_SECONDARY);

            fillRoundRect(g, pinX0, byMeta - 8 * s, pinW, cfgH, 7 * s, C_CYAN_SOFT, C_TEAL_PRIMARY, 1.0 * s);
            drawFittedString(g, "Pin Widget", pinX0 + 8 * s, byMeta + 7 * s, pinW - 12 * s, 9.5 * s, Font.BOLD, C_TEAL_DARK);

            fillRoundRect(g, cfgX0, byMeta - 8 * s, cfgW, cfgH, 7 * s, C_WHITE, C_DIVIDER, 1.0 * s);
            drawFittedString(g, "Configure IP", cfgX0 + 8 * s, byMeta + 7 * s, cfgW - 12 * s, 9.5 * s, Font.BOLD, C_TEXT_PRIMARY);

            // Compact On-Device Switch Rules section inside ExpandedDrawer
            double hwY0 = byMeta + 20 * s;
            double hwH = 52 * s;
            fillRoundRect(g, sx0, hwY0, sx1 - sx0, hwH, 8 * s, C_WHITE, C_DIVIDER, 1.0 * s);
            double manageW = 74 * s;
            double refW = 54 * s;
            double refH = 20 * s;
            double refX0 = sx1 - 8 * s - refW;
            double manageX0 = refX0 - 5 * s - manageW;
            double maxHwW = manageX0 - (sx0 + 9 * s) - 6 * s;
            drawFittedString(g, "On-Device Rules: On-Device Ready • Long Press", sx0 + 9 * s, hwY0 + 14 * s, maxHwW, 9.4 * s, Font.BOLD, C_TEAL_DARK);
            drawFittedString(g, "3 rule(s) stored on switch (DB v4)", sx0 + 9 * s, hwY0 + 25.5 * s, maxHwW, 8.6 * s, Font.PLAIN, C_TEXT_SECONDARY);
            fillRoundRect(g, manageX0, hwY0 + 6 * s, manageW, refH, 6 * s, C_TEAL_PRIMARY, null, 0);
            drawFittedString(g, "Manage Rules", manageX0 + 7 * s, hwY0 + 19.2 * s, manageW - 12 * s, 8.6 * s, Font.BOLD, C_WHITE);
            fillRoundRect(g, refX0, hwY0 + 6 * s, refW, refH, 6 * s, C_CYAN_SOFT, C_TEAL_PRIMARY, 0.9 * s);
            drawFittedString(g, "Refresh", refX0 + 9 * s, hwY0 + 19.2 * s, refW - 14 * s, 8.6 * s, Font.BOLD, C_TEAL_DARK);
            fillRoundRect(g, sx0 + 8 * s, hwY0 + 30.5 * s, (sx1 - sx0) - 16 * s, 16.5 * s, 5 * s, new Color(0xF8, 0xFA, 0xFC), C_DIVIDER, 0.8 * s);
            drawFittedString(g, "Goodnight Long Press  •  Hold paddle 2s → Toggle: Porch Entry Switch", sx0 + 13 * s, hwY0 + 41.5 * s, (sx1 - sx0) - 26 * s, 8.4 * s, Font.PLAIN, C_TEXT_PRIMARY);
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
        // SSDP Discovery Banner auto-hides in v33.0 once switches are present in the home
        y = drawRoomFilterBar(g, pad, y, w - pad, s, "All Rooms (9)");

        // Room 1: Living Room (ON Tree Lights + ON 80% Dimmer)
        y = drawRoomGroupHeader(g, pad, y, w - pad, "Living Room", 3, 3, "3/3 Active • Avg 12ms", true, false, 80, s);
        y = drawDeviceRow(g, pad + (int) (8 * s), y, w - pad, "Christmas Tree Living", "Tree Lights", true, true, 11, "192.168.1.101", null, false, false, "", "", s);
        y = drawDeviceRow(g, pad + (int) (8 * s), y, w - pad, "Living Room Floor Lamp", "Lightbulb", true, true, 14, "192.168.1.103", 80, false, false, "", "", s);

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
            true, true, "231844K1200455", "F7C059 (Dimmer)", s
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

        int y = drawTopHeader(g, w, s, false);
        int pad = (int) (10 * s);

        // 1. On-Device Switch Rules Header Card (Live On-Switch Management)
        int hdrH = (int) (96 * s);
        fillRoundRect(g, 0, y, w, hdrH, 0, C_WHITE, null, 0);
        double maxHdrW = w - 28 * s;

        g.setFont(fUi(14.5 * s, Font.BOLD));
        String hwTitle = "On-Device Switch Rules";
        g.setColor(C_TEXT_PRIMARY);
        g.drawString(hwTitle, (int) (14 * s), (int) (y + 22 * s));
        int hwTitleW = g.getFontMetrics().stringWidth(hwTitle);
        double osX = 14 * s + hwTitleW + 7 * s;
        double osW = 58 * s;
        fillRoundRect(g, osX, y + 10.5 * s, osW, 15.5 * s, 7.8 * s, C_CYAN_SOFT, null, 0);
        drawFittedString(g, "On-Switch", osX + 6 * s, y + 21.0 * s, osW - 10 * s, 8.8 * s, Font.BOLD, C_TEAL_DARK);

        drawFittedString(g, "Stored in switch memory • runs even when your phone is off", 14 * s, y + 38 * s, maxHdrW, 10.0 * s, Font.PLAIN, C_TEXT_SECONDARY);
        drawFittedString(g, "3 rule(s) stored • 2 ready • 1 unsupported", 14 * s, y + 53 * s, maxHdrW, 10.2 * s, Font.BOLD, C_TEAL_DARK);

        double newBtnW = 94 * s;
        double readBtnW = 96 * s;
        fillRoundRect(g, 14 * s, y + 61 * s, newBtnW, 27 * s, 13.5 * s, C_TEAL_PRIMARY, null, 0);
        drawFittedString(g, "+ New Rule", 25 * s, y + 78.2 * s, newBtnW - 18 * s, 10.8 * s, Font.BOLD, C_WHITE);
        fillRoundRect(g, 14 * s + newBtnW + 8 * s, y + 61 * s, readBtnW, 27 * s, 13.5 * s, C_WHITE, C_DIVIDER, 1.1 * s);
        drawFittedString(g, "Refresh All", 14 * s + newBtnW + 22 * s, y + 78.2 * s, readBtnW - 22 * s, 10.6 * s, Font.BOLD, C_TEAL_DARK);
        y += hdrH;
        g.setColor(C_DIVIDER);
        g.drawLine(0, y, w, y);
        y += (int) (7 * s);

        // 2. Expanded OpenWrt Switch Card (Porch Entry Switch — Live CRUD, Solar & Backups)
        int card1H = (int) (242 * s);
        fillRoundRect(g, pad, y, w - pad * 2, card1H, 11 * s, C_WHITE, C_DIVIDER, 1.0 * s);
        double chipW = 60 * s;
        double chipX = w - pad - 10 * s - chipW;
        drawFittedString(g, "Porch Entry Switch", pad + 12 * s, y + 18 * s, chipX - (pad + 12 * s) - 6 * s, 13.5 * s, Font.BOLD, C_TEXT_PRIMARY);
        drawFittedString(g, "Porch • WLS0403 (2nd-Gen 3-Way)", pad + 12 * s, y + 32 * s, chipX - (pad + 12 * s) - 6 * s, 10.2 * s, Font.PLAIN, C_TEXT_SECONDARY);
        fillRoundRect(g, chipX, y + 9 * s, chipW, 22 * s, 7 * s, C_WHITE, C_DIVIDER, 1.0 * s);
        drawFittedString(g, "Refresh", chipX + 10 * s, y + 23.5 * s, chipW - 16 * s, 9.8 * s, Font.BOLD, C_TEAL_DARK);

        // Capability, DB & Action Chips
        double bY = y + 38 * s;
        fillRoundRect(g, pad + 12 * s, bY, 136 * s, 16.5 * s, 8.2 * s, C_CYAN_SOFT, null, 0);
        drawFittedString(g, "On-Device Ready • Long Press", pad + 19 * s, bY + 11.5 * s, 126 * s, 9.0 * s, Font.BOLD, C_TEAL_DARK);
        fillRoundRect(g, pad + 153 * s, bY, 76 * s, 16.5 * s, 8.2 * s, new Color(0xF1, 0xF5, 0xF9), null, 0);
        drawFittedString(g, "DB v4 • 3 rule(s)", pad + 160 * s, bY + 11.5 * s, 66 * s, 9.0 * s, Font.PLAIN, C_TEXT_SECONDARY);

        double actY = y + 59 * s;
        String[] actChips = {"+ Add Rule", "Solar Location", "Backups (3)"};
        double actX = pad + 12 * s;
        for (int ai = 0; ai < actChips.length; ai++) {
            double cw = (ai == 0 ? 64 : (ai == 1 ? 80 : 70)) * s;
            fillRoundRect(g, actX, actY, cw, 20 * s, 7 * s, ai == 0 ? C_CYAN_SOFT : C_WHITE, ai == 0 ? C_TEAL_PRIMARY : C_DIVIDER, 0.9 * s);
            drawFittedString(g, actChips[ai], actX + 7 * s, actY + 13.5 * s, cw - 12 * s, 8.8 * s, Font.BOLD, ai == 0 ? C_TEAL_DARK : C_TEXT_PRIMARY);
            actX += cw + 6 * s;
        }

        // Live Editable On-Device Rule Rows (with Toggle Switch)
        String[][] hwRules = {
            {"Goodnight Long Press", "Long Press", "Hold wall paddle for 2s", "Toggle: Master Dimmer V2"},
            {"Evening Sunset Porch", "Schedule", "Sunset -15m → 11:00 PM • Daily", "Turn ON Porch Entry Switch"},
            {"Porch Auto-Off Delay", "Auto-Off", "Auto-Off after 30 min ON", "Turn OFF automatically"}
        };
        double hRy = y + 85 * s;
        for (String[] hr : hwRules) {
            double hRh = 43 * s;
            fillRoundRect(g, pad + 10 * s, hRy, w - pad * 2 - 20 * s, hRh, 8.5 * s, new Color(0xF8, 0xFA, 0xFC), C_DIVIDER, 0.9 * s);
            fillRoundRect(g, pad + 17 * s, hRy + 7.5 * s, 28 * s, 28 * s, 7 * s, C_CYAN_SOFT, null, 0);
            g.setColor(C_TEAL_DARK);
            g.setStroke(new BasicStroke((float) (1.6 * s), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            double icx = pad + 31 * s, icy = hRy + 21.5 * s;
            g.draw(new Ellipse2D.Double(icx - 6.5 * s, icy - 6.5 * s, 13 * s, 13 * s));
            g.draw(new Line2D.Double(icx, icy - 3.5 * s, icx, icy));
            g.draw(new Line2D.Double(icx, icy, icx + 3 * s, icy + 1.5 * s));

            double swW = 32 * s;
            double swX = w - pad - 17 * s - swW;
            double pillW = 50 * s;
            double pillX = swX - 6 * s - pillW;
            double rtx = pad + 52 * s;
            double rMaxW = pillX - rtx - 6 * s;
            drawFittedString(g, hr[0], rtx, hRy + 14.5 * s, rMaxW, 11.0 * s, Font.BOLD, C_TEXT_PRIMARY);
            fillRoundRect(g, pillX, hRy + 5 * s, pillW, 13.5 * s, 6.7 * s, C_CYAN_SOFT, null, 0);
            drawFittedString(g, hr[1], pillX + 5 * s, hRy + 14.3 * s, pillW - 8 * s, 8.2 * s, Font.BOLD, C_TEAL_DARK);
            // Live ON toggle pill
            fillRoundRect(g, swX, hRy + 12 * s, swW, 18 * s, 9 * s, C_TEAL_PRIMARY, null, 0);
            g.setColor(C_WHITE);
            g.fill(new Ellipse2D.Double(swX + swW - 15.5 * s, hRy + 14.5 * s, 13 * s, 13 * s));

            drawFittedString(g, hr[2], rtx, hRy + 27 * s, swX - rtx - 6 * s, 9.4 * s, Font.PLAIN, C_TEXT_SECONDARY);
            drawFittedString(g, hr[3], rtx, hRy + 38.5 * s, swX - rtx - 6 * s, 9.4 * s, Font.BOLD, C_TEAL_DARK);
            hRy += hRh + 4.5 * s;
        }
        drawFittedString(g, "Solar location on switch: Mountain View, CA, United States", pad + 12 * s, y + card1H - 7 * s, w - pad * 2 - 24 * s, 9.2 * s, Font.PLAIN, C_TEXT_SECONDARY);
        y += card1H + (int) (7 * s);

        // 3. Expanded RTOS Switch Card (Honest No On-Device Rules + Dimmer Night Mode note)
        int card2H = (int) (78 * s);
        fillRoundRect(g, pad, y, w - pad * 2, card2H, 11 * s, C_WHITE, C_DIVIDER, 1.0 * s);
        drawFittedString(g, "Master Dimmer V2", pad + 12 * s, y + 18 * s, chipX - (pad + 12 * s) - 6 * s, 13.5 * s, Font.BOLD, C_TEXT_PRIMARY);
        drawFittedString(g, "Bedroom • WDS060 (RTOS Dimmer)", pad + 12 * s, y + 32 * s, chipX - (pad + 12 * s) - 6 * s, 10.2 * s, Font.PLAIN, C_TEXT_SECONDARY);
        fillRoundRect(g, chipX, y + 9 * s, chipW, 22 * s, 7 * s, C_WHITE, C_DIVIDER, 1.0 * s);
        drawFittedString(g, "Refresh", chipX + 10 * s, y + 23.5 * s, chipW - 16 * s, 9.8 * s, Font.BOLD, C_TEAL_DARK);
        fillRoundRect(g, pad + 12 * s, y + 38 * s, 96 * s, 16 * s, 8 * s, new Color(0xF3, 0xF4, 0xF6), null, 0);
        drawFittedString(g, "No On-Device Rules", pad + 19 * s, y + 49 * s, 86 * s, 9.0 * s, Font.BOLD, new Color(0x4B, 0x55, 0x63));
        drawFittedString(g, "Firmware does not expose on-device rules storage. Dimmer Night Mode is still stored on-device.", pad + 12 * s, y + 68 * s, w - pad * 2 - 24 * s, 9.2 * s, Font.PLAIN, C_TEXT_SECONDARY);
        y += card2H + (int) (7 * s);

        // 4. Built-In Anti-Brick Protection, Clock Sync & Backup Recovery Summary Card
        int safeH = (int) (72 * s);
        fillRoundRect(g, pad, y, w - pad * 2, safeH, 11 * s, C_WHITE, C_DIVIDER, 1.0 * s);
        drawFittedString(g, "Armored Local Rules Engine", pad + 12 * s, y + 19 * s, w - pad * 2 - 24 * s, 12.2 * s, Font.BOLD, C_TEAL_DARK);
        drawFittedString(g, "• Auto-syncs switch clock before saving schedules or timers", pad + 12 * s, y + 36 * s, w - pad * 2 - 24 * s, 9.6 * s, Font.PLAIN, C_TEXT_PRIMARY);
        drawFittedString(g, "• Saves automatic pre-write backup with one-tap restore", pad + 12 * s, y + 50 * s, w - pad * 2 - 24 * s, 9.6 * s, Font.PLAIN, C_TEXT_PRIMARY);
        drawFittedString(g, "• Zero cloud account, zero GPS permissions — 100% local LAN", pad + 12 * s, y + 64 * s, w - pad * 2 - 24 * s, 9.6 * s, Font.PLAIN, C_TEXT_SECONDARY);

        drawBottomNav(g, w, h, "Rules", s);
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
        // SSDP Discovery Banner auto-hides in v33.0 once switches are present in the home
        y = drawRoomFilterBar(g, pad, y, w - pad, s, "All Rooms (9)");

        int gap = (int) (12 * s);
        int colW = (w - pad * 2 - gap) / 2;
        int lx0 = pad, lx1 = pad + colW;
        int rx0 = lx1 + gap, rx1 = w - pad;

        int yl = y;
        yl = drawRoomGroupHeader(g, lx0, yl, lx1, "Living Room", 3, 3, "3/3 Active • Avg 12ms", true, false, 80, s * 0.88);
        yl = drawDeviceRow(g, lx0 + (int) (6 * s), yl, lx1, "Christmas Tree Living", "Tree Lights", true, true, 11, "192.168.1.101", null, false, false, "", "", s * 0.88);
        yl = drawDeviceRow(g, lx0 + (int) (6 * s), yl, lx1, "Christmas Candle", "Lightbulb", true, true, 13, "192.168.1.102", null, false, false, "", "", s * 0.88);
        yl = drawDeviceRow(g, lx0 + (int) (6 * s), yl, lx1, "Living Room Floor Lamp", "Lightbulb", true, true, 14, "192.168.1.103", 80, true, true, "231844K1200455", "F7C059 (Dimmer)", s * 0.88);
        yl = drawRoomGroupHeader(g, lx0, yl, lx1, "Porch", 1, 1, "1/1 Active • Avg 13ms", true, false, 100, s * 0.88);
        yl = drawDeviceRow(g, lx0 + (int) (6 * s), yl, lx1, "Front Porch Switch", "Smart Switch", true, true, 13, "192.168.1.119", null, false, false, "", "", s * 0.88);

        int yr = y;
        yr = drawRoomGroupHeader(g, rx0, yr, rx1, "Bedroom", 1, 3, "2/3 Active • Avg 15ms", true, false, 100, s * 0.88);
        yr = drawDeviceRow(g, rx0 + (int) (6 * s), yr, rx1, "Master Bath Ensuite Light", "Lightbulb", true, true, 12, "192.168.1.112", 65, false, false, "", "", s * 0.88);
        yr = drawDeviceRow(g, rx0 + (int) (6 * s), yr, rx1, "Bedroom Ceiling Fan", "Fan / Vent", false, true, 15, "192.168.1.114", null, false, false, "", "", s * 0.88);
        yr = drawDeviceRow(g, rx0 + (int) (6 * s), yr, rx1, "Lightbulb Bedroom", "Lightbulb", false, false, 0, "192.168.1.108", 100, false, false, "", "", s * 0.88);

        yr = drawRoomGroupHeader(g, rx0, yr, rx1, "Kitchen", 2, 2, "2/2 Active • Avg 11ms", true, false, 60, s * 0.88);
        yr = drawDeviceRow(g, rx0 + (int) (6 * s), yr, rx1, "Christmas Tree Kitchen", "Tree Lights", true, true, 11, "192.168.1.104", null, false, false, "", "", s * 0.88);
        yr = drawDeviceRow(g, rx0 + (int) (6 * s), yr, rx1, "Kitchen Under-Cabinet", "Lightbulb", true, true, 12, "192.168.1.107", 60, false, false, "", "", s * 0.88);

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

        // Collapsible SETUP DIAGNOSTICS Panel (ProvisionDebugPanel.kt v33.0)
        fillRoundRect(g, lx0 + 14 * s, sy, lx1 - lx0 - 28 * s, 206 * s, 10 * s, new Color(0x12, 0x16, 0x1F), new Color(0x2E, 0x38, 0x4D), 1.0 * s);
        double dbgRight = lx1 - 22 * s;
        String[] dbgBtns = {"Save/Share", "Copy", "Hide"};
        double dbx = dbgRight;
        for (String bLbl : dbgBtns) {
            g.setFont(fUi(8.8 * s, Font.BOLD));
            double bwBtn = g.getFontMetrics().stringWidth(bLbl) + 11 * s;
            dbx -= bwBtn;
            fillRoundRect(g, dbx, sy + 9 * s, bwBtn, 19 * s, 9.5 * s, null, new Color(0x47, 0x55, 0x69), 1.0 * s);
            g.setColor(new Color(0xE2, 0xE8, 0xF0));
            g.drawString(bLbl, (int) (dbx + 5.5 * s), (int) (sy + 21.5 * s));
            dbx -= 4 * s;
        }
        drawFittedString(g, "SETUP DIAGNOSTICS", lx0 + 22 * s, sy + 21.5 * s, dbx - (lx0 + 22 * s) - 4 * s, 9.8 * s, Font.BOLD, new Color(0x8B, 0xE8, 0x36));
        String[][] dbg = {
            {"[NET] ip=10.22.22.100 boundWifi=true", "norm"},
            {"[PROBE] 10.22.22.1:49153 -> HTTP_200 (/setup.xml)", "ok"},
            {"[ID] SKU=WLS0403 • FW=2.00.11563 • Method 2", "norm"},
            {"[TARGET AP] WeMi-Home-2.4G (Ch 6, WPA2/AES)", "norm"},
            {"[STEP 5] GetNetworkStatus=1 (Connected)", "ok"},
            {"[CLOSE] CloseSetup -> success (wemi-telemetry-latest.txt)", "ok"}
        };
        int dyDbg = sy + (int) (42 * s);
        for (String[] dl : dbg) {
            Color dCol = "ok".equals(dl[1]) ? new Color(0x86, 0xEF, 0xAC) : new Color(0xE2, 0xE8, 0xF0);
            drawFittedString(g, dl[0], lx0 + 22 * s, dyDbg, innerW - 16 * s, 9.2 * s, Font.PLAIN, dCol);
            dyDbg += (int) (20.5 * s);
        }
        fillRoundRect(g, lx0 + 22 * s, dyDbg - 5 * s, innerW - 18 * s, 23 * s, 7 * s, new Color(0x1E, 0x29, 0x3B), new Color(0x33, 0x41, 0x55), 0.9 * s);
        drawFittedString(g, "Keep Dialog Open on Success: OFF (stays open on errors)", lx0 + 29 * s, dyDbg + 10 * s, innerW - 32 * s, 8.8 * s, Font.PLAIN, new Color(0xCB, 0xD5, 0xE1));

        // Right Pane: Live On-Device Switch Rules (On-Switch) + UPnP/SOAP Log & MIT License
        int ry = y;
        fillRoundRect(g, rx0, ry, rx1 - rx0, 356 * s, 14 * s, C_WHITE, C_DIVIDER, 1.0 * s);
        double newBtnTW = 74 * s;
        double newBtnTX = rx1 - 12 * s - newBtnTW;
        drawFittedString(g, "On-Device Switch Rules", rx0 + 14 * s, ry + 24 * s, newBtnTX - (rx0 + 14 * s) - 8 * s, 14.5 * s, Font.BOLD, C_TEXT_PRIMARY);
        drawFittedString(g, "On-Switch • Create, edit & backup inside switch memory", rx0 + 14 * s, ry + 40 * s, newBtnTX - (rx0 + 14 * s) - 8 * s, 9.6 * s, Font.PLAIN, C_TEXT_SECONDARY);
        fillRoundRect(g, newBtnTX, ry + 13 * s, newBtnTW, 24 * s, 12 * s, C_TEAL_PRIMARY, null, 0);
        drawFittedString(g, "+ New Rule", newBtnTX + 10 * s, ry + 28 * s, newBtnTW - 14 * s, 9.8 * s, Font.BOLD, C_WHITE);

        // Live Editable Switch Box
        int riy = ry + (int) (50 * s);
        fillRoundRect(g, rx0 + 12 * s, riy, rx1 - rx0 - 24 * s, 212 * s, 10 * s, new Color(0xF8, 0xFA, 0xFC), C_DIVIDER, 1.0 * s);
        drawFittedString(g, "Porch Entry Switch • WLS0403", rx0 + 20 * s, riy + 18 * s, innerW - 16 * s, 12.0 * s, Font.BOLD, C_TEXT_PRIMARY);
        fillRoundRect(g, rx0 + 20 * s, riy + 24 * s, 126 * s, 16 * s, 8 * s, C_CYAN_SOFT, null, 0);
        drawFittedString(g, "On-Device Ready • Long Press", rx0 + 26 * s, riy + 35 * s, 116 * s, 8.6 * s, Font.BOLD, C_TEAL_DARK);
        fillRoundRect(g, rx0 + 150 * s, riy + 24 * s, 72 * s, 16 * s, 8 * s, new Color(0xE2, 0xE8, 0xF0), null, 0);
        drawFittedString(g, "DB v4 • Backups", rx0 + 156 * s, riy + 35 * s, 64 * s, 8.4 * s, Font.PLAIN, C_TEXT_SECONDARY);

        String[][] tHwRules = {
            {"Goodnight Long Press (Long Press)", "Hold paddle 2s → Toggle: Master Dimmer V2"},
            {"Evening Sunset Porch (Schedule)", "Sunset -15m → 11:00 PM • Daily • Turn ON"},
            {"Porch Auto-Off Delay (Auto-Off)", "Auto-Off after 30 min ON • Turn OFF"}
        };
        int tryY = riy + (int) (46 * s);
        for (String[] tri : tHwRules) {
            fillRoundRect(g, rx0 + 20 * s, tryY, rx1 - rx0 - 40 * s, 44 * s, 8 * s, C_WHITE, C_DIVIDER, 0.9 * s);
            double tSwW = 28 * s;
            double tSwX = rx1 - 26 * s - tSwW;
            drawFittedString(g, tri[0], rx0 + 27 * s, tryY + 17 * s, tSwX - (rx0 + 27 * s) - 6 * s, 10.6 * s, Font.BOLD, C_TEXT_PRIMARY);
            drawFittedString(g, tri[1], rx0 + 27 * s, tryY + 34 * s, tSwX - (rx0 + 27 * s) - 6 * s, 9.6 * s, Font.BOLD, C_TEAL_DARK);
            fillRoundRect(g, tSwX, tryY + 14 * s, tSwW, 16 * s, 8 * s, C_TEAL_PRIMARY, null, 0);
            g.setColor(C_WHITE);
            g.fill(new Ellipse2D.Double(tSwX + tSwW - 13.5 * s, tryY + 16 * s, 12 * s, 12 * s));
            tryY += (int) (49 * s);
        }
        drawFittedString(g, "Solar location: Mountain View, CA • Auto clock sync", rx0 + 20 * s, riy + 204 * s, innerW - 16 * s, 8.8 * s, Font.PLAIN, C_TEXT_SECONDARY);

        // RTOS Unsupported capability readout card
        int rtosY = riy + (int) (220 * s);
        fillRoundRect(g, rx0 + 12 * s, rtosY, rx1 - rx0 - 24 * s, 74 * s, 10 * s, new Color(0xF8, 0xFA, 0xFC), C_DIVIDER, 1.0 * s);
        drawFittedString(g, "Master Dimmer V2 • WDS060 (RTOS)", rx0 + 20 * s, rtosY + 18 * s, innerW - 16 * s, 11.4 * s, Font.BOLD, C_TEXT_PRIMARY);
        fillRoundRect(g, rx0 + 20 * s, rtosY + 24 * s, 94 * s, 15.5 * s, 7.8 * s, new Color(0xE5, 0xE7, 0xEB), null, 0);
        drawFittedString(g, "No On-Device Rules", rx0 + 27 * s, rtosY + 35 * s, 84 * s, 8.6 * s, Font.BOLD, new Color(0x4B, 0x55, 0x63));
        drawFittedString(g, "Firmware does not expose rules DB. Dimmer Night Mode stays on-device.", rx0 + 20 * s, rtosY + 57 * s, innerW - 18 * s, 9.0 * s, Font.PLAIN, C_TEXT_SECONDARY);

        // Bottom Right Card: Live UPnP / SOAP Packet Log & MIT Open Source Notices
        ry += (int) (368 * s);
        fillRoundRect(g, rx0, ry, rx1 - rx0, 312 * s, 14 * s, C_WHITE, C_DIVIDER, 1.0 * s);
        drawFittedString(g, "UPnP / SOAP Packet Log & MIT License", rx0 + 14 * s, ry + 24 * s, innerW, 14.0 * s, Font.BOLD, C_TEXT_PRIMARY);
        drawFittedString(g, "MIT License • Armored Rules Sync & Headless Widgets", rx0 + 14 * s, ry + 41 * s, innerW, 10.0 * s, Font.BOLD, C_TEAL_DARK);
        String[][] lItems = {
            {"StoreRules • Porch Entry Switch (rules.db)", "POST /upnp/control/rules1 -> DB v4 verified (backup saved)"},
            {"TimeSync • Porch Entry Switch (timesync1)", "Auto-synced local clock & DST before rule write"},
            {"Widget#Toggle(ROOM) • Living Room (ON)", "Home Screen Widget -> 3 switches synced (12ms)"},
            {"Open Source Notices: AndroidX, Compose, Room", "Kotlin & Coroutines (Apache-2.0) • MIT License"}
        };
        int liy = ry + (int) (54 * s);
        for (String[] li : lItems) {
            fillRoundRect(g, rx0 + 12 * s, liy, rx1 - rx0 - 24 * s, 57 * s, 8 * s, new Color(0xF1, 0xF5, 0xF9), null, 0);
            drawFittedString(g, li[0], rx0 + 20 * s, liy + 21 * s, innerW - 16 * s, 10.6 * s, Font.BOLD, C_TEXT_PRIMARY);
            drawFittedString(g, li[1], rx0 + 20 * s, liy + 41 * s, innerW - 16 * s, 9.6 * s, Font.PLAIN, C_TEXT_SECONDARY);
            liy += (int) (63 * s);
        }

        drawBottomNav(g, w, h, "Rules", s);
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

        int padX = (int) (20 * s);
        int y = (int) (12 * s);
        boolean isDimmerMode = "dimmer".equals(mode);
        boolean isScenesMode = "scenes".equals(mode);

        // 1. Single Compact Top Header Bar (wemi TV + Active Count + Focused Pill + Scene Chips + Rescan)
        double barH = 38 * s;
        double cx = padX;

        double bwLogo = 104 * s;
        fillRoundRect(g, cx, y, bwLogo, barH, 10 * s, new Color(0x08, 0x91, 0xB2, 65), C_TV_CYAN, 1.5 * s);
        g.setColor(C_WHITE);
        g.setFont(fBrand(16.5 * s, Font.BOLD));
        g.drawString("wemi TV", (int) (cx + 16 * s), (int) (y + 25 * s));
        cx += bwLogo + 10 * s;

        String cntTxt = "14/18 ON";
        g.setFont(fUi(12 * s, Font.BOLD));
        int cntW = g.getFontMetrics().stringWidth(cntTxt) + (int) (20 * s);
        fillRoundRect(g, cx, y, cntW, barH, 8 * s, new Color(0x0F, 0x25, 0x40), new Color(0x1E, 0x3A, 0x5F), 1.0 * s);
        g.setColor(C_TV_CYAN);
        g.drawString(cntTxt, (int) (cx + 10 * s), (int) (y + 24 * s));
        cx += cntW + 10 * s;

        // Focused Switch Status Pill (TvHeadersAndScenes.kt v28.0+ single-row packing)
        String focusPillTxt = isDimmerMode
            ? "Floor Lamp: 80% • L/R Dim"
            : (isScenesMode ? "Movie 20% • OK Apply Scene" : "Christmas Tree Living • OK Toggle");
        Color focusPillBg = isDimmerMode ? new Color(0xFB, 0xBF, 0x24) : new Color(0x06, 0xB6, 0xD4, 52);
        Color focusPillBorder = isDimmerMode ? new Color(0xFB, 0xBF, 0x24) : new Color(0x22, 0xD3, 0xEE, 175);
        Color focusPillFg = isDimmerMode ? C_TV_BG_TOP : C_WHITE;
        g.setFont(fUi(11.5 * s, Font.BOLD));
        int fpW = Math.min((int) (175 * s), g.getFontMetrics().stringWidth(focusPillTxt) + (int) (20 * s));
        fillRoundRect(g, cx, y, fpW, barH, 9 * s, focusPillBg, focusPillBorder, 1.2 * s);
        drawFittedString(g, focusPillTxt, cx + 10 * s, y + 24 * s, fpW - 16 * s, 11.5 * s, Font.BOLD, focusPillFg);
        cx += fpW + 8 * s;

        if (isDimmerMode) {
            String doneTxt = "Done Dimming";
            g.setFont(fUi(11.0 * s, Font.BOLD));
            int dw = g.getFontMetrics().stringWidth(doneTxt) + (int) (18 * s);
            fillRoundRect(g, cx, y + 3 * s, dw, barH - 6 * s, 8 * s, new Color(0xFB, 0xBF, 0x24), C_WHITE, 1.2 * s);
            g.setColor(C_TV_BG_TOP);
            g.drawString(doneTxt, (int) (cx + 9 * s), (int) (y + 23 * s));
        }

        // Right-aligned Scene Chips + Rescan + Sync Button (TvHeadersAndScenes.kt v37.0)
        String[][] scenes = {
            {"Movie 20%", isScenesMode ? "1" : "0"},
            {"Holiday ON", "0"},
            {"All OFF", "0"},
            {"Rescan", "0"},
            {"Sync", "0"}
        };
        double rx = w - padX;
        for (int i = scenes.length - 1; i >= 0; i--) {
            String[] sc = scenes[i];
            boolean scFocus = "1".equals(sc[1]);
            g.setFont(fUi(12 * s, Font.BOLD));
            int sw = g.getFontMetrics().stringWidth(sc[0]) + (int) (22 * s);
            rx -= sw;
            fillRoundRect(
                g, rx, y, sw, barH, 10 * s,
                scFocus ? new Color(0x13, 0x3B, 0x66) : new Color(0x0F, 0x25, 0x40),
                scFocus ? C_TV_CYAN : new Color(0x22, 0xD3, 0xEE, 95),
                (scFocus ? 2.4 : 1.0) * s
            );
            g.setColor(C_WHITE);
            g.drawString(sc[0], (int) (rx + 11 * s), (int) (y + 24 * s));
            rx -= 7 * s;
        }
        y += (int) (barH + 12 * s);

        int livingLampDim = isScenesMode ? 20 : 80;
        String livingLampStat = "ON • " + livingLampDim + "% Brightness";
        // 2. Room Section 1: LIVING ROOM (Full-width header + 3 cards across)
        Object[][] livingRoomCards = {
            {"Christmas Tree Living", "Tree Lights", true, true, "ON • Active • 11ms", "192.168.1.101", null, !isDimmerMode && !isScenesMode, false},
            {"Living Room Floor Lamp", "Lightbulb", true, true, livingLampStat, "192.168.1.103", livingLampDim, isDimmerMode, isDimmerMode},
            {"Christmas Candle", "Lightbulb", true, true, "ON • Active • 13ms", "192.168.1.102", null, false, false}
        };
        y = drawTvRoomSection(g, padX, y, w - padX * 2, "LIVING ROOM", "3/3 ON", "Dimmers: " + livingLampDim + "%", livingRoomCards, s);

        int landingDim = isScenesMode ? 20 : 75;
        String landingStat = "ON • " + landingDim + "% Brightness";
        // 3. Room Section 2: BEDROOM & LANDING (Full-width header + 3 cards across)
        Object[][] bedroomCards = {
            {"Lightbulb Landing", "Lightbulb", true, true, landingStat, "192.168.1.105", landingDim, false, false},
            {"Bedroom Ceiling Fan", "Fan / Vent", false, true, "OFF • Active • 15ms", "192.168.1.114", null, false, false},
            {"Lightbulb Bedroom", "Lightbulb", false, false, "Disconnected • Press OK to Retry", "192.168.1.108", null, false, false}
        };
        y = drawTvRoomSection(g, padX, y, w - padX * 2, "BEDROOM & LANDING", "1/3 ON", "Dimmers: " + landingDim + "%", bedroomCards, s);

        if (isScenesMode) {
            String toastTxt = "TV Movie Mode: Dimmed 2 smart bulb(s) to 20%";
            g.setFont(fUi(12 * s, Font.BOLD));
            int tw = g.getFontMetrics().stringWidth(toastTxt) + (int) (32 * s);
            double tx0 = (w - tw) / 2.0;
            double ty0 = h - 38 * s;
            fillRoundRect(g, tx0, ty0, tw, 28 * s, 9 * s, new Color(0x0F, 0x25, 0x40, 235), C_TV_CYAN, 1.5 * s);
            g.setColor(C_TV_CYAN);
            g.drawString(toastTxt, (int) (tx0 + 16 * s), (int) (ty0 + 19 * s));
        }

        g.dispose();
        return img;
    }

    private static int drawTvRoomSection(
        Graphics2D g, int x0, int y0, int totalW,
        String roomTitle, String countBadge, String dimmerSummary,
        Object[][] cards, double s
    ) {
        int hdrH = (int) (34 * s);
        fillRoundRect(g, x0, y0, totalW, hdrH, 10 * s, new Color(0x0B, 0x19, 0x2C), new Color(0x1E, 0x3A, 0x5F), 1.0 * s);

        g.setColor(C_TV_CYAN);
        double dotR = 4.5 * s;
        g.fill(new Ellipse2D.Double(x0 + 14 * s - dotR, y0 + hdrH / 2.0 - dotR, dotR * 2, dotR * 2));

        g.setColor(C_WHITE);
        g.setFont(fUi(13 * s, Font.BOLD));
        g.drawString(roomTitle, (int) (x0 + 26 * s), (int) (y0 + 22 * s));
        int rtW = g.getFontMetrics().stringWidth(roomTitle);

        double bx = x0 + 36 * s + rtW;
        g.setFont(fUi(11 * s, Font.BOLD));
        int cbW = g.getFontMetrics().stringWidth(countBadge) + (int) (16 * s);
        fillRoundRect(g, bx, y0 + 6 * s, cbW, 22 * s, 6 * s, new Color(0x08, 0x91, 0xB2, 72), null, 0);
        g.setColor(C_TV_CYAN);
        g.drawString(countBadge, (int) (bx + 8 * s), (int) (y0 + 21 * s));

        if (dimmerSummary != null) {
            g.setColor(C_TV_SUBTEXT);
            g.setFont(fUi(11 * s, Font.PLAIN));
            g.drawString(dimmerSummary, (int) (bx + cbW + 12 * s), (int) (y0 + 21 * s));
        }

        // Right side of Room Master Header: -10% / +10% room dimmer step chips + Room Master (TvRoomSectionHeader.kt)
        double rightEdge = x0 + totalW - 14 * s;
        g.setColor(C_TV_SUBTEXT);
        g.setFont(fUi(11 * s, Font.PLAIN));
        String rmRight = "Room Master";
        int rmW = g.getFontMetrics().stringWidth(rmRight);
        rightEdge -= rmW;
        g.drawString(rmRight, (int) rightEdge, (int) (y0 + 21 * s));

        if (dimmerSummary != null) {
            String[] roomSteps = {"+10%", "-10%"};
            for (String stLbl : roomSteps) {
                g.setFont(fUi(10.5 * s, Font.BOLD));
                int stW = g.getFontMetrics().stringWidth(stLbl) + (int) (14 * s);
                rightEdge -= (8 * s + stW);
                fillRoundRect(g, rightEdge, y0 + 6 * s, stW, 22 * s, 6 * s, new Color(255, 255, 255, 26), null, 0);
                g.setColor(C_WHITE);
                g.drawString(stLbl, (int) (rightEdge + 7 * s), (int) (y0 + 21 * s));
            }
        }

        int yCards = y0 + hdrH + (int) (10 * s);
        int cols = 3;
        int gap = (int) (12 * s);
        int cardW = (totalW - gap * (cols - 1)) / cols;
        int cardH = (int) (156 * s);

        for (int idx = 0; idx < cards.length; idx++) {
            Object[] cd = cards[idx];
            String cName = (String) cd[0];
            String cCat = (String) cd[1];
            boolean cOn = (Boolean) cd[2];
            boolean cReach = (Boolean) cd[3];
            String cStat = (String) cd[4];
            String cIp = (String) cd[5];
            Integer cDim = (Integer) cd[6];
            boolean cFocused = (Boolean) cd[7];
            boolean cDimming = (Boolean) cd[8];

            int cx0 = x0 + idx * (cardW + gap);
            int cy0 = yCards;

            Color bgCard = cDimming
                ? new Color(0x23, 0x20, 0x14)
                : (cFocused ? new Color(0x13, 0x3B, 0x66) : (!cReach ? C_TV_CARD_OFFLINE : (cOn ? C_TV_CARD_ACTIVE : C_TV_CARD)));
            Color outCard = cDimming
                ? new Color(0xFB, 0xBF, 0x24)
                : (cFocused ? C_TV_CYAN : (cReach && cOn ? new Color(0x08, 0x91, 0xB2, 165) : new Color(0x1E, 0x29, 0x3B)));
            double strokeW = (cFocused || cDimming) ? 3.6 * s : 1.3 * s;

            fillRoundRect(g, cx0, cy0, cardW, cardH, 14 * s, bgCard, outCard, strokeW);

            // Top line: Switch Name + Focus / Dimming badge
            double badgeW = 0;
            if (cDimming || cFocused) {
                String bTxt = cDimming ? "DIM -/+" : "FOCUSED • OK";
                Color bCol = cDimming ? new Color(0xFB, 0xBF, 0x24) : C_TV_CYAN;
                g.setFont(fUi(9.8 * s, Font.BOLD));
                badgeW = g.getFontMetrics().stringWidth(bTxt) + 14 * s;
                fillRoundRect(g, cx0 + cardW - 12 * s - badgeW, cy0 + 10 * s, badgeW, 19 * s, 6 * s, bCol, null, 0);
                g.setColor(C_TV_BG_TOP);
                g.drawString(bTxt, (int) (cx0 + cardW - 12 * s - badgeW + 7 * s), (int) (cy0 + 23 * s));
            }
            drawFittedString(g, cName, cx0 + 13 * s, cy0 + 24 * s, cardW - 26 * s - badgeW, 15.5 * s, Font.BOLD, C_TV_TEXT);

            // Second line: Illustration + Category + IP + Status + Power Ring
            drawCategoryIllustration(g, cx0 + 12 * s, cy0 + 36 * s, 35 * s, 42 * s, cCat, cOn, cReach, s);

            double pr = 18 * s;
            double pcx = cx0 + cardW - 13 * s - pr;
            double pcy = cy0 + 57 * s;

            double mtx = cx0 + 54 * s;
            double maxCardStatW = (pcx - pr - 8 * s) - mtx;
            g.setFont(fUi(10.0 * s, Font.BOLD));
            int catPw = g.getFontMetrics().stringWidth(cCat) + (int) (12 * s);
            fillRoundRect(g, mtx, cy0 + 36 * s, catPw, 18 * s, 5 * s, cFocused ? new Color(0x22, 0xD3, 0xEE, 56) : new Color(255, 255, 255, 22), null, 0);
            g.setColor(C_TV_CYAN);
            g.drawString(cCat, (int) (mtx + 6 * s), (int) (cy0 + 49 * s));

            g.setColor(C_TV_SUBTEXT);
            g.setFont(fUi(9.8 * s, Font.PLAIN));
            g.drawString(cIp, (int) (mtx + catPw + 6 * s), (int) (cy0 + 49 * s));

            Color statCol = !cReach
                ? new Color(0xF8, 0x71, 0x71)
                : (cDimming ? new Color(0xFB, 0xBF, 0x24) : (cOn ? C_TV_CYAN : C_TV_SUBTEXT));
            drawFittedString(g, cStat, mtx, cy0 + 70 * s, maxCardStatW, 11.5 * s, Font.BOLD, statCol);

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
                double boxY = cy0 + 88 * s;
                double boxH = cDimming ? 56 * s : 40 * s;
                Color dimBoxBg = cDimming ? new Color(0xFB, 0xBF, 0x24, 56) : new Color(255, 255, 255, 16);
                Color dimBoxBorder = cDimming ? new Color(0xFB, 0xBF, 0x24) : new Color(0x22, 0xD3, 0xEE, 70);
                fillRoundRect(g, cx0 + 12 * s, boxY, cardW - 24 * s, boxH, 8 * s, dimBoxBg, dimBoxBorder, (cDimming ? 2.2 : 1.0) * s);

                // Left '-' circle button + progress bar + '%' + right '+' circle button
                double btnR = 10 * s;
                double btnCy = boxY + (cDimming ? 19 * s : boxH / 2.0);
                double minusCx = cx0 + 28 * s;
                double plusCx = cx0 + cardW - 28 * s;
                Color btnBg = new Color(255, 255, 255, 36);
                Color btnFg = cDimming ? new Color(0xFB, 0xBF, 0x24) : C_WHITE;

                g.setColor(btnBg);
                g.fill(new Ellipse2D.Double(minusCx - btnR, btnCy - btnR, btnR * 2, btnR * 2));
                g.fill(new Ellipse2D.Double(plusCx - btnR, btnCy - btnR, btnR * 2, btnR * 2));
                g.setColor(btnFg);
                g.setFont(fUi(12.5 * s, Font.BOLD));
                g.drawString("-", (int) (minusCx - 3 * s), (int) (btnCy + 4 * s));
                g.drawString("+", (int) (plusCx - 4 * s), (int) (btnCy + 4 * s));

                double byBar = btnCy - 4 * s;
                double bx0B = minusCx + btnR + 8 * s;
                double bx1B = plusCx - btnR - 38 * s;
                Color barCol = cDimming ? new Color(0xFB, 0xBF, 0x24) : C_TV_CYAN;
                fillRoundRect(g, bx0B, byBar, bx1B - bx0B, 8 * s, 4 * s, new Color(255, 255, 255, 38), null, 0);
                fillRoundRect(g, bx0B, byBar, (bx1B - bx0B) * (cDim / 100.0), 8 * s, 4 * s, barCol, null, 0);

                g.setColor(cDimming ? new Color(0xFB, 0xBF, 0x24) : C_WHITE);
                g.setFont(fUi(11 * s, Font.BOLD));
                g.drawString(cDim + "%", (int) (bx1B + 6 * s), (int) (btnCy + 4 * s));

                if (cDimming) {
                    String dimHint = "Left: -10%  •  Right: +10%   (Up: Toggle • Down: Next)";
                    drawFittedString(g, dimHint, cx0 + 20 * s, boxY + 46 * s, cardW - 40 * s, 10.0 * s, Font.BOLD, new Color(0xFB, 0xBF, 0x24));
                }
            }
        }

        return yCards + cardH + (int) (14 * s);
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

        double badgeX = lx + 204 * s;
        double badgeW = 228 * s;
        fillRoundRect(g, badgeX, 66 * s, badgeW, 34 * s, 10 * s, C_TEAL_DARK, C_TV_CYAN, 1.8 * s);
        String badgeLbl = "CHROMEBOOK • WIDGETS • TV";
        g.setFont(fUi(12.2 * s, Font.BOLD));
        int badgeTw = g.getFontMetrics().stringWidth(badgeLbl);
        drawFittedString(g, badgeLbl, badgeX + Math.max(10 * s, (badgeW - badgeTw) / 2.0), 88 * s, badgeW - 20 * s, 12.2 * s, Font.BOLD, C_WHITE);

        drawFittedString(g, "WeMi Switch Control", lx, 150 * s, maxLeftW, 28.0 * s, Font.BOLD, C_TV_CYAN);
        drawFittedString(g, "Local UPnP / SSDP / SOAP / GENA Smart Switch Controller", lx, 180 * s, maxLeftW, 14.0 * s, Font.PLAIN, new Color(226, 232, 240));

        String[] pills = {"Chromebook Ready", "Home Screen Widgets", "Zero-Cloud 10.22.22.1 Setup", "3-Col Room TV Grid"};
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

        // Right: Live UI Card Preview matching actual single-row TopHeader look & feel
        int cx0 = (int) (556 * s), cy0 = (int) (26 * s), cx1 = (int) (992 * s), cy1 = (int) (474 * s);
        fillRoundRect(g, cx0, cy0, cx1 - cx0, cy1 - cy0, 20 * s, C_BG_GRAY, C_TV_CYAN, 2.0 * s);
        fillRoundRect(g, cx0 + 2, cy0 + 2, cx1 - cx0 - 4, 52 * s, 18 * s, C_TEAL_PRIMARY, null, 0);
        g.setColor(C_WHITE);
        g.setFont(fBrand(24 * s, Font.BOLD));
        g.drawString("wemi", (int) (cx0 + 16 * s), (int) (cy0 + 36 * s));
        int prevBrandW = g.getFontMetrics().stringWidth("wemi");

        // Subtle version pill beside wemi in feature preview
        g.setFont(fUi(8.8 * s, Font.PLAIN));
        double pvW = g.getFontMetrics().stringWidth(appVersionTag) + 8 * s;
        double pvH = 13 * s;
        double pvX = cx0 + 16 * s + prevBrandW + 4 * s;
        double pvY = cy0 + 36 * s - pvH + 1 * s;
        fillRoundRect(g, pvX, pvY, pvW, pvH, 4 * s, new Color(255, 255, 255, 24), null, 0);
        g.setColor(new Color(255, 255, 255, 162));
        g.drawString(appVersionTag, (int) (pvX + 4 * s), (int) (pvY + 9.8 * s));

        // Compact single-row header controls: All Off + Refresh + Edit + '+'
        double hcy = cy0 + 28 * s;
        double addSz = 24 * s;
        double addX0 = cx1 - 12 * s - addSz;
        fillRoundRect(g, addX0, hcy - addSz / 2.0, addSz, addSz, 7 * s, C_WHITE, null, 0);
        double addCx = addX0 + addSz / 2.0;
        g.setColor(C_TEAL_DARK);
        g.setStroke(new BasicStroke((float) (2.0 * s), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Double(addCx - 5 * s, hcy, addCx + 5 * s, hcy));
        g.draw(new Line2D.Double(addCx, hcy - 5 * s, addCx, hcy + 5 * s));

        double editW = 44 * s, editH = 24 * s;
        double editX = addX0 - 6 * s - editW;
        fillRoundRect(g, editX, hcy - editH / 2.0, editW, editH, 12 * s, new Color(255, 255, 255, 42), null, 0);
        g.setColor(C_WHITE);
        g.setFont(fUi(11 * s, Font.BOLD));
        g.drawString("Edit", (int) (editX + 11 * s), (int) (hcy + 4 * s));

        double refCx = editX - 18 * s;
        g.setColor(new Color(255, 255, 255, 42));
        g.fill(new Ellipse2D.Double(refCx - 12 * s, hcy - 12 * s, 24 * s, 24 * s));
        g.setColor(C_WHITE);
        g.setStroke(new BasicStroke((float) (1.8 * s), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Arc2D.Double(refCx - 5.5 * s, hcy - 5.5 * s, 11 * s, 11 * s, 35, 275, Arc2D.OPEN));
        Path2D refArrow = new Path2D.Double();
        refArrow.moveTo(refCx + 2.0 * s, hcy - 5.0 * s);
        refArrow.lineTo(refCx + 6.0 * s, hcy - 5.0 * s);
        refArrow.lineTo(refCx + 5.2 * s, hcy - 1.2 * s);
        g.draw(refArrow);

        double aoW = 68 * s, aoH = 24 * s;
        double aoX = refCx - 12 * s - 6 * s - aoW;
        fillRoundRect(g, aoX, hcy - aoH / 2.0, aoW, aoH, 12 * s, new Color(255, 255, 255, 58), null, 0);
        drawIecPowerSymbol(g, aoX + 12.5 * s, hcy, 3.8 * s, C_WHITE, 1.4 * s);
        g.setColor(C_WHITE);
        g.setFont(fUi(10.5 * s, Font.BOLD));
        g.drawString("All Off", (int) (aoX + 22.0 * s), (int) (hcy + 3.8 * s));

        int cyRow = cy0 + (int) (60 * s);
        cyRow = drawRoomGroupHeader(g, cx0 + (int) (10 * s), cyRow, cx1 - (int) (10 * s), "Living Room", 3, 3, "3/3 Active • Avg 12ms", true, false, 80, s * 0.78);
        cyRow = drawDeviceRow(g, cx0 + (int) (16 * s), cyRow, cx1 - (int) (10 * s), "Christmas Tree Living", "Tree Lights", true, true, 11, "192.168.1.101", null, false, false, "", "", s * 0.78);
        cyRow = drawDeviceRow(g, cx0 + (int) (16 * s), cyRow, cx1 - (int) (10 * s), "Living Room Floor Lamp", "Lightbulb", true, true, 14, "192.168.1.103", 80, false, false, "", "", s * 0.78);
        cyRow = drawDeviceRow(g, cx0 + (int) (16 * s), cyRow, cx1 - (int) (10 * s), "Bedroom Ceiling Fan", "Fan / Vent", false, true, 15, "192.168.1.114", null, false, false, "", "", s * 0.78);
        cyRow = drawDeviceRow(g, cx0 + (int) (16 * s), cyRow, cx1 - (int) (10 * s), "Lightbulb Bedroom", "Lightbulb", false, false, 0, "192.168.1.108", 100, false, false, "", "", s * 0.78);

        g.dispose();
        return img;
    }

    private static int drawChromeOsDesktopHeader(Graphics2D g, int w, double s, String windowSubtitle) {
        // 1. Sleek ChromeOS desktop window title bar (30dp)
        int titleBarH = (int) (30 * s);
        g.setColor(new Color(0x0B, 0x16, 0x28));
        g.fillRect(0, 0, w, titleBarH);

        // Mini teal icon badge + title on left
        double icSz = 16 * s;
        fillRoundRect(g, 14 * s, (titleBarH - icSz) / 2.0, icSz, icSz, 4.5 * s, C_TEAL_PRIMARY, C_TV_CYAN, 0.8 * s);
        drawIecPowerSymbol(g, 14 * s + icSz / 2.0, titleBarH / 2.0, 4.2 * s, C_WHITE, 1.4 * s);
        drawFittedString(g, "WeMi Switch Control — ChromeOS Desktop", 38 * s, 19.5 * s, 460 * s, 11.5 * s, Font.BOLD, new Color(0xE2, 0xE8, 0xF0));

        // Center-right LAN context badge
        g.setFont(fUi(10.0 * s, Font.BOLD));
        int subW = g.getFontMetrics().stringWidth(windowSubtitle) + (int) (18 * s);
        double subX = w - 118 * s - subW;
        fillRoundRect(g, subX, 6 * s, subW, 18 * s, 9 * s, new Color(0x0E, 0x74, 0x90, 120), new Color(0x22, 0xD3, 0xEE, 120), 0.9 * s);
        g.setColor(new Color(0xE0, 0xF7, 0xFA));
        g.drawString(windowSubtitle, (int) (subX + 9 * s), (int) (18.5 * s));

        // Minimize / Maximize / Close window controls on far right
        double ctrlY = titleBarH / 2.0;
        g.setColor(new Color(0x94, 0xA3, 0xB8));
        g.setStroke(new BasicStroke((float) (1.5 * s), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        // Minimize
        g.draw(new Line2D.Double(w - 90 * s, ctrlY + 2 * s, w - 80 * s, ctrlY + 2 * s));
        // Maximize
        g.draw(new Rectangle2D.Double(w - 60 * s, ctrlY - 4.5 * s, 9 * s, 9 * s));
        // Close
        g.draw(new Line2D.Double(w - 30 * s, ctrlY - 4.5 * s, w - 21 * s, ctrlY + 4.5 * s));
        g.draw(new Line2D.Double(w - 21 * s, ctrlY - 4.5 * s, w - 30 * s, ctrlY + 4.5 * s));

        // 2. Authentic single-row WeMi TopHeader right beneath title bar (48dp)
        int headerH = (int) (48 * s);
        int y0 = titleBarH;
        LinearGradientPaint lg = new LinearGradientPaint(
            new Point2D.Double(0, y0),
            new Point2D.Double(w * 0.9, y0 + headerH),
            new float[]{0f, 0.5f, 1f},
            new Color[]{C_CYAN_TOP, C_TEAL_PRIMARY, C_SAPPHIRE_BOT}
        );
        g.setPaint(lg);
        g.fillRect(0, y0, w, headerH);

        double cy = y0 + headerH / 2.0;
        g.setColor(C_WHITE);
        g.setFont(fBrand(27 * s, Font.BOLD));
        int brandX = (int) (18 * s);
        g.drawString("wemi", brandX, (int) (cy + 9 * s));
        int brandW = g.getFontMetrics().stringWidth("wemi");

        // Subtle low-contrast version pill beside "wemi"
        g.setFont(fUi(9.5 * s, Font.PLAIN));
        double verW = g.getFontMetrics().stringWidth(appVersionTag) + 9 * s;
        double verH = 14.5 * s;
        double verX = brandX + brandW + 5 * s;
        double verY = cy + 9 * s - verH + 1.5 * s;
        fillRoundRect(g, verX, verY, verW, verH, 5 * s, new Color(255, 255, 255, 24), null, 0);
        g.setColor(new Color(255, 255, 255, 162));
        g.drawString(appVersionTag, (int) (verX + 4.5 * s), (int) (verY + 10.6 * s));

        // Right-aligned single-row actions: All Off, Refresh, Edit, '+'
        double addSz = 28 * s;
        double ax0 = w - 18 * s - addSz;
        double ay0 = cy - addSz / 2.0;
        fillRoundRect(g, ax0, ay0, addSz, addSz, 8.5 * s, C_WHITE, null, 0);
        g.setColor(C_TEAL_DARK);
        g.setStroke(new BasicStroke((float) (2.2 * s), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        double acx = ax0 + addSz / 2.0;
        double arm = 6.2 * s;
        g.draw(new Line2D.Double(acx - arm, cy, acx + arm, cy));
        g.draw(new Line2D.Double(acx, cy - arm, acx, cy + arm));

        double editW = 48 * s, editH = 27 * s;
        double ex0 = ax0 - 7 * s - editW;
        fillRoundRect(g, ex0, cy - editH / 2.0, editW, editH, 13.5 * s, new Color(255, 255, 255, 42), null, 0);
        g.setColor(C_WHITE);
        g.setFont(fUi(12.2 * s, Font.BOLD));
        int editTw = g.getFontMetrics().stringWidth("Edit");
        g.drawString("Edit", (int) (ex0 + (editW - editTw) / 2.0), (int) (cy + 4.5 * s));

        double btnR = 14 * s;
        double cxRef = ex0 - 7 * s - btnR;
        g.setColor(new Color(255, 255, 255, 42));
        g.fill(new Ellipse2D.Double(cxRef - btnR, cy - btnR, btnR * 2, btnR * 2));
        g.setColor(C_WHITE);
        g.setStroke(new BasicStroke((float) (1.9 * s), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        double ar = 6.5 * s;
        g.draw(new Arc2D.Double(cxRef - ar, cy - ar, ar * 2, ar * 2, 35, 285, Arc2D.OPEN));
        double ahx = cxRef + ar * Math.cos(Math.toRadians(35));
        double ahy = cy - ar * Math.sin(Math.toRadians(35));
        g.draw(new Line2D.Double(ahx, ahy, ahx - 3.4 * s, ahy - 0.8 * s));
        g.draw(new Line2D.Double(ahx, ahy, ahx + 0.6 * s, ahy - 3.6 * s));

        double allOffW = 72 * s, allOffH = 27 * s;
        double aox0 = cxRef - btnR - 7 * s - allOffW;
        fillRoundRect(g, aox0, cy - allOffH / 2.0, allOffW, allOffH, 13.5 * s, new Color(255, 255, 255, 58), null, 0);
        drawIecPowerSymbol(g, aox0 + 14 * s, cy, 4.3 * s, C_WHITE, 1.55 * s);
        g.setColor(C_WHITE);
        g.setFont(fUi(11.8 * s, Font.BOLD));
        g.drawString("All Off", (int) (aox0 + 23 * s), (int) (cy + 4.3 * s));

        return y0 + headerH;
    }

    private static void drawChromeOsBottomChrome(Graphics2D g, int w, int h, String selectedTab, double s) {
        int shelfH = (int) (34 * s);
        int navH = (int) (46 * s);
        int navY0 = h - shelfH - navH;

        // App bottom navigation bar sitting cleanly above ChromeOS shelf
        g.setColor(C_WHITE);
        g.fillRect(0, navY0, w, navH);
        g.setColor(C_TEAL_PRIMARY);
        g.fillRect(0, navY0, w, Math.max(2, (int) (2.0 * s)));

        String[] tabs = {"Devices", "Rules", "More"};
        int barSpan = (int) (420 * s);
        int startX = (w - barSpan) / 2;
        int colW = barSpan / 3;
        for (int i = 0; i < tabs.length; i++) {
            String tab = tabs[i];
            boolean active = tab.equals(selectedTab);
            double cx = startX + i * colW + colW / 2.0;
            double cyIcon = navY0 + 16 * s;
            Color tint = active ? C_TEAL_DARK : C_TEXT_SECONDARY;
            if (active) {
                fillRoundRect(g, cx - 26 * s, cyIcon - 10 * s, 52 * s, 20 * s, 10 * s, C_CYAN_SOFT, null, 0);
            }
            g.setColor(tint);
            g.setStroke(new BasicStroke((float) (1.8 * s), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            if ("Devices".equals(tab)) {
                fillRoundRect(g, cx - 7 * s, cyIcon - 6 * s, 10 * s, 12 * s, 3.5 * s, tint, null, 0);
                g.draw(new Line2D.Double(cx - 12 * s, cyIcon, cx - 7 * s, cyIcon));
                g.draw(new Line2D.Double(cx + 3 * s, cyIcon - 3.5 * s, cx + 9 * s, cyIcon - 3.5 * s));
                g.draw(new Line2D.Double(cx + 3 * s, cyIcon + 3.5 * s, cx + 9 * s, cyIcon + 3.5 * s));
            } else if ("Rules".equals(tab)) {
                fillRoundRect(g, cx - 7.5 * s, cyIcon - 7.5 * s, 15 * s, 15 * s, 3 * s, null, tint, 1.8 * s);
                g.draw(new Line2D.Double(cx - 7.5 * s, cyIcon - 2.5 * s, cx + 7.5 * s, cyIcon - 2.5 * s));
            } else {
                for (int dx : new int[]{-7, 0, 7}) {
                    double dr = 2.2 * s;
                    g.fill(new Ellipse2D.Double(cx + dx * s - dr, cyIcon - dr, dr * 2, dr * 2));
                }
            }
            g.setFont(fUi(10.2 * s, active ? Font.BOLD : Font.PLAIN));
            int tw = g.getFontMetrics().stringWidth(tab);
            g.drawString(tab, (int) (cx - tw / 2.0), (int) (navY0 + 39 * s));
        }

        // ChromeOS Desktop Shelf
        int shelfY0 = h - shelfH;
        g.setColor(new Color(0x0B, 0x12, 0x20));
        g.fillRect(0, shelfY0, w, shelfH);
        g.setColor(new Color(255, 255, 255, 24));
        g.fillRect(0, shelfY0, w, 1);

        // Launcher button on left
        double lcx = 22 * s, lcy = shelfY0 + shelfH / 2.0;
        g.setColor(new Color(255, 255, 255, 34));
        g.fill(new Ellipse2D.Double(lcx - 10 * s, lcy - 10 * s, 20 * s, 20 * s));
        g.setColor(C_WHITE);
        g.setStroke(new BasicStroke((float) (1.6 * s)));
        g.draw(new Ellipse2D.Double(lcx - 5.5 * s, lcy - 5.5 * s, 11 * s, 11 * s));

        // Centered pinned ChromeOS shelf icons (WeMi active in middle)
        double centerShelfX = w / 2.0;
        double[] iconOffsets = {-44 * s, 0, 44 * s};
        Color[] iconCols = {new Color(0x33, 0x41, 0x55), C_TEAL_PRIMARY, new Color(0x33, 0x41, 0x55)};
        for (int i = 0; i < iconOffsets.length; i++) {
            double icx = centerShelfX + iconOffsets[i];
            double r = 10.5 * s;
            g.setColor(iconCols[i]);
            g.fill(new Ellipse2D.Double(icx - r, lcy - r - 1 * s, r * 2, r * 2));
            if (i == 1) {
                drawIecPowerSymbol(g, icx, lcy - 1 * s, 4.8 * s, C_WHITE, 1.5 * s);
                fillRoundRect(g, icx - 7 * s, h - 4 * s, 14 * s, 2.5 * s, 1.2 * s, C_TV_CYAN, null, 0);
            } else if (i == 0) {
                g.setColor(new Color(0x94, 0xA3, 0xB8));
                g.draw(new Ellipse2D.Double(icx - 4.5 * s, lcy - 5.5 * s, 9 * s, 9 * s));
            } else {
                fillRoundRect(g, icx - 4.5 * s, lcy - 5 * s, 9 * s, 8 * s, 2 * s, new Color(0x94, 0xA3, 0xB8), null, 0);
            }
        }

        // System tray pill on right
        double trayW = 104 * s, trayH = 22 * s;
        double trayX = w - 14 * s - trayW;
        double trayY = shelfY0 + (shelfH - trayH) / 2.0;
        fillRoundRect(g, trayX, trayY, trayW, trayH, 11 * s, new Color(255, 255, 255, 24), null, 0);
        g.setColor(C_WHITE);
        g.setStroke(new BasicStroke((float) (1.4 * s), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Arc2D.Double(trayX + 12 * s, trayY + 6 * s, 11 * s, 11 * s, 45, 90, Arc2D.OPEN));
        g.draw(new Arc2D.Double(trayX + 14.5 * s, trayY + 8.5 * s, 6 * s, 6 * s, 45, 90, Arc2D.OPEN));
        fillRoundRect(g, trayX + 34 * s, trayY + 7 * s, 14 * s, 8 * s, 1.8 * s, null, C_WHITE, 1.2 * s);
        fillRoundRect(g, trayX + 36 * s, trayY + 9 * s, 9 * s, 4 * s, 0.8 * s, C_WHITE, null, 0);
        g.setFont(fUi(10.5 * s, Font.BOLD));
        g.drawString("9:41", (int) (trayX + 60 * s), (int) (trayY + 15 * s));
    }

    private static int drawChromeOsWifiHomeBar(Graphics2D g, int x0, int y0, int x1, double s) {
        int h = (int) (60 * s);
        fillRoundRect(g, x0, y0, x1 - x0, h, 11 * s, C_WHITE, new Color(0x08, 0x91, 0xB2, 95), 1.2 * s);

        double ir = 13 * s;
        double icx = x0 + 12 * s + ir;
        double icy = y0 + h / 2.0;
        g.setColor(C_CYAN_SOFT);
        g.fill(new Ellipse2D.Double(icx - ir, icy - ir, ir * 2, ir * 2));
        g.setColor(C_TEAL_DARK);
        g.setStroke(new BasicStroke((float) (1.8 * s), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Arc2D.Double(icx - 7 * s, icy - 5 * s, 14 * s, 14 * s, 45, 90, Arc2D.OPEN));
        g.draw(new Arc2D.Double(icx - 3.5 * s, icy - 1.5 * s, 7 * s, 7 * s, 45, 90, Arc2D.OPEN));

        double bw = 88 * s;
        double bh = 28 * s;
        double bx0 = x1 - 12 * s - bw;
        double by0 = y0 + (h - bh) / 2.0;
        fillRoundRect(g, bx0, by0, bw, bh, 8 * s, C_TEAL_PRIMARY, null, 0);
        drawFittedString(g, "Reconnect", bx0 + 13 * s, by0 + 18 * s, bw - 20 * s, 11.0 * s, Font.BOLD, C_WHITE);

        // Inline home profile chips to the left of Reconnect
        String[][] chips = {{"Main Home (18)", "1"}, {"Lake Cabin (3)", "0"}, {"+ Add Home", "0"}};
        double ch = 24 * s;
        double totalChipsW = 0;
        for (String[] c : chips) {
            boolean sel = "1".equals(c[1]);
            g.setFont(fUi(10.2 * s, sel ? Font.BOLD : Font.PLAIN));
            totalChipsW += g.getFontMetrics().stringWidth(c[0]) + 16 * s + 6 * s;
        }
        double chipsStartX = bx0 - 12 * s - totalChipsW;
        double cx = chipsStartX;
        double cyChip = y0 + (h - ch) / 2.0;
        for (String[] c : chips) {
            boolean sel = "1".equals(c[1]);
            g.setFont(fUi(10.2 * s, sel ? Font.BOLD : Font.PLAIN));
            int cw = g.getFontMetrics().stringWidth(c[0]) + (int) (16 * s);
            fillRoundRect(g, cx, cyChip, cw, ch, 7 * s, sel ? C_TEAL_PRIMARY : C_WHITE, sel ? C_TEAL_PRIMARY : C_DIVIDER, 1.1 * s);
            g.setColor(sel ? C_WHITE : C_TEXT_PRIMARY);
            g.drawString(c[0], (int) (cx + 8 * s), (int) (cyChip + 16 * s));
            cx += cw + 6 * s;
        }

        double tx = icx + ir + 10 * s;
        double maxTextW = chipsStartX - tx - 12 * s;
        drawFittedString(g, "Main Home", tx, y0 + 24 * s, maxTextW, 14.5 * s, Font.BOLD, C_TEXT_PRIMARY);
        drawFittedString(g, "17/18 Active • 1 Offline • 192.168.86.0/24 • 15s ChromeOS LAN Sync", tx, y0 + 42 * s, maxTextW, 10.6 * s, Font.PLAIN, C_TEXT_SECONDARY);

        return y0 + h + (int) (6 * s);
    }

    private static BufferedImage renderChromebookDesktopDashboard(int w, int h) {
        double s = w / 1280.0;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = createG2(img);
        g.setColor(C_BG_GRAY);
        g.fillRect(0, 0, w, h);

        int y = drawChromeOsDesktopHeader(g, w, s, "Local LAN • 192.168.86.0/24") + (int) (8 * s);
        int pad = (int) (18 * s);
        y = drawChromeOsWifiHomeBar(g, pad, y, w - pad, s);
        y = drawRoomFilterBar(g, pad, y, w - pad, s, "All Rooms (9)") + (int) (2 * s);

        int gap = (int) (14 * s);
        int colW = (w - pad * 2 - gap * 2) / 3;
        int c1x0 = pad, c1x1 = pad + colW;
        int c2x0 = c1x1 + gap, c2x1 = c2x0 + colW;
        int c3x0 = c2x1 + gap, c3x1 = w - pad;
        double cs = s * 0.84;

        // Column 1: Living Room (expanded Floor Lamp dimmer & Pin Widget drawer) + Porch
        int y1 = y;
        y1 = drawRoomGroupHeader(g, c1x0, y1, c1x1, "Living Room", 3, 3, "3/3 Active • Avg 12ms", true, false, 80, cs);
        y1 = drawDeviceRow(g, c1x0 + (int) (6 * s), y1, c1x1, "Christmas Tree Living", "Tree Lights", true, true, 11, "192.168.86.101", null, false, false, "", "", cs);
        y1 = drawDeviceRow(g, c1x0 + (int) (6 * s), y1, c1x1, "Living Room Floor Lamp", "Lightbulb", true, true, 14, "192.168.86.103", 80, true, true, "231844K1200455", "WDS060 (Dimmer)", cs);
        y1 += (int) (4 * s);
        y1 = drawRoomGroupHeader(g, c1x0, y1, c1x1, "Porch", 1, 1, "1/1 Active • Avg 13ms", true, false, 100, cs);
        y1 = drawDeviceRow(g, c1x0 + (int) (6 * s), y1, c1x1, "Front Porch Switch", "Smart Switch", true, true, 13, "192.168.86.119", null, false, false, "", "", cs);

        // Column 2: Bedroom (all 3 visual states: ON, Standby OFF, Disconnected) + Kitchen
        int y2 = y;
        y2 = drawRoomGroupHeader(g, c2x0, y2, c2x1, "Bedroom", 1, 3, "2/3 Active • Avg 15ms", true, false, 100, cs);
        y2 = drawDeviceRow(g, c2x0 + (int) (6 * s), y2, c2x1, "Master Bath Ensuite Light", "Lightbulb", true, true, 12, "192.168.86.112", 65, false, false, "", "", cs);
        y2 = drawDeviceRow(g, c2x0 + (int) (6 * s), y2, c2x1, "Bedroom Ceiling Fan", "Fan / Vent", false, true, 15, "192.168.86.114", null, false, false, "", "", cs);
        y2 = drawDeviceRow(g, c2x0 + (int) (6 * s), y2, c2x1, "Lightbulb Bedroom", "Lightbulb", false, false, 0, "192.168.86.108", 100, false, false, "", "", cs);
        y2 += (int) (4 * s);
        y2 = drawRoomGroupHeader(g, c2x0, y2, c2x1, "Kitchen", 2, 2, "2/2 Active • Avg 11ms", true, false, 60, cs);
        y2 = drawDeviceRow(g, c2x0 + (int) (6 * s), y2, c2x1, "Christmas Tree Kitchen", "Tree Lights", true, true, 11, "192.168.86.104", null, false, false, "", "", cs);
        y2 = drawDeviceRow(g, c2x0 + (int) (6 * s), y2, c2x1, "Kitchen Under-Cabinet", "Lightbulb", true, true, 12, "192.168.86.107", 60, false, false, "", "", cs);

        // Column 3: Hallway & Landing (Master Room Dimmer) + Office
        int y3 = y;
        y3 = drawRoomGroupHeader(g, c3x0, y3, c3x1, "Hallway & Landing", 2, 2, "2/2 Active • Avg 12ms", true, true, 75, cs);
        y3 = drawDeviceRow(g, c3x0 + (int) (6 * s), y3, c3x1, "Lightbulb Landing", "Lightbulb", true, true, 12, "192.168.86.105", 75, false, false, "", "", cs);
        y3 = drawDeviceRow(g, c3x0 + (int) (6 * s), y3, c3x1, "Stairway Wall Sconce", "Lightbulb", true, true, 13, "192.168.86.106", 50, false, false, "", "", cs);
        y3 += (int) (4 * s);
        y3 = drawRoomGroupHeader(g, c3x0, y3, c3x1, "Office & Study", 2, 2, "2/2 Active • Avg 11ms", true, false, 100, cs);
        y3 = drawDeviceRow(g, c3x0 + (int) (6 * s), y3, c3x1, "Desk Lamp Switch", "Lightbulb", true, true, 10, "192.168.86.121", 90, false, false, "", "", cs);
        y3 = drawDeviceRow(g, c3x0 + (int) (6 * s), y3, c3x1, "Hallway 3-Way Switch", "Smart Switch", true, true, 12, "192.168.86.118", null, false, false, "", "", cs);

        drawChromeOsBottomChrome(g, w, h, "Devices", s);
        g.dispose();
        return img;
    }

    private static BufferedImage renderChromebookSsdpScanner(int w, int h) {
        double s = w / 1280.0;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = createG2(img);
        g.setColor(C_BG_GRAY);
        g.fillRect(0, 0, w, h);

        int y0 = drawChromeOsDesktopHeader(g, w, s, "ChromeOS LAN • Guided /24 Subnet Discovery") + (int) (12 * s);
        int pad = (int) (20 * s);
        int gap = (int) (16 * s);
        int leftW = (int) ((w - pad * 2 - gap) * 0.58);
        int lx0 = pad, lx1 = pad + leftW;
        int rx0 = lx1 + gap, rx1 = w - pad;
        int paneH = h - (int) (80 * s) - y0 - (int) (10 * s);

        // LEFT PANE: SSDP Network Discovery Scan with ChromeOS ArcVmManualSubnetFallbackCard
        fillRoundRect(g, lx0, y0, lx1 - lx0, paneH, 14 * s, C_WHITE, C_TEAL_PRIMARY, 1.5 * s);
        int px = lx0 + (int) (18 * s);
        double leftInnerW = (lx1 - lx0) - 36 * s;
        int y = y0 + (int) (28 * s);

        drawFittedString(g, "SSDP Network Discovery Scan", px, y, leftInnerW, 18.0 * s, Font.BOLD, C_TEXT_PRIMARY);
        drawFittedString(g, "Multicast M-SEARCH • 239.255.255.250:1900 • Automatic Physical Subnet Fallback", px, y + 18 * s, leftInnerW, 11.2 * s, Font.PLAIN, C_TEXT_SECONDARY);
        y += (int) (34 * s);

        // Search target pills + interface pill
        double cx = px;
        String[][] targets = {{"Belkin BasicEvent", "1"}, {"All Smart Switches", "0"}, {"UPnP Root", "0"}};
        for (String[] t : targets) {
            boolean sel = "1".equals(t[1]);
            g.setFont(fUi(10.5 * s, sel ? Font.BOLD : Font.PLAIN));
            int cw = g.getFontMetrics().stringWidth(t[0]) + (int) (18 * s);
            fillRoundRect(g, cx, y, cw, 25 * s, 7 * s, sel ? C_CYAN_SOFT : C_WHITE, sel ? C_TEAL_PRIMARY : C_DIVIDER, 1.1 * s);
            g.setColor(sel ? C_TEAL_DARK : C_TEXT_PRIMARY);
            g.drawString(t[0], (int) (cx + 9 * s), (int) (y + 16.5 * s));
            cx += cw + 6 * s;
        }
        fillRoundRect(g, lx1 - 18 * s - 220 * s, y, 220 * s, 25 * s, 7 * s, new Color(0xF1, 0xF5, 0xF9), C_DIVIDER, 1.0 * s);
        drawFittedString(g, "Interface: 100.115.92.6 • 42ms", lx1 - 18 * s - 210 * s, y + 16.5 * s, 200 * s, 10.2 * s, Font.BOLD, C_SAPPHIRE_BOT);
        y += (int) (34 * s);

        // Authentic ChromeOS Virtual Network Manual Subnet Fallback Card (ArcVmManualSubnetFallbackCard)
        int fbH = (int) (122 * s);
        fillRoundRect(g, px, y, leftInnerW, fbH, 12 * s, new Color(0xF0, 0xF9, 0xFF), new Color(0x08, 0x91, 0xB2, 130), 1.3 * s);
        drawFittedString(g, "ChromeOS Virtual Network Detected (100.115.92.6)", px + 14 * s, y + 22 * s, leftInnerW - 28 * s, 13.0 * s, Font.BOLD, C_TEAL_DARK);
        drawFittedString(g, "ChromeOS isolates Android apps on a virtual subnet. If your router blocks multicast discovery,", px + 14 * s, y + 40 * s, leftInnerW - 28 * s, 10.8 * s, Font.PLAIN, C_TEXT_SECONDARY);
        drawFittedString(g, "enter your home router IPv4 prefix (such as 192.168.1 or 192.168.86) to scan for local smart switches.", px + 14 * s, y + 55 * s, leftInnerW - 28 * s, 10.8 * s, Font.PLAIN, C_TEXT_SECONDARY);

        // OutlinedTextField + >=48dp Scan Subnet Button
        double scanBtnW = 118 * s;
        double scanBtnH = 38 * s;
        double scanBtnX = px + leftInnerW - 14 * s - scanBtnW;
        double fieldY = y + 70 * s;
        double fieldW = scanBtnX - (px + 14 * s) - 10 * s;
        fillRoundRect(g, px + 14 * s, fieldY, fieldW, scanBtnH, 8 * s, C_WHITE, C_TEAL_PRIMARY, 1.3 * s);
        drawFittedString(g, "Home Subnet (e.g. 192.168.1)", px + 24 * s, fieldY + 12 * s, fieldW - 20 * s, 8.8 * s, Font.BOLD, C_TEAL_DARK);
        drawFittedString(g, "192.168.86", px + 24 * s, fieldY + 29 * s, fieldW - 20 * s, 13.0 * s, Font.BOLD, C_TEXT_PRIMARY);

        fillRoundRect(g, scanBtnX, fieldY, scanBtnW, scanBtnH, 9 * s, C_TEAL_PRIMARY, null, 0);
        g.setFont(fUi(12.2 * s, Font.BOLD));
        int scanTw = g.getFontMetrics().stringWidth("Scan Subnet");
        drawFittedString(g, "Scan Subnet", scanBtnX + (scanBtnW - scanTw) / 2.0, fieldY + 23.5 * s, scanBtnW - 16 * s, 12.2 * s, Font.BOLD, C_WHITE);
        y += fbH + (int) (14 * s);

        // Discovered Devices header + Rescan + Adopt All
        double btnAdoptAllW = 86 * s;
        double btnRescanW = 74 * s;
        double btnRescanX = px + leftInnerW - btnAdoptAllW - 8 * s - btnRescanW;
        drawFittedString(g, "Discovered Switches on 192.168.86.0/24 (4)", px, y + 18 * s, btnRescanX - px - 10 * s, 14.5 * s, Font.BOLD, C_TEXT_PRIMARY);
        fillRoundRect(g, btnRescanX, y, btnRescanW, 27 * s, 7 * s, C_WHITE, C_TEAL_PRIMARY, 1.1 * s);
        drawFittedString(g, "Rescan", btnRescanX + 15 * s, y + 17.5 * s, btnRescanW - 20 * s, 11.2 * s, Font.BOLD, C_TEAL_DARK);
        fillRoundRect(g, px + leftInnerW - btnAdoptAllW, y, btnAdoptAllW, 27 * s, 7 * s, C_TEAL_PRIMARY, null, 0);
        drawFittedString(g, "Adopt All", px + leftInnerW - btnAdoptAllW + 15 * s, y + 17.5 * s, btnAdoptAllW - 20 * s, 11.2 * s, Font.BOLD, C_WHITE);
        y += (int) (35 * s);

        Object[][] found = {
            {"Christmas Tree Living", "Tree Lights", true, "192.168.86.101:49153 • F7C063", "SN: 221517K01018AA • 11ms • /setup.xml", true},
            {"Living Room Floor Lamp", "Lightbulb", true, "192.168.86.103:49153 • WDS060", "SN: 231844K12004BB • 14ms • /setup.xml", true},
            {"Master Bath Ensuite Light", "Lightbulb", true, "192.168.86.112:49153 • F7C059", "SN: 211902K09011CC • 12ms • Room: Ensuite", true},
            {"Hallway 3-Way Switch", "Smart Switch", false, "192.168.86.118:49154 • WLS0403", "SN: 240199K33082DD • 16ms • Port Healed", false}
        };
        for (Object[] it : found) {
            int rh = (int) (58 * s);
            boolean synced = (Boolean) it[5];
            fillRoundRect(g, px, y, leftInnerW, rh, 10 * s, synced ? new Color(0xF8, 0xFA, 0xFC) : new Color(0xEB, 0xFA, 0xFC), synced ? C_DIVIDER : C_TEAL_PRIMARY, 1.1 * s);
            drawCategoryIllustration(g, px + 8 * s, y + 8 * s, 38 * s, 40 * s, (String) it[1], (Boolean) it[2], true, s * 0.92);

            double bw = 72 * s, bh = 26 * s;
            double bx0 = px + leftInnerW - 10 * s - bw;
            double by0 = y + (rh - bh) / 2.0;
            double tx = px + 54 * s;
            double maxRowW = bx0 - tx - 10 * s;
            drawFittedString(g, (String) it[0], tx, y + 19 * s, maxRowW, 13.2 * s, Font.BOLD, C_TEXT_PRIMARY);
            drawFittedString(g, (String) it[3], tx, y + 35 * s, maxRowW, 10.2 * s, Font.BOLD, C_TEAL_DARK);
            drawFittedString(g, (String) it[4], tx, y + 49 * s, maxRowW, 9.4 * s, Font.PLAIN, C_TEXT_SECONDARY);

            fillRoundRect(g, bx0, by0, bw, bh, 7 * s, synced ? C_WHITE : C_TEAL_PRIMARY, synced ? C_TEAL_DARK : null, 1.0 * s);
            drawFittedString(g, synced ? "Adopted" : "Adopt", bx0 + (synced ? 12 : 18) * s, by0 + 17 * s, bw - 14 * s, 10.8 * s, Font.BOLD, synced ? C_TEAL_DARK : C_WHITE);
            y += rh + (int) (7 * s);
        }

        // RIGHT PANE: ChromeOS Network Diagnostics + Clean Home & Live SOAP Sync
        double rightInnerW = (rx1 - rx0) - 32 * s;
        int topCardH = (int) (274 * s);
        fillRoundRect(g, rx0, y0, rx1 - rx0, topCardH, 14 * s, new Color(0x12, 0x16, 0x1F), new Color(0x2E, 0x38, 0x4D), 1.2 * s);
        drawFittedString(g, "CHROMEOS LAN DIAGNOSTICS", rx0 + 16 * s, y0 + 26 * s, rightInnerW - 110 * s, 11.2 * s, Font.BOLD, new Color(0x8B, 0xE8, 0x36));
        fillRoundRect(g, rx1 - 16 * s - 94 * s, y0 + 11 * s, 94 * s, 21 * s, 10 * s, new Color(0x1E, 0x29, 0x3B), new Color(0x47, 0x55, 0x69), 1.0 * s);
        drawFittedString(g, "Save/Share Log", rx1 - 16 * s - 86 * s, y0 + 25 * s, 78 * s, 9.5 * s, Font.BOLD, new Color(0xE2, 0xE8, 0xF0));

        String[][] diagLines = {
            {"[NET] ip=100.115.92.6 boundWifi=true arcVmNat=true", "ok"},
            {"[DNS] dns=192.168.86.1 (RFC 1918 physical router hint)", "ok"},
            {"[SSDP] MulticastSocket bound -> eth1 (239.255.255.250)", "norm"},
            {"[STAGE C] Unicast sweep 192.168.86.0/24 -> 4 switches", "ok"},
            {"[HOME] Physical subnet saved -> 192.168.86.0/24", "norm"},
            {"[HOME] Virtual 100.115.92.x filtered from Home profiles", "norm"},
            {"[GENA] SUBSCRIBE skipped on CGNAT guest IP (100.115.92.6)", "warn"},
            {"[POLL] Active 15s SOAP state sync -> 4/4 healthy (12ms)", "ok"}
        };
        int dy = y0 + (int) (54 * s);
        for (String[] dl : diagLines) {
            Color c = "ok".equals(dl[1])
                ? new Color(0x86, 0xEF, 0xAC)
                : ("warn".equals(dl[1]) ? new Color(0xFB, 0xBF, 0x24) : new Color(0xE2, 0xE8, 0xF0));
            drawFittedString(g, dl[0], rx0 + 16 * s, dy, rightInnerW, 10.2 * s, Font.PLAIN, c);
            dy += (int) (26 * s);
        }

        // Bottom-right card: Clean Wi-Fi Home & Live SOAP Sync Log
        int botY = y0 + topCardH + (int) (14 * s);
        int botH = (y0 + paneH) - botY;
        fillRoundRect(g, rx0, botY, rx1 - rx0, botH, 14 * s, C_WHITE, C_DIVIDER, 1.1 * s);
        drawFittedString(g, "Live Local SOAP Sync & Privacy", rx0 + 16 * s, botY + 26 * s, rightInnerW, 14.5 * s, Font.BOLD, C_TEXT_PRIMARY);
        drawFittedString(g, "Zero cloud servers • Zero extra permissions • Pure local LAN", rx0 + 16 * s, botY + 44 * s, rightInnerW, 10.6 * s, Font.BOLD, C_TEAL_DARK);

        String[][] soapRows = {
            {"GetBinaryState • Christmas Tree Living", "http://192.168.86.101:49153/upnp/control/basicevent1", "HTTP 200 OK (11ms) • 15s ChromeOS LAN Sync"},
            {"SetBinaryState (1, 80%) • Living Room Floor Lamp", "http://192.168.86.103:49153/upnp/control/basicevent1", "HTTP 200 OK (14ms) • Dimmer Updated"},
            {"Open Source MIT License • Local-Only Telemetry", "wemi-telemetry-latest.txt excluded from cloud Auto-Backup", "MIT License • Copyright (c) 2026 Michael R. Head"}
        };
        int sy = botY + (int) (58 * s);
        for (String[] sr : soapRows) {
            int sh = (int) (58 * s);
            fillRoundRect(g, rx0 + 14 * s, sy, rx1 - rx0 - 28 * s, sh, 9 * s, new Color(0xF3, 0xF6, 0xF8), null, 0);
            drawFittedString(g, sr[0], rx0 + 22 * s, sy + 18 * s, rightInnerW - 12 * s, 11.0 * s, Font.BOLD, C_TEXT_PRIMARY);
            drawFittedString(g, sr[1], rx0 + 22 * s, sy + 35 * s, rightInnerW - 12 * s, 9.8 * s, Font.BOLD, C_TEAL_DARK);
            drawFittedString(g, sr[2], rx0 + 22 * s, sy + 50 * s, rightInnerW - 12 * s, 9.6 * s, Font.PLAIN, C_TEXT_SECONDARY);
            sy += sh + (int) (8 * s);
        }

        drawChromeOsBottomChrome(g, w, h, "More", s);
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
        for (String chip : new String[]{"3-Col Room Grid", "Room Bar Dimming", "Movie 20% Scene", "Local LAN UPnP"}) {
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

        BufferedImage cb1 = renderChromebookDesktopDashboard(1920, 1080);
        saveImage(cb1, "play_store_assets/screenshots/chromebook/chromebook_1_desktop_dashboard.png", "PNG");
        saveImage(cb1, "play_store_assets/screenshots/tablet_10_inch/tablet10_3_chromebook_desktop.png", "PNG");
        BufferedImage cb2 = renderChromebookSsdpScanner(1920, 1080);
        saveImage(cb2, "play_store_assets/screenshots/chromebook/chromebook_2_ssdp_subnet_scanner.png", "PNG");
        saveImage(cb2, "play_store_assets/screenshots/tablet_10_inch/tablet10_4_chromebook_ssdp_scanner.png", "PNG");

        BufferedImage tv1 = renderTvScreen(1920, 1080, "focus");
        saveImage(tv1, "play_store_assets/screenshots/android_tv/tv_1_room_groups_and_remote_focus.png", "PNG");
        BufferedImage tv2 = renderTvScreen(1920, 1080, "dimmer");
        saveImage(tv2, "play_store_assets/screenshots/android_tv/tv_2_dimmer_and_ssdp_discovery.png", "PNG");
        BufferedImage tv3 = renderTvScreen(1920, 1080, "scenes");
        saveImage(tv3, "play_store_assets/screenshots/android_tv/tv_3_living_room_scenes_and_rules.png", "PNG");
    }
}
