package com.example.luminae.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class SessionManager {

    private static final String PREF_NAME  = "LuminaSession";
    private static final String KEY_UID    = "user_id";
    private static final String KEY_NAME   = "username";
    private static final String KEY_EMAIL  = "email";
    private static final String KEY_ROLE   = "role";

    private final SharedPreferences prefs;
    private final SharedPreferences.Editor editor;

    public SessionManager(Context context) {
        prefs  = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        editor = prefs.edit();
    }

    /** Call after successful login */
    public void save(String userId, String username, String email, String role) {
        editor.putString(KEY_UID,   userId);
        editor.putString(KEY_NAME,  username);
        editor.putString(KEY_EMAIL, email);
        editor.putString(KEY_ROLE,  role);
        editor.apply();
    }

    public boolean isLoggedIn() { return prefs.getString(KEY_UID, null) != null; }
    public String getUserId()   { return prefs.getString(KEY_UID,   ""); }
    public String getUsername() { return prefs.getString(KEY_NAME,  ""); }
    public String getEmail()    { return prefs.getString(KEY_EMAIL, ""); }
    public String getRole()     { return prefs.getString(KEY_ROLE,  "student"); }
    public boolean isAdmin()    { String r = getRole(); return "admin".equals(r) || "admin-college".equals(r); }

    /** Call on logout */
    public void clear() { editor.clear(); editor.apply(); }
}
