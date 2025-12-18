package com.fathzer.sync4j.test;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InputStream;

import org.junit.jupiter.api.Test;

import com.fathzer.sync4j.File;

class AbstractFileProviderTestTest {

    @Test
    void testCreateMockFile() throws IOException {
        File file = AbstractFileProviderTest.createMockFile("content");
        try (InputStream is = file.getInputStream()) {
            assertEquals("content", new String(is.readAllBytes()));
        }
        assertEquals("content".getBytes().length, file.getSize());

        // Can write the file more than once
        try (InputStream is = file.getInputStream()) {
            assertEquals("content", new String(is.readAllBytes()));
        }
    }

}
