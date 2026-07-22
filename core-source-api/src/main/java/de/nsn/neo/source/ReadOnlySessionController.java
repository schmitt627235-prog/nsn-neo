package de.nsn.neo.source;

import de.nsn.neo.session.SourceSession;

/** Session facade used until a provider's existing login form is bound natively. */
public final class ReadOnlySessionController implements SessionController {
    private final SourceSession session;
    private final boolean requiresLogin;
    public ReadOnlySessionController(SourceSession session, boolean requiresLogin) {
        this.session = session; this.requiresLogin = requiresLogin;
    }
    @Override public boolean requiresLogin() { return requiresLogin; }
    @Override public boolean isLoggedIn() { return session.isLoggedIn(); }
    @Override public void login(String email, char[] password, Callback<Boolean> callback) {
        callback.onError(new UnsupportedOperationException("Login-Adapter noch nicht verbunden"));
    }
    @Override public void logout(Callback<Boolean> callback) { session.clear(); callback.onSuccess(true); }
}
