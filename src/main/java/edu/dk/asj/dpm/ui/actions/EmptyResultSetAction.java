package edu.dk.asj.dpm.ui.actions;

/**
 * Available UI actions when presenting empty result set.
 */
public enum EmptyResultSetAction {
    Back("Go back");

    private String name;

    EmptyResultSetAction(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }
}
