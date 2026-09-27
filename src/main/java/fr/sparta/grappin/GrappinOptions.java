package fr.sparta.grappin;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Options choisies a la creation (give) ou a la mise a jour (set) d'un grappin.
 * Une valeur null signifie "non precisee" : on garde celle du grappin, ou le defaut de config.yml.
 */
public record GrappinOptions(Boolean repairable, Boolean unlimited, Boolean noCooldown) {
    public static final GrappinOptions NONE = new GrappinOptions(null, null, null);

    public enum Kind { REPAIRABLE, UNLIMITED, COOLDOWN }

    private record Choice(Kind kind, boolean value) {}

    private static final Map<String, Choice> WORDS = Map.ofEntries(
        Map.entry("reparable", new Choice(Kind.REPAIRABLE, true)),
        Map.entry("réparable", new Choice(Kind.REPAIRABLE, true)),
        Map.entry("repairable", new Choice(Kind.REPAIRABLE, true)),
        Map.entry("irreparable", new Choice(Kind.REPAIRABLE, false)),
        Map.entry("irréparable", new Choice(Kind.REPAIRABLE, false)),
        Map.entry("unrepairable", new Choice(Kind.REPAIRABLE, false)),
        Map.entry("illimite", new Choice(Kind.UNLIMITED, true)),
        Map.entry("illimité", new Choice(Kind.UNLIMITED, true)),
        Map.entry("unlimited", new Choice(Kind.UNLIMITED, true)),
        Map.entry("limite", new Choice(Kind.UNLIMITED, false)),
        Map.entry("limité", new Choice(Kind.UNLIMITED, false)),
        Map.entry("limited", new Choice(Kind.UNLIMITED, false)),
        Map.entry("sans-cooldown", new Choice(Kind.COOLDOWN, true)),
        Map.entry("sanscooldown", new Choice(Kind.COOLDOWN, true)),
        Map.entry("nocooldown", new Choice(Kind.COOLDOWN, true)),
        Map.entry("no-cooldown", new Choice(Kind.COOLDOWN, true)),
        Map.entry("cooldown", new Choice(Kind.COOLDOWN, false)),
        Map.entry("avec-cooldown", new Choice(Kind.COOLDOWN, false))
    );

    /** Mots proposes a l'autocompletion, par categorie. */
    public static final Map<Kind, List<String>> SUGGESTIONS = Map.of(
        Kind.REPAIRABLE, List.of("reparable", "irreparable"),
        Kind.UNLIMITED, List.of("illimite", "limite"),
        Kind.COOLDOWN, List.of("sans-cooldown", "cooldown")
    );

    /** Categorie de l'option, ou null si le mot n'est pas une option. */
    public static Kind kindOf(String raw) {
        Choice choice = WORDS.get(raw.toLowerCase(Locale.ROOT));
        return choice == null ? null : choice.kind();
    }

    /** Copie avec l'option ajoutee, ou null si le mot n'est pas une option. */
    public GrappinOptions with(String raw) {
        Choice choice = WORDS.get(raw.toLowerCase(Locale.ROOT));
        if (choice == null) {
            return null;
        }
        return switch (choice.kind()) {
            case REPAIRABLE -> new GrappinOptions(choice.value(), this.unlimited, this.noCooldown);
            case UNLIMITED -> new GrappinOptions(this.repairable, choice.value(), this.noCooldown);
            case COOLDOWN -> new GrappinOptions(this.repairable, this.unlimited, choice.value());
        };
    }
}
