package dk.asj.dpm.vault;

public class VaultEntry {
    private final String name;
    private final String password;

    public VaultEntry(String name, String password) {
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
