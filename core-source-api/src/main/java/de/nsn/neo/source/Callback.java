package de.nsn.neo.source;

public interface Callback<T> {
    void onSuccess(T value);
    void onError(Throwable error);
}

