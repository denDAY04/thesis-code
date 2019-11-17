package edu.dk.asj.dpm.ui;

import edu.dk.asj.dpm.Application;
import edu.dk.asj.dpm.ui.actions.EmptyVaultAction;
import edu.dk.asj.dpm.ui.actions.MenuAction;
import edu.dk.asj.dpm.ui.actions.SignInAction;
import edu.dk.asj.dpm.ui.actions.VaultAction;
import edu.dk.asj.dpm.vault.VaultEntry;
import org.beryx.textio.TextIO;
import org.beryx.textio.TextIoFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

public class UserInterface {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserInterface.class);
    private static final String BLANK_SCREEN = "clean-bookmark";

    private TextIO textUI;
    private Application application;
    private boolean signedIn;

    public UserInterface(Application application) {
        Objects.requireNonNull(application);

        this.application = application;
        textUI = TextIoFactory.getTextIO();
        signedIn = false;

        message("\n===================================="
                + "\n    Distributed Password Manager    "
                + "\n====================================");
        textUI.getTextTerminal().setBookmark(BLANK_SCREEN);
    }

    @SuppressWarnings("InfiniteLoopStatement")
    public void run() {
        while (true) {
            if (!signedIn) {
                signIn();
            }

            MenuAction action = textUI.newEnumInputReader(MenuAction.class).read();
            switch (action){
                case ShowVault:
                    handleVaultEntries(application.getVault().getAll());
                    break;

                case SearchVault:
                    String query = textUI.newStringInputReader().read("Search entry names for:");
                    handleVaultEntries(application.getVault().search(query));
                    break;

                case AddVaultEntry:
                    addVaultEntry();
                    break;

                case SignOut:
                    signOut();
                    break;
            }
        }
    }

    /**
     * Display a non-fatal error in the UI.
     * @param errorMessage the error.
     */
    public void error(String errorMessage) {
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
     * Display a fatal error in the UI and close the application afterwards.
     * @param errorMessage the error.
     */
    public void fatal(String errorMessage) {
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
//        System.exit(-1);
        application.exit();
    }

    /**
     * Display a message in the UI.
     * @param message the message.
     */
    public void message(String message) {
        if (textUI != null) {
            textUI.getTextTerminal().println(message);
        } else {
            System.out.println(message);
        }
    }

    /**
     * Prompt user for their master password.
     * @param prompt the prompt text to display.
     * @return the user's input.
     */
    public String getPassword(String prompt) {
        return textUI.newStringInputReader().withInputMasking(true).read(prompt);
    }

    /**
     * Prompt the user to flag whether this is the first device being configured in the node network.
     * @return the user's input.
     */
    public boolean isFirstDevice() {
        String firstConfigPrompt = "Is this the first device you're configuring for your password manager?";
        return textUI.newBooleanInputReader().withFalseInput("n").withTrueInput("y").read(firstConfigPrompt);
    }

    /**
     * Prompt the user for the node network's seed.
     * @return the user's input
     */
    public String getNetworkSeed() {
        String seedPrompt = "Input Network Seed (shown at first device configuration):";
        return textUI.newStringInputReader().read(seedPrompt);
    }


    @SuppressWarnings("SwitchStatementWithTooFewBranches")
    private void handleVaultEntries(List<VaultEntry> entries) {
        clearScreen();

        message(":: Vault Entries ::");
        if (entries == null || entries.isEmpty()){
            message("No entries found");
            EmptyVaultAction action = textUI.newEnumInputReader(EmptyVaultAction.class).read();
            switch (action) {
                case Back:
                    clearScreen();
                    break;
            }
        } else {
            int i = 1;
            for (VaultEntry entry : entries) {
                message("[" + (i++) + "] " + entry);
            }

            VaultAction action = textUI.newEnumInputReader(VaultAction.class).read();
            switch (action) {
                case Delete:
                    Integer selection = textUI
                            .newIntInputReader()
                            .withMinVal(0)
                            .withMaxVal(entries.size())
                            .read("Delete entry number ([0] to abort and go back):");

                    clearScreen();
                    if (selection == 0) {
                        return;
                    }
                    boolean removed = application.getVault().remove(entries.get(selection - 1));
                    if (!removed) {
                        error("Could not delete entry");
                    } else {
                        application.notifyVaultChange();
                        message("Entry deleted");
                    }
                    break;

                case Back:
                    clearScreen();
                    break;
            }
        }
    }

    private void addVaultEntry() {
        clearScreen();
        message("Adding new entry ([x] to abort and go back)");

        String name = textUI.newStringInputReader().read("New entry name:");
        if (name.equals("x")) {
            clearScreen();
            return;
        }
        String pwd = textUI.newStringInputReader().read("New entry password:");
        if (pwd.equals("x")) {
            clearScreen();
            return;
        }

        clearScreen();
        boolean added = application.getVault().add(new VaultEntry(name, pwd));
        if (!added) {
            error("Could not add new entry to vault");
        } else {
            application.notifyVaultChange();
            message("Entry added");
        }
    }

    private void signIn() {
        clearScreen();

        boolean authenticated = false;
        do {
            SignInAction action = textUI.newEnumInputReader(SignInAction.class).read();
            switch (action) {

                case SignIn:
                    String pwd = getPassword("Master password:");
                    authenticated = application.getSecurityController().isMasterPassword(pwd);
                    clearScreen();
                    if (!authenticated) {
                        error("Incorrect password");
                        // Enforce a short timeout on incorrect passwords to discourage automated oracle attacks
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                            // do nothing
                        }
                    }
                    break;
                case Exit:
                    application.exit();
                    return;
            }
        } while (!authenticated);
        signedIn = true;
        application.constructVault();
    }

    private void signOut() {
        signedIn = false;
        application.clearVault();
        message("Signed out");
    }

    private void clearScreen() {
        textUI.getTextTerminal().resetToBookmark(BLANK_SCREEN);
    }
}
