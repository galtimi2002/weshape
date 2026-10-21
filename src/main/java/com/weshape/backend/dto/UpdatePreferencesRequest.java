package com.weshape.backend.dto;

import com.weshape.backend.model.ThemeMode;

/** Either field may be left out; only what is sent changes. */
public record UpdatePreferencesRequest(
        String preferredLanguage,
        ThemeMode themeMode
) {
}
