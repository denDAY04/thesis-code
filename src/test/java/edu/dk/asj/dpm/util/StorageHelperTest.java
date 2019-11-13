package edu.dk.asj.dpm.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class StorageHelperTest {

    @Test
    @DisplayName("Create and get path")
    void getPath() throws IOException {
        String storagePath = "unit-test/path";
        Path path = StorageHelper.getOrCreateStoragePath(storagePath);
        assertNotNull(path, "Path is null");

        // clean-up
        Files.delete(path);
        Files.delete(Paths.get(storagePath.split("/")[0]));
    }
}