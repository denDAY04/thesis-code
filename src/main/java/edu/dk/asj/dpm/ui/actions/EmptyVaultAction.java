package edu.dk.asj.dpm.ui.actions;

public enum EmptyVaultAction {
    Back("Go back");

    private String name;

    EmptyVaultAction(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }
}
