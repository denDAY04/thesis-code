package edu.dk.asj.dpm.ui.actions;

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
