package edu.dk.asj.dpm.ui.actions;

/**
 * Available UI actions when presenting the main menu.
 */
public enum MenuAction {
    ShowVault("Show vault"),
    SearchVault("Search for names"),
    AddVaultEntry("Add new entry"),
    SignOut("Sign out");

    private String name;

    MenuAction(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }
}
