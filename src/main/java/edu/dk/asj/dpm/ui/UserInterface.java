package edu.dk.asj.dpm.ui;

import edu.dk.asj.dpm.network.NetworkController;
import edu.dk.asj.dpm.properties.NetworkProperties;
import edu.dk.asj.dpm.properties.PropertiesContainer;
import edu.dk.asj.dpm.security.SecurityController;
import edu.dk.asj.dpm.vault.SecureVault;
import edu.dk.asj.dpm.vault.VaultFragment;
import org.beryx.textio.TextIO;
import org.beryx.textio.TextIoFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;

public class UserInterface {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserInterface.class);

    private static NetworkProperties networkProperties;
    private static PropertiesContainer propertiesContainer;
    private static TextIO textUI;
    private static NetworkController network;
    private static SecureVault vault;

    public static void main(String[] args) {
        textUI = TextIoFactory.getTextIO();
        loadProperties();
        configureApplication();

        // TODO rest of activities incl. sign-in
    }

    /**
     * Display a non-fatal error in the UI.
     * @param errorMessage the error.
     */
    public static void error(String errorMessage) {
        if (textUI != null) {
            LOGGER.warn(errorMessage);
            textUI.getTextTerminal().executeWithPropertiesConfigurator(
                    properties -> properties.setPromptColor("red"),
                    ui -> ui.println(errorMessage));
        }
         else {
            System.err.println(errorMessage);
        }
    }

    /**
     * Display a fatal error in the UI and close the application afterwards. This method is not expected to return
     * due to calling {@link System#exit(int)}.
     * @param errorMessage the error.
     */
    public static void fatal(String errorMessage) {
        if (textUI != null) {
            LOGGER.error(errorMessage);
            textUI.getTextTerminal().executeWithPropertiesConfigurator(
                    properties -> properties.setPromptColor("red"),
                    ui -> {
                        ui.println(errorMessage);
                        ui.println("Exiting application due to fatal error");
                    });
        }
        else {
            System.err.println(errorMessage);
        }
        System.exit(-1);
    }

    /**
     * Display a message in the UI.
     * @param message the message.
     */
    public static void message(String message) {
        if (textUI != null) {
            LOGGER.info(message);
            textUI.getTextTerminal().println(message);
        } else {
            System.out.println(message);
        }
    }

    private static void loadProperties() {
        try {
            propertiesContainer = PropertiesContainer.loadProperties();
        } catch (IOException e) {
            fatal("Failed to load application properties");
        }
    }

    private static void configureApplication() {
        message("Loading configuration...");

        String path = propertiesContainer.getStorageProperties().getNetworkPropertiesPath();
        networkProperties = NetworkProperties.loadFromStorage(path);
        if (networkProperties != null) {
            message("Configuration loaded");
            String pwPrompt = "Input Master Password: ";
            String password = textUI.newStringInputReader()
                    .withInputMasking(true)
                    .read(pwPrompt);
            SecurityController.getInstance().setMasterPassword(password);

        } else {
            error("Configuration not found");
            message("Initiating new configuration");

            String pwPrompt = "Input Master Password (must be the same for each device): ";
            String password = textUI.newStringInputReader()
                    .withInputMasking(true)
                    .read(pwPrompt);
            SecurityController.getInstance().setMasterPassword(password);

            String firstConfigPrompt = "Is this the first device you're configuring? ";
            Boolean firstConfig = textUI.newBooleanInputReader()
                    .withFalseInput("n")
                    .withTrueInput("y")
                    .read(firstConfigPrompt);

            String networkIdSeed;
            String newSeedMessage = null;
            if (firstConfig) {
                networkIdSeed = Long.toString(System.currentTimeMillis(), 16);
                newSeedMessage = "\nRemember/write down your network seed:\t\t" + networkIdSeed;
            } else {
                String seedPrompt = "Input Network Seed (shown at first device configuration): ";
                networkIdSeed = textUI.newStringInputReader().read(seedPrompt);
            }

            networkProperties = NetworkProperties.generate(password, networkIdSeed, path);
            if (networkProperties == null) {
                fatal("The new configuration could not be completed");
            }
            network = new NetworkController(networkProperties, propertiesContainer);

            initialiseFragment();

            message("Configuration complete");
            if (newSeedMessage != null) {
                message(newSeedMessage);
            }

            network.startDiscoveryListener();
        }
    }

    private static void initialiseFragment() {
        String networkError = "Encountered a network error while initialising the vault";

        try {
            // Get existing fragments from the network to construct existing vault, or initialize empty if no fragments
            int nodeCount = 1;
            VaultFragment[] networkFragments = network.getNetworkFragments();
            if (networkFragments == null || networkFragments.length < 1) {
                vault = SecureVault.builder().buildEmpty();
            } else {
                nodeCount = nodeCount + networkFragments.length;
                SecureVault.Builder builder = SecureVault.builder();
                Arrays.stream(networkFragments).forEach(builder::addFragment);
                vault = builder.build();
            }

            VaultFragment[] newFragments = vault.fragment(nodeCount);
            boolean saved = SecurityController.getInstance().saveFragment(newFragments[0], propertiesContainer.getStorageProperties().getFragmentPath());
            if (!saved) {
                fatal("Could not saved fragment");
            }

            // Send new fragments to the network
            if (nodeCount > 1) {
                VaultFragment[] newNetworkFragments = Arrays.copyOfRange(newFragments, 1, newFragments.length);
                if (!network.sendNetworkFragments(newNetworkFragments)) {
                    fatal(networkError);
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            fatal(networkError);
        }
    }

    private static void signOut() {
        SecurityController.getInstance().clearMasterPassword();
        textUI.getTextTerminal().println("Signed out");
    }
}
