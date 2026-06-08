package zw.gov.mohcc.impilo.ndila.core.tiles;

import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Generates lightweight sovereign PNG map tiles for preview environments.
 * No external tile servers — deterministic colours and grid lines only.
 */
@Service
public class NdilaPreviewTileRasterService {

    private static final int TILE_SIZE = 256;

    public byte[] renderPng(int z, int x, int y) throws IOException {
        BufferedImage image = new BufferedImage(TILE_SIZE, TILE_SIZE, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int hash = stableHash(z, x, y);
        int baseR = 200 + (hash % 35);
        int baseG = 215 + ((hash / 7) % 25);
        int baseB = 225 + ((hash / 13) % 20);
        g.setColor(new Color(baseR, baseG, baseB));
        g.fillRect(0, 0, TILE_SIZE, TILE_SIZE);

        g.setColor(new Color(180, 195, 210));
        g.setStroke(new BasicStroke(1f));
        for (int i = 0; i <= TILE_SIZE; i += 32) {
            g.drawLine(i, 0, i, TILE_SIZE);
            g.drawLine(0, i, TILE_SIZE, i);
        }

        g.setColor(new Color(120, 140, 165));
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        g.drawString("Ndila " + z + "/" + x + "/" + y, 8, 18);

        g.dispose();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static int stableHash(int z, int x, int y) {
        int h = 17;
        h = 31 * h + z;
        h = 31 * h + x;
        h = 31 * h + y;
        return Math.abs(h);
    }
}
