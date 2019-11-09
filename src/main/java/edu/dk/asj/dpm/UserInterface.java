package edu.dk.asj.dpm;

import edu.dk.asj.dpm.properties.NetworkProperties;
import edu.dk.asj.dpm.properties.PropertiesContainer;
import org.beryx.textio.TextIO;
import org.beryx.textio.TextIoFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class UserInterface {

    private static PropertiesContainer properties;
    private static NetworkProperties networkProperties;
    private static TextIO textUI;
    private static byte[] pwHash;

    public static void main(String[] args) {
        textUI = TextIoFactory.getTextIO();
        loadProperties();
        configureApplication();
    }

    private static void configureApplication() {
        message("Loading configuration...");

        String path = properties.getStorageProperties().getNetworkPropertiesPath();
        networkProperties = NetworkProperties.loadFromStorage(path);
        if (networkProperties == null) {
            message("Starting new configuration.");

            String password = textUI.newStringInputReader()
                    .withInputMasking(true)
                    .read("Input Master Password (must be the same for each device): ");

            MessageDigest hashFunction = SecurityScheme.getInstance().getHashFunction();
            hashFunction.update(password.getBytes(StandardCharsets.UTF_8));
            pwHash = hashFunction.digest();

            Boolean firstConfig = textUI.newBooleanInputReader()
                    .withFalseInput("n")
                    .withTrueInput("y")
                    .read("Is this the first device you're configuring? ");
            String seed;
            if (firstConfig) {
                seed = Long.toString(System.currentTimeMillis(), 16);
            } else {
                seed = textUI.newStringInputReader()
                        .read("Input Network Seed (shown at first device configuration): ");
            }


            networkProperties = NetworkProperties.generate(password, seed, path);
        }
        message("Configuration complete");
    }

    public static void error(String message) {
        if (textUI != null) {
            textUI.getTextTerminal().executeWithPropertiesConfigurator(
                    properties -> properties.setPromptColor("red"),
                    ui -> ui.println(message));
        }
         else {
            System.err.println(message);
        }
    }

    public static void message(String message) {
        if (textUI != null) {
            textUI.getTextTerminal().println(message);
        } else {
            System.out.println(message);
        }
    }

    private static void loadProperties() {
        try {
            properties = PropertiesContainer.loadProperties();
        } catch (IOException e) {
            error("Failed to load application properties");
            System.exit(-1);
        }
    }
}
