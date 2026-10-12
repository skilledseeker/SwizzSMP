package com.swizzsmp.ability;

/**
 * What an ability activation did.
 * SUCCESS starts the cooldown now. ARMED means the handler will start it later
 * (next-hit abilities). FAIL starts nothing and optionally tells the player why.
 */
public record AbilityResult(Kind kind, String messageKey) {

    public enum Kind { SUCCESS, ARMED, FAIL }

    private static final AbilityResult SUCCESS = new AbilityResult(Kind.SUCCESS, null);
    private static final AbilityResult ARMED = new AbilityResult(Kind.ARMED, null);
    private static final AbilityResult SILENT_FAIL = new AbilityResult(Kind.FAIL, null);

    public static AbilityResult success() {
        return SUCCESS;
    }

    public static AbilityResult armed() {
        return ARMED;
    }

    public static AbilityResult fail(String messageKey) {
        return new AbilityResult(Kind.FAIL, messageKey);
    }

    public static AbilityResult silentFail() {
        return SILENT_FAIL;
    }
}
