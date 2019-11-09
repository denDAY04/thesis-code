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

    /**
     * Determine equality between this vault entry and another object. Equality is ONLY true if both objects are
     * a vault entry, and they have equal names (not case sensitive).
     * @param obj other object to compare with.
     * @return true if the objects are equal, false otherwise.
     */
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

    /**
     * Compute (Java) hash code for this vault object, uniquely defined by its name. This hash value is not to be
     * confused with a cryptographically secure hash, and this should not be used in secure computations.
     * @return this entry's hash value.
     */
    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
}
