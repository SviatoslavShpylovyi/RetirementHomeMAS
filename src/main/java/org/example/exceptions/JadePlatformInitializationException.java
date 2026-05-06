package org.example.exceptions;

public class JadePlatformInitializationException extends RuntimeException {
    public JadePlatformInitializationException(final Throwable cause) {
        super("Could't initialize JADE platform.", cause);
    }
}