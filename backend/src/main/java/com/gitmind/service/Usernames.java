package com.gitmind.service;
import java.util.Locale;
public final class Usernames {
    private Usernames() {}
    public static String normalize(String username) {
        if (username == null || !username.matches("^[a-zA-Z0-9](?:[a-zA-Z0-9]|-(?=[a-zA-Z0-9])){0,38}$"))
            throw new IllegalArgumentException("Invalid GitHub username format");
        return username.toLowerCase(Locale.ROOT);
    }
}
