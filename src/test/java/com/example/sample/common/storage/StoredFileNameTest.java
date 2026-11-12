package com.example.sample.common.storage;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StoredFileNameTest {

    private static final UUID ID = UUID.fromString("71dd496a-7935-425b-aa32-507952eb1d22");

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "front.png              | front.png",
            "back.PNG               | back.png",
            "my photo (1).jpg       | my_photo_1.jpg",
            "앞면 사진(1).jpg        | 1.jpg",
            "사진.png               | image.png",
            "C:\\Users\\me\\a-b.webp | a-b.webp",
            "dir/sub/x_y.gif        | x_y.gif",
            "..hidden.png           | hidden.png"
    })
    void storedFileName(String original, String expectedSuffix) {
        assertThat(SupabaseFileStorageService.storedFileName(ID, original)).isEqualTo(ID + "_" + expectedSuffix);
    }

    @org.junit.jupiter.api.Test
    void longNameIsTruncated() {
        String name = SupabaseFileStorageService.storedFileName(ID, "a".repeat(300) + ".png");
        assertThat(name).isEqualTo(ID + "_" + "a".repeat(100) + ".png");
    }
}
