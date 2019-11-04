package dk.asj.dpm;

import dk.asj.dpm.properties.PropertiesContainer;

import java.io.IOException;

public class UserInterface {

    private static PropertiesContainer properties;

    public static void main(String[] args) {
        System.out.println("Starting Distributed Password Manager...");

        if (!initProperties()) {
            System.out.println("Closing due to error.");
            return;
        }

    }

    private static boolean initProperties() {
        try {
            properties = PropertiesContainer.loadProperties();
            return true;
        } catch (IOException e) {
            System.err.println("Failed to load properties");
            return false;
        }
    }
}
