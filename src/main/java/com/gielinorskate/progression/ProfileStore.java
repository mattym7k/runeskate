package com.gielinorskate.progression;

/**
* Per-account (RuneScape profile) settings. The plugin's one is the ConfigManager's RS profile configuration of
* the plugin's config group; tests use a map.
*/
public interface ProfileStore
{
/** The logged-in account's profile key, or null when there is none (logged out). */
String profile();

/** The value saved under {@code key} for {@code profile}, or null. */
String get(String profile, String key);

void set(String profile, String key, String value);
}
