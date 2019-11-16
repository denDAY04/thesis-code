package edu.dk.asj.dpm.ui;

import edu.dk.asj.dpm.Application;
import edu.dk.asj.dpm.security.SecurityController;
import org.beryx.textio.TextIO;
import org.beryx.textio.TextIoFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

public class UserInterface {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserInterface.class);

    private TextIO textUI;
    private Application application;

    public UserInterface(Application application) {
        Objects.requireNonNull(application);

        this.application = application;
        textUI = TextIoFactory.getTextIO();
    }

    public void run() {
        signIn();
        MenuAction action = getMenuAction();
        // TODO continue flow
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
            LOGGER.debug(message);
            textUI.getTextTerminal().println(message);
        } else {
            System.out.println(message);
        }
    }

    public String getPassword(String prompt) {
        return textUI.newStringInputReader().withInputMasking(true).read(prompt);
    }

    public boolean isFirstDevice() {
        String firstConfigPrompt = "Is this the first device you're configuring for your password manager?";
        return textUI.newBooleanInputReader().withFalseInput("n").withTrueInput("y").read(firstConfigPrompt);
    }

    public String getNetworkSeed() {
        String seedPrompt = "Input Network Seed (shown at first device configuration):";
        return textUI.newStringInputReader().read(seedPrompt);
    }

    private void signIn() {
        message(":: Actions ::" + "\n[1] Sign in" + "\n[x] Exit program");

        boolean authenticated;
        do {
            Character input = textUI.newCharInputReader()
                    .withPossibleValues('1', 'x')
                    .read();

            if (input == 'x') {
                application.exit();
            }

            String pwd = getPassword("Master password:");
            authenticated = application.getSecurityController().isMasterPassword(pwd);
            if (!authenticated) {
                error("Incorrect password. Try again.");
                // Enforce a short timeout on incorrect passwords to discourage automated oracle attacks
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    // do nothing
                }
            }
        } while (!authenticated);

        message("===============" + "\n    WELCOME    " + "\n===============");
    }

    private MenuAction getMenuAction() {
        String msg = ":: Menu ::" +
                "\n[1] Show vault" +
                "\n[2] Search vault" +
                "\n[3] Add entry into vault" +
                "\n[4] Sign out" +
                "\n[x] Exit";
        message(msg);

        Character action = textUI.newCharInputReader().withPossibleValues(MenuAction.getInputValues()).read();
        return MenuAction.parse(action);
    }

    private void signOut() {
        SecurityController.getInstance().clearMasterPassword();
        message("Signed out");
    }
}
