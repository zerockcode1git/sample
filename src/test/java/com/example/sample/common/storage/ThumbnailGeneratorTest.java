package com.example.sample.common.storage;

import com.example.sample.support.TestImages;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ThumbnailGeneratorTest {

    private final ThumbnailGenerator generator = new ThumbnailGenerator();

    @Test
    void shrinkLandscapeKeepingRatio() throws IOException {
        BufferedImage thumb = thumbnail(TestImages.png(1200, 800), "a.png");
        assertThat(thumb.getWidth()).isEqualTo(300);
        assertThat(thumb.getHeight()).isEqualTo(200);
    }

    @Test
    void shrinkPortraitKeepingRatio() throws IOException {
        BufferedImage thumb = thumbnail(TestImages.jpeg(600, 1500), "b.jpg");
        assertThat(thumb.getWidth()).isEqualTo(120);
        assertThat(thumb.getHeight()).isEqualTo(300);
    }

    @Test
    void doNotEnlargeSmallImage() throws IOException {
        BufferedImage thumb = thumbnail(TestImages.png(40, 30), "small.png");
        assertThat(thumb.getWidth()).isEqualTo(40);
        assertThat(thumb.getHeight()).isEqualTo(30);
    }

    @Test
    void outputIsSmallJpeg() {
        byte[] original = TestImages.png(2000, 2000);
        byte[] thumb = generator.create(new MockMultipartFile("images", "big.png", "image/png", original));
        assertThat(thumb[0]).isEqualTo((byte) 0xFF);   // JPEG SOI
        assertThat(thumb[1]).isEqualTo((byte) 0xD8);
        assertThat(thumb.length).isLessThan(original.length);
    }

    @Test
    void rejectNonImageContent() {
        // Content-Type과 확장자는 이미지지만 내용은 이미지가 아님
        MockMultipartFile fake = new MockMultipartFile("images", "fake.png", "image/png", "not an image".getBytes());
        assertThatThrownBy(() -> generator.create(fake))
                .isInstanceOf(InvalidFileException.class)
                .hasMessageContaining("이미지를 읽을 수 없습니다");
    }

    @Test
    void thumbnailId() {
        assertThat(ThumbnailGenerator.thumbnailId("28d00fd0-c6ee-453f-8e6c-0584d806f8e7.png"))
                .isEqualTo("28d00fd0-c6ee-453f-8e6c-0584d806f8e7.jpg");
        assertThat(ThumbnailGenerator.thumbnailId("abc.webp")).isEqualTo("abc.jpg");
    }

    private BufferedImage thumbnail(byte[] data, String name) throws IOException {
        byte[] thumb = generator.create(new MockMultipartFile("images", name, "image/png", data));
        return ImageIO.read(new ByteArrayInputStream(thumb));
    }
}
