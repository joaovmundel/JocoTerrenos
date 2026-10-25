package io.github.joaovmundel.jocoTerrenos.exceptions;

/**
 * The persistent storage could not be read or written. Always carries the original cause.
 */
public class StorageException extends Exception {

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }

}
