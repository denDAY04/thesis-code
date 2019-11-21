package edu.dk.asj.dpm.ui.actions;

/**
 * Available UI actions when presenting sign-in.
 */
public enum SignInAction {
    SignIn("Sign in"),
    Exit("Exit application");

    private String name;

    SignInAction(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }
}
