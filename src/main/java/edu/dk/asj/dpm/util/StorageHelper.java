package edu.dk.asj.dpm.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class StorageHelper {

    /**
     * Check if the storage path exists, create it if it does not, and return the path.
     * @param storagePath the storage path.
     * @return the path.
     * @throws IOException if an I/O error occurred.
     */
    public static Path getOrCreateStoragePath(String storagePath) throws IOException {
        Path path = Paths.get(storagePath);
        if (Files.notExists(path)) {
            Files.createDirectories(path.getParent());
            Files.createFile(path);
        }
        return path;
    }
}
