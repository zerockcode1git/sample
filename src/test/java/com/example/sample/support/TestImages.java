package com.example.sample.support;

import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/** 테스트용 실제 이미지 (썸네일 생성 시 디코딩되므로 진짜 이미지여야 함) */
public final class TestImages {

    private TestImages() {
    }

    public static byte[] png(int width, int height) {
        return encode(width, height, "png", BufferedImage.TYPE_INT_ARGB);
    }

    public static byte[] jpeg(int width, int height) {
        return encode(width, height, "jpeg", BufferedImage.TYPE_INT_RGB);
    }

    /** images 파트용 PNG 파일 (기본 40x30) */
    public static MockMultipartFile pngPart(String filename) {
        return new MockMultipartFile("images", filename, MediaType.IMAGE_PNG_VALUE, png(40, 30));
    }

    private static byte[] encode(int width, int height, String format, int type) {
        BufferedImage img = new BufferedImage(width, height, type);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(220, 60, 60));
        g.fillRect(0, 0, width, height);
        g.setColor(Color.WHITE);
        g.fillOval(width / 4, height / 4, width / 2, height / 2);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(img, format, out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }
}
