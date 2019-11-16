package edu.dk.asj.dpm;

import edu.dk.asj.dpm.network.NetworkController;
import edu.dk.asj.dpm.properties.NetworkProperties;
import edu.dk.asj.dpm.properties.PropertiesContainer;
import edu.dk.asj.dpm.security.SecurityController;
import edu.dk.asj.dpm.ui.UserInterface;
import edu.dk.asj.dpm.vault.SecureVault;
import edu.dk.asj.dpm.vault.VaultFragment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;

public class Application {
    private static final Logger LOGGER = LoggerFactory.getLogger(Application.class);

    private UserInterface ui;
    private SecureVault vault;
    private NetworkController networkController;
    private NetworkProperties networkProperties;
    private PropertiesContainer propertiesContainer;
    private SecurityController securityController;

    private Application() {
        ui = new UserInterface(this);
        securityController = SecurityController.getInstance();
    }

    public static void main(String[] args) {
        Application application = new Application();
        application.loadProperties();
        application.configure();

        application.ui.run();
    }

    public SecureVault getVault() {
        return vault;
    }

    public NetworkController getNetworkController() {
        return networkController;
    }

    public NetworkProperties getNetworkProperties() {
        return networkProperties;
    }

    public PropertiesContainer getPropertiesContainer() {
        return propertiesContainer;
    }

    public SecurityController getSecurityController() {
        return securityController;
    }

    /**
     * Close the application. This method does not return, due to invoking {@link System#exit(int)}.
     */
    public void exit() {
        networkController.close();
        System.exit(0);
    }

    private void loadProperties() {
        try {
            propertiesContainer = PropertiesContainer.loadProperties();
        } catch (IOException e) {
            ui.fatal("Failed to load application properties");
        }
    }

    private void configure() {
        ui.message("Loading configuration...");

        String path = propertiesContainer.getStorageProperties().getNetworkPropertiesPath();
        networkProperties = NetworkProperties.loadFromStorage(path);
        if (networkProperties != null) {
            ui.message("Configuration loaded");
            String pwd = ui.getPassword("Master Password:");
            securityController.setMasterPassword(pwd);

        } else {
            ui.error("Configuration not found");
            ui.message("Initiating new configuration");

            String pwd;
            boolean pwdConfirmed;
            do {
                pwd = ui.getPassword("New master password (must be the same for each future device):");
                String pwdConfirmation = ui.getPassword("Confirm new master password:");
                pwdConfirmed = pwd.equals(pwdConfirmation);
                if (!pwdConfirmed) {
                    ui.error("The two passwords do not match. Try again.");
                }
            } while (!pwdConfirmed);
            securityController.setMasterPassword(pwd);

            String networkIdSeed;
            String newSeedMessage = null;
            boolean firstConfig = ui.isFirstDevice();
            if (firstConfig) {
                networkIdSeed = Long.toString(System.currentTimeMillis(), 16);
                newSeedMessage = "\n\tRemember/write down your network seed:\t\t" + networkIdSeed;
            } else {
                networkIdSeed = ui.getNetworkSeed();
            }

            networkProperties = NetworkProperties.generate(pwd, networkIdSeed, path);
            if (networkProperties == null) {
                ui.fatal("The new configuration could not be completed");
            }
            networkController = new NetworkController(networkProperties, propertiesContainer);

            initialiseFragment();

            ui.message("Configuration complete");
            if (newSeedMessage != null) {
                ui.message(newSeedMessage);
            }

            networkController.startDiscoveryListener();
        }
    }

    private void initialiseFragment() {
        String networkError = "Encountered a network error while initialising the vault";

        try {
            // Get existing fragments from the network to construct existing vault, or initialize empty if no fragments
            int nodeCount = 1;
            VaultFragment[] networkFragments = networkController.getNetworkFragments();
            if (networkFragments == null || networkFragments.length < 1) {
                vault = SecureVault.builder().buildEmpty();
            } else {
                nodeCount = nodeCount + networkFragments.length;
                SecureVault.Builder builder = SecureVault.builder();
                Arrays.stream(networkFragments).forEach(builder::addFragment);
                vault = builder.build();
            }

            VaultFragment[] newFragments = vault.fragment(nodeCount);
            boolean saved = securityController.saveFragment(newFragments[0], propertiesContainer.getStorageProperties().getFragmentPath());
            if (!saved) {
                ui.fatal("Could not saved fragment");
            }

            // Send new fragments to the network
            if (nodeCount > 1) {
                VaultFragment[] newNetworkFragments = Arrays.copyOfRange(newFragments, 1, newFragments.length);
                if (!networkController.sendNetworkFragments(newNetworkFragments)) {
                    ui.fatal(networkError);
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            ui.fatal(networkError);
        }
    }
}
