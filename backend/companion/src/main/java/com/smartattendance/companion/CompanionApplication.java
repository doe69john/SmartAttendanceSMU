package com.smartattendance.companion;

public final class CompanionApplication {

    public static void main(String[] args) {
        try {
            new DependencyBootstrap().launch(args);
        } catch (Exception ex) {
            ex.printStackTrace();
            System.exit(1);
        }
    }
}
