package pt.inputrecorder;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

final class ClickGuiRenderer {
    final Text regular = new Text(10, Font.PLAIN);
    final Text small = new Text(8, Font.PLAIN);
    final Text title = new Text(15, Font.BOLD);

    static void color(int argb) {
        GlStateManager.color((argb >> 16 & 255) / 255f, (argb >> 8 & 255) / 255f,
                (argb & 255) / 255f, (argb >>> 24) / 255f);
    }

    static void rounded(float x, float y, float w, float h, float radius, int argb) {
        if (w <= 0 || h <= 0) return;
        radius = Math.min(radius, Math.min(w, h) / 2);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.disableTexture2D(); color(argb);
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        GL11.glVertex2f(x + w / 2, y + h / 2);
        for (int corner = 0; corner < 4; corner++) {
            float cx = corner < 2 ? x + w - radius : x + radius;
            float cy = corner == 0 || corner == 3 ? y + radius : y + h - radius;
            for (int step = 0; step <= 10; step++) {
                double angle = Math.toRadians(-90 + corner * 90 + step * 9);
                GL11.glVertex2f(cx + (float)Math.cos(angle) * radius, cy + (float)Math.sin(angle) * radius);
            }
        }
        GL11.glVertex2f(x + w - radius, y);
        GL11.glEnd(); GlStateManager.enableTexture2D(); GlStateManager.color(1, 1, 1, 1);
    }

    static void line(float x1, float y1, float x2, float y2, int argb) {
        GlStateManager.disableTexture2D(); GlStateManager.enableBlend(); color(argb);
        GL11.glLineWidth(1.5f); GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex2f(x1, y1); GL11.glVertex2f(x2, y2); GL11.glEnd(); GL11.glLineWidth(1);
        GlStateManager.enableTexture2D(); GlStateManager.color(1, 1, 1, 1);
    }

    void close() { regular.close(); small.close(); title.close(); }

    static void refreshIcon(float x, float y, int argb) {
        GlStateManager.disableTexture2D(); GlStateManager.enableBlend(); color(argb);
        GL11.glLineWidth(1.2f); GL11.glBegin(GL11.GL_LINE_STRIP);
        for (int i = 0; i <= 24; i++) {
            double angle = Math.toRadians(-35 + i * 12);
            GL11.glVertex2f(x + (float)Math.cos(angle) * 4, y + (float)Math.sin(angle) * 4);
        }
        GL11.glEnd(); GL11.glLineWidth(1);
        GlStateManager.enableTexture2D(); GlStateManager.color(1, 1, 1, 1);
        line(x + 3.3f, y - 2.3f, x + 3.3f, y - 6, argb);
        line(x + 3.3f, y - 2.3f, x - .4f, y - 2.3f, argb);
    }

    static final class Text {
        private static final int ATLAS = 512;
        private final ResourceLocation texture;
        private final int[] gx = new int[256], gy = new int[256], gw = new int[256];
        private final float[] advances = new float[256];
        private final int glyphHeight;
        Text(int size, int style) {
            BufferedImage atlas = new BufferedImage(ATLAS, ATLAS, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = atlas.createGraphics();
            graphics.setFont(new Font("SansSerif", style, size * 2));
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            graphics.setColor(java.awt.Color.WHITE);
            FontMetrics metrics = graphics.getFontMetrics(); glyphHeight = metrics.getHeight() + 4;
            int x = 0, y = 0;
            for (int code = 32; code < 256; code++) {
                String letter = String.valueOf((char)code);
                float advance = (float)metrics.getStringBounds(letter, graphics).getWidth();
                int cell = (int)Math.ceil(advance) + 4;
                if (x + cell > ATLAS) { x = 0; y += glyphHeight; }
                if (y + glyphHeight > ATLAS) throw new IllegalStateException("Font atlas overflow");
                gx[code] = x; gy[code] = y; gw[code] = cell; advances[code] = advance / 2;
                graphics.drawString(letter, x + 2, y + 2 + metrics.getAscent()); x += cell;
            }
            graphics.dispose();
            texture = Minecraft.getMinecraft().getTextureManager().getDynamicTextureLocation("inputrecorder_clickgui", new DynamicTexture(atlas));
            Minecraft.getMinecraft().getTextureManager().bindTexture(texture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        }

        float width(String value) {
            float total = 0; for (int i = 0; i < value.length(); i++) total += advances[code(value.charAt(i))]; return total;
        }

        String trim(String value, float limit) {
            if (width(value) <= limit) return value;
            int end = value.length();
            while (end > 0 && width(value.substring(0, end)) + width("...") > limit) end--;
            return limit < width("...") ? "" : value.substring(0, end) + "...";
        }

        void centered(String value, float center, float y, int argb) { draw(value, center - width(value) / 2, y, argb); }

        void draw(String value, float x, float y, int argb) {
            GlStateManager.enableTexture2D(); GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            Minecraft.getMinecraft().getTextureManager().bindTexture(texture); color(argb);
            GL11.glBegin(GL11.GL_QUADS);
            for (int i = 0; i < value.length(); i++) {
                int c = code(value.charAt(i));
                float left = x - 1, right = left + gw[c] / 2f, bottom = y + glyphHeight / 2f;
                float u = gx[c] / (float)ATLAS, v = gy[c] / (float)ATLAS;
                float u2 = (gx[c] + gw[c]) / (float)ATLAS, v2 = (gy[c] + glyphHeight) / (float)ATLAS;
                GL11.glTexCoord2f(u, v); GL11.glVertex2f(left, y);
                GL11.glTexCoord2f(u, v2); GL11.glVertex2f(left, bottom);
                GL11.glTexCoord2f(u2, v2); GL11.glVertex2f(right, bottom);
                GL11.glTexCoord2f(u2, v); GL11.glVertex2f(right, y); x += advances[c];
            }
            GL11.glEnd(); GlStateManager.color(1, 1, 1, 1);
        }

        private static int code(char value) { return value >= 32 && value < 256 ? value : '?'; }
        
        private void close() { Minecraft.getMinecraft().getTextureManager().deleteTexture(texture); }
    }
}
