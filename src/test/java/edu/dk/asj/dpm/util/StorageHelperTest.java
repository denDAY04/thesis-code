package edu.dk.asj.dpm.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class StorageHelperTest {

    @Test
    @DisplayName("Create and get path")
    void getPath() throws IOException {
        Path path = StorageHelper.getOrCreateStoragePath("unit-test/path");
        assertNotNull(path, "Path is null");

        // clean-up
        Files.delete(path);
    }
}