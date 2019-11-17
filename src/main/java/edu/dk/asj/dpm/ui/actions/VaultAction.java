package edu.dk.asj.dpm.ui.actions;

public enum VaultAction {
    Delete("Delete an entry"),
    Back("Go back");

    private String name;

    VaultAction(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }
}
