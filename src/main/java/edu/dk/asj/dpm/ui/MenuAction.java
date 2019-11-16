package edu.dk.asj.dpm.ui;

import java.util.Arrays;
import java.util.List;

public enum MenuAction {
    ShowVault,
    SearchVault,
    AddVaultEntry,
    SignOut,
    Exit;

    static MenuAction parse(char c) {
        switch (c) {
            case '1':
                return ShowVault;
            case '2':
                return SearchVault;
            case '3':
                return AddVaultEntry;
            case '4':
                return SignOut;
            case 'x':
                return Exit;
            default:
                throw new IllegalArgumentException("Unmapped user action input " + c);
        }
    }

    static List<Character> getInputValues() {
        return Arrays.asList('1', '2', '3', '4', 'x');
    }
}
