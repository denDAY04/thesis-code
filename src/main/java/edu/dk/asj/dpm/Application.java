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
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collection;

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
        LOGGER.info("Starting application");

        Application application = new Application();
        application.loadProperties();
        application.configure();

        LOGGER.info("Application running");
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
        LOGGER.info("Exiting application");
        System.exit(0);
    }

    /**
     * Construct the secure vault
     */
    public void constructVault() {
        ui.message("Loading local data...");
        VaultFragment localFragment = securityController.loadFragment(propertiesContainer.getStorageProperties().getFragmentPath());
        try {
            ui.message("Loading node network data...");
            Collection<VaultFragment> networkFragments = networkController.getNetworkFragments();

            ui.message("Building vault...");
            SecureVault.Builder builder = SecureVault.builder();
            builder.addFragment(localFragment);
            networkFragments.forEach(builder::addFragment);
            vault = builder.build();
        } catch (IOException | ClassNotFoundException e) {
            LOGGER.error("Fetch vault fragments exception", e);
            ui.fatal("Encountered an error while retrieving the vault from node network");
        }

    }

    /**
     * Clear the in-game memory storing the vault
     */
    public void clearVault() {
        vault = null;
    }

    /**
     * Notify the network of changes to the vault. This means
     * <ol>
     *     <li>Fragment the new vault</li>
     *     <li>Save a local fragment</li>
     *     <li>Send rest of fragments to node network</li>
     * </ol>
     */
    public void notifyVaultChange() {
        int networkSize = networkController.getNetworkSize();
        String networkError = "Encountered a network error while notifying network of vault change";

        try {
            VaultFragment[] fragments = vault.fragment(networkSize);
            boolean saved = securityController.saveFragment(fragments[0], propertiesContainer.getStorageProperties().getFragmentPath());
            if (!saved) {
                ui.fatal("Could not save fragment");
            }
            if (networkSize > 1) {
                if (!networkController.sendNetworkFragments(Arrays.copyOfRange(fragments, 1, fragments.length))) {
                    ui.fatal(networkError);
                }
            }
        } catch (IOException e) {
            LOGGER.error("Notify network exception", e);
            ui.fatal(networkError);
        }
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
            boolean pwdVerified;
            String pwd;
            do {
                pwd = ui.getPassword("Input master password to start application:");

                BigInteger networkIdVerification = securityController.computeNetworkId(pwd, networkProperties.getNetworkIdSeed());
                pwdVerified = networkIdVerification.equals(networkProperties.getNetworkId());
                if (!pwdVerified) {
                    ui.error("Incorrect password");
                }
            } while (!pwdVerified);

            securityController.setMasterPassword(pwd);
            networkController = new NetworkController(networkProperties, propertiesContainer);
            ui.message("Configuration loaded");

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
                newSeedMessage = "Remember/write down your network seed:\t" + networkIdSeed;
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
        }
        networkController.startDiscoveryListener();
    }

    private void initialiseFragment() {
        String networkError = "Encountered a network error while initialising the vault";
        SecureVault temporaryVault;
        try {
            // Get existing fragments from the network to construct existing vault, or initialize empty if no fragments
            Collection<VaultFragment> networkFragments = networkController.getNetworkFragments();
            if (networkFragments.isEmpty()) {
                temporaryVault = SecureVault.builder().buildEmpty();
            } else {
                SecureVault.Builder builder = SecureVault.builder();
                networkFragments.forEach(builder::addFragment);
                temporaryVault = builder.build();
            }

            int nodeCount = networkController.getNetworkSize();
            VaultFragment[] newFragments = temporaryVault.fragment(nodeCount);
            boolean saved = securityController.saveFragment(newFragments[0], propertiesContainer.getStorageProperties().getFragmentPath());
            if (!saved) {
                ui.fatal("Could not save fragment");
            }

            // Send new fragments to the network
            if (nodeCount > 1) {
                VaultFragment[] newNetworkFragments = Arrays.copyOfRange(newFragments, 1, newFragments.length);
                if (!networkController.sendNetworkFragments(newNetworkFragments)) {
                    ui.fatal(networkError);
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            LOGGER.error("Initialize vault fragment exception", e);
            ui.fatal(networkError);
        }
    }
}
