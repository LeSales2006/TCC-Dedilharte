package com.example.dedilharte.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import com.example.dedilharte.auth.SessionManager;
import java.nio.charset.StandardCharsets;

/** Pins asynchronous reads and writes to the session that requested them. */
final class UserCacheScope {
    private final SessionManager session;
    private final String userId;
    private final String token;
    private final String role;

    UserCacheScope(Context context) {
        session = new SessionManager(context);
        userId = session.getUserId();
        token = session.getToken();
        role = session.getRole();
    }

    SharedPreferences preferences(Context context, String name) {
        String owner = Base64.encodeToString(userId.getBytes(StandardCharsets.UTF_8),
                Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
        // Unscoped legacy caches have no reliable owner or publication provenance.
        // Leave them intact, but never import them into an authenticated account.
        return context.getSharedPreferences(name + "_v2_" + owner, Context.MODE_PRIVATE);
    }

    boolean isCurrent() {
        return session.isLoggedIn() && userId.equals(session.getUserId())
                && token.equals(session.getToken()) && role.equals(session.getRole());
    }

    boolean isAdmin() {
        return isCurrent() && "admin".equals(role);
    }
}
