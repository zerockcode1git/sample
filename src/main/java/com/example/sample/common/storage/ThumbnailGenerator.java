package com.example.sample.common.storage;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 목록 화면용 썸네일 생성. 원본 비율을 유지해 {@value #MAX_SIZE}px 상자 안에 맞추고(확대는 안 함) JPEG로 저장합니다.
 * 투명 배경은 흰색으로 채웁니다. 원본을 실제로 디코딩하므로 이미지가 아닌 파일을 걸러내는 검증도 겸합니다.
 */
@Component
public class ThumbnailGenerator {

    public static final int MAX_SIZE = 300;
    public static final String CONTENT_TYPE = "image/jpeg";
    private static final float JPEG_QUALITY = 0.85f;

    /** 원본 파일 ID({uuid}.{ext})에 대응하는 썸네일 파일 ID({uuid}.jpg) */
    public static String thumbnailId(String fileId) {
        int dot = fileId.lastIndexOf('.');
        return (dot < 0 ? fileId : fileId.substring(0, dot)) + ".jpg";
    }

    public byte[] create(MultipartFile file) {
        BufferedImage source;
        try (InputStream in = file.getInputStream()) {
            source = ImageIO.read(in);
        } catch (IOException e) {
            throw new InvalidFileException("이미지를 읽을 수 없습니다. 파일=" + file.getOriginalFilename());
        }
        if (source == null) {
            throw new InvalidFileException("이미지를 읽을 수 없습니다 (손상되었거나 지원하지 않는 형식). 파일=" + file.getOriginalFilename());
        }
        return toJpeg(resize(source));
    }

    private static BufferedImage resize(BufferedImage source) {
        double scale = Math.min(1.0, (double) MAX_SIZE / Math.max(source.getWidth(), source.getHeight()));
        int w = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int h = Math.max(1, (int) Math.round(source.getHeight() * scale));

        BufferedImage target = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = target.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, w, h);
            g.drawImage(source, 0, 0, w, h, null);
        } finally {
            g.dispose();
        }
        return target;
    }

    private static byte[] toJpeg(BufferedImage image) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(ios);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(JPEG_QUALITY);
            writer.write(null, new IIOImage(image, null, null), param);
        } catch (IOException e) {
            throw new IllegalStateException("썸네일 JPEG 생성 실패", e);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }
}
