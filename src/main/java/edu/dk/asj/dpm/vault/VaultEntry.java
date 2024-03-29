package edu.dk.asj.dpm.vault;

import java.io.Serializable;
import java.util.Objects;

/**
 * A vault entry encapsulates the data stored in a secure vault as discrete objects.<p>
 * <p>
 * An entry is uniquely defined by its name.
 */
public class VaultEntry implements Serializable {
    private static final long serialVersionUID = -8754746222972229122L;

    private final String name;
    private final String password;

    /**
     * Create a discrete entry with all the data required.
     * @param name identifying name for the entry. Must not be null or blank (contain only whitespaces)
     * @param password the password for the entry. Must not be null.
     */
    public VaultEntry(String name, String password) throws IllegalArgumentException {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Name must not be null or blank");
        }
        this.name = name;
        this.password = Objects.requireNonNull(password, "Password must not be null");
    }

    /**
     * Get the entry's identifying name.
     * @return the name.
     */
    public String getName() {
        return name;
    }

    /**
     * Get the entry's password.
     * @return the password
     */
    public String getPassword() {
        return password;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }

        Class<?> objClass = obj.getClass();
        if (!this.getClass().equals(objClass)) {
            return false;
        }

        VaultEntry other = (VaultEntry) obj;
        return this.name.toLowerCase().equals(other.name.toLowerCase());
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public String toString() {
        return name + " / " + password;
    }
}
