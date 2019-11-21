package edu.dk.asj.dpm.ui.actions;

/**
 * Available UI actions when presenting a result set.
 */
public enum ResultSetAction {
    Delete("Delete an entry"),
    Back("Go back");

    private String name;

    ResultSetAction(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }
}
