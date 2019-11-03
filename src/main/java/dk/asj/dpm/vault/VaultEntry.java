package dk.asj.dpm.vault;

import java.io.Serializable;

public class VaultEntry implements Serializable {
    private static final long serialVersionUID = -8754746222972229122L;

    private final String name;
    private final String password;

    public VaultEntry(String name, String password) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Name must not be null or blank");
        }
        if (password == null) {
            throw new IllegalArgumentException("Password must not be null");
        }

        this.name = name;
        this.password = password;
    }

    public String getName() {
        return name;
    }

    public String getPassword() {
        return password;
    }
}
