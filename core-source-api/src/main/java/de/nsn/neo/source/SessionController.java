package de.nsn.neo.source;

public interface SessionController {
    boolean requiresLogin();
    boolean isLoggedIn();
    void login(String email, char[] password, Callback<Boolean> callback);
    void logout(Callback<Boolean> callback);
}

