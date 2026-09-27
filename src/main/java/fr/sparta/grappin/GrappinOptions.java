package fr.sparta.grappin;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Options choisies a la creation (give) ou a la mise a jour (set) d'un grappin.
 * Une valeur null signifie "non precisee" : on garde celle du grappin, ou le defaut de config.yml.
 *
 * @param cooldown   niveau de recharge : "court", "moyen" ou "long"
 * @param durability niveau de durabilite : "normale", "bonne" ou "excellente"
 */
public record GrappinOptions(Boolean repairable, Boolean unlimited, String cooldown, String durability) {
    public static final GrappinOptions NONE = new GrappinOptions(null, null, null, null);

    /** Niveaux de recharge, du plus court au plus long. Leurs durees sont dans config.yml. */
    public static final List<String> COOLDOWNS = List.of("court", "moyen", "long");
    /** Niveaux de durabilite, du plus faible au plus fort. Leurs valeurs sont dans config.yml. */
    public static final List<String> DURABILITIES = List.of("normale", "bonne", "excellente");

    public enum Kind { REPAIRABLE, UNLIMITED, COOLDOWN, DURABILITY }

    private record Choice(Kind kind, Object value) {}

    // Variantes avec et sans accent, et l'anglais.
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
        Map.entry("court", new Choice(Kind.COOLDOWN, "court")),
        Map.entry("cours", new Choice(Kind.COOLDOWN, "court")),
        Map.entry("short", new Choice(Kind.COOLDOWN, "court")),
        Map.entry("moyen", new Choice(Kind.COOLDOWN, "moyen")),
        Map.entry("medium", new Choice(Kind.COOLDOWN, "moyen")),
        Map.entry("long", new Choice(Kind.COOLDOWN, "long")),
        Map.entry("normale", new Choice(Kind.DURABILITY, "normale")),
        Map.entry("normal", new Choice(Kind.DURABILITY, "normale")),
        Map.entry("bonne", new Choice(Kind.DURABILITY, "bonne")),
        Map.entry("bon", new Choice(Kind.DURABILITY, "bonne")),
        Map.entry("good", new Choice(Kind.DURABILITY, "bonne")),
        Map.entry("excellente", new Choice(Kind.DURABILITY, "excellente")),
        Map.entry("excellent", new Choice(Kind.DURABILITY, "excellente"))
    );

    /** Mots proposes a l'autocompletion, par categorie. */
    public static final Map<Kind, List<String>> SUGGESTIONS = Map.of(
        Kind.REPAIRABLE, List.of("reparable", "irreparable"),
        Kind.UNLIMITED, List.of("illimite", "limite"),
        Kind.COOLDOWN, COOLDOWNS,
        Kind.DURABILITY, DURABILITIES
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
            case REPAIRABLE -> new GrappinOptions((Boolean) choice.value(), unlimited, cooldown, durability);
            case UNLIMITED -> new GrappinOptions(repairable, (Boolean) choice.value(), cooldown, durability);
            case COOLDOWN -> new GrappinOptions(repairable, unlimited, (String) choice.value(), durability);
            case DURABILITY -> new GrappinOptions(repairable, unlimited, cooldown, (String) choice.value());
        };
    }
}
