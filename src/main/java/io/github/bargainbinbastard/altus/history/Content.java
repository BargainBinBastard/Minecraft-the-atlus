package io.github.bargainbinbastard.altus.history;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Fixed content tables. Order matters: it drives the deterministic random streams. */
public final class Content {
    private Content() {}

    public static final List<Item> BLOCKS = items("block", new String[][] {
            {"deepslate", "Deepslate"}, {"amethyst", "Amethyst"}, {"kelp", "Kelp"}, {"wheat", "Wheat"},
            {"glass", "Glass"}, {"bone_block", "Bone"}, {"copper", "Copper"}, {"moss", "Moss"},
            {"obsidian", "Obsidian"}, {"coral", "Coral"}, {"honeycomb", "Honeycomb"}, {"snow", "Snow"},
            {"iron_block", "Iron"}, {"sculk", "Sculk"}, {"cobweb", "Cobwebs"}, {"candle", "Candles"},
            {"mud", "Mud"}, {"prismarine", "Prismarine"}});

    public static final List<Item> ENTITIES = items("entity", new String[][] {
            {"wolf", "Wolves"}, {"bee", "Bees"}, {"drowned", "the Drowned"}, {"enderman", "Endermen"},
            {"skeleton", "Skeletons"}, {"fox", "Foxes"}, {"axolotl", "Axolotls"}, {"spider", "Spiders"},
            {"phantom", "Phantoms"}, {"sheep", "Sheep"}, {"bat", "Bats"}, {"witch", "Witches"}});

    public static final List<Item> ACTIONS;
    static {
        String[][] a = {
                {"mine_ore", "tore ore from the deep places"}, {"fell_tree", "felled the old trees"},
                {"till_soil", "broke the earth for planting"}, {"dig_deep", "dug toward the floor of the world"},
                {"kill_passive", "slaughtered gentle beasts"}, {"kill_hostile", "hunted the things of the night"},
                {"breed_animals", "multiplied the herds"}, {"craft_tool", "forged new tools"},
                {"brew_potion", "brewed strange draughts"}, {"enchant_item", "bound words into iron"},
                {"burn_item", "fed offerings to the fire"}, {"light_fire", "set the land alight"},
                {"extinguish_fire", "drowned the sacred fires"}, {"sleep_in_nether", "slept in the burning places"},
                {"build_height", "built toward the sky"}};
        List<Item> l = new ArrayList<>();
        for (String[] r : a) l.add(new Item("action", r[0], r[0], r[1]));
        ACTIONS = Collections.unmodifiableList(l);
    }

    public static final List<String> EPITHETS = List.of("Warden", "Hunger", "Lantern", "Mother", "Father", "King",
            "Queen", "Witness", "Tongue", "Hand", "Saint", "Vessel", "Mouth", "Thorn", "Keeper", "Widow", "Weaver",
            "Shepherd", "Judge", "Heart", "Eye", "Smith", "Herald", "Beast");
    public static final List<String> G_ADJ = List.of("Smothering", "Silent", "Hollow", "Ashen", "Patient", "Gray",
            "Bitter", "Last", "Unlit", "Sunken", "Knotted", "Weeping");
    public static final List<String> G_NOUN = List.of("Tree", "Chalice", "Hand", "Choir", "Thorn", "Lantern", "Knot",
            "Veil", "Ledger", "Door", "Bell", "Root");
    public static final List<String> SECLUDE = List.of("to the summit of the Mountain",
            "into the house on the mountainside", "into the deepest Woods");
    public static final List<String> MOUNTAIN_PLACES = List.of("at the Mountain's summit",
            "in a cave on the Mountain's eastern face", "beneath a cairn on the Mountain's northern slope");
    public static final List<String> HOUSE_PLACES = List.of("behind the hearth of the House",
            "beneath the House's cellar stair", "in the House's locked attic");
    public static final List<String> WOODS_PLACES = List.of("at the hollow oak in the deep Woods",
            "by the black pool in the Woods", "under the fallen stones at the Woods' edge");

    public static final Map<String, String> TRAITS_DEF = new LinkedHashMap<>();
    static {
        TRAITS_DEF.put("secretive", "Hides all knowledge of itself. Its very existence is a secret.");
        TRAITS_DEF.put("obfuscator", "Works to keep mortals from correct knowledge of the Altus.");
        TRAITS_DEF.put("historian", "Never lies, and works to see every event recorded truly.");
        TRAITS_DEF.put("mad", "Sometimes acts against its own motives.");
        TRAITS_DEF.put("vengeful", "Holds grudges long and lets them grow.");
        TRAITS_DEF.put("forgiving", "Lets wrongs go.");
        TRAITS_DEF.put("proud", "Belittles the weak and inflates its own deeds.");
        TRAITS_DEF.put("ambitious", "Seeks power, taken from the weak if need be.");
        TRAITS_DEF.put("loyal", "Stands by its groups.");
        TRAITS_DEF.put("opportunist", "Follows power: changes groups and sides to stand with the strongest.");
        TRAITS_DEF.put("zealous", "Treats its dislikes as crimes, and punishes whoever commits them.");
        TRAITS_DEF.put("reclusive", "Withdraws from the others.");
        TRAITS_DEF.put("gregarious", "Seeks company and founds groups.");
        TRAITS_DEF.put("paranoid", "Suspects everyone.");
        TRAITS_DEF.put("protective", "Defends its friends and children.");
        TRAITS_DEF.put("devourer", "Hungers for the deaths of weaker gods.");
    }
    public static final List<String> TRAIT_NAMES = List.copyOf(TRAITS_DEF.keySet());
    public static final Set<String> RARE = Set.of("mad", "devourer");
    public static final String[][] EXCL = {{"historian", "obfuscator"}, {"historian", "secretive"},
            {"vengeful", "forgiving"}, {"loyal", "opportunist"}, {"reclusive", "gregarious"},
            {"protective", "devourer"}};

    /** Closing lines a lying teller may add. The teller uses the first of its own traits listed here. */
    public static final Map<String, String> TELLS = Map.of(
            "proud", "Mark it well; there is no doubt in it.",
            "secretive", "Ask no more of this.",
            "vengeful", "Remember who did this.",
            "opportunist", "It is as the strongest tell it.",
            "loyal", "I would swear to it beside my allies.",
            "forgiving", "Let it rest now.",
            "obfuscator", "Seek no further; there is nothing more to find.",
            "paranoid", "Trust no other account.",
            "zealous", "So it was, and so it is written.");

    public static final Map<String, String> ORIGIN_LABEL = Map.of("glory", "god-from-glory", "gods",
            "god-from-god", "corpse", "god-from-corpse", "nowhere", "god-from-nowhere");

    public static final Map<String, String> ACTION_NAMES = Map.ofEntries(
            Map.entry("mine_ore", "mining ore"), Map.entry("fell_tree", "felling trees"),
            Map.entry("till_soil", "tilling the soil"), Map.entry("dig_deep", "digging deep"),
            Map.entry("kill_passive", "killing gentle beasts"),
            Map.entry("kill_hostile", "hunting the creatures of the night"),
            Map.entry("breed_animals", "breeding the herds"), Map.entry("craft_tool", "forging tools"),
            Map.entry("brew_potion", "brewing potions"), Map.entry("enchant_item", "enchanting iron"),
            Map.entry("burn_item", "burning offerings"), Map.entry("light_fire", "kindling fires"),
            Map.entry("extinguish_fire", "quenching fires"),
            Map.entry("sleep_in_nether", "sleeping in the burning places"),
            Map.entry("build_height", "building toward the sky"));

    public static final Map<String, String> TRAIT_ADJ = Map.ofEntries(Map.entry("secretive", "secretive"),
            Map.entry("obfuscator", "a deceiver of mortals"), Map.entry("historian", "a keeper of the true record"),
            Map.entry("mad", "touched by madness"), Map.entry("vengeful", "vengeful"),
            Map.entry("forgiving", "forgiving"), Map.entry("proud", "proud"), Map.entry("ambitious", "ambitious"),
            Map.entry("loyal", "loyal"), Map.entry("opportunist", "an opportunist"), Map.entry("zealous", "zealous"),
            Map.entry("reclusive", "reclusive"), Map.entry("gregarious", "gregarious"),
            Map.entry("paranoid", "paranoid"), Map.entry("protective", "protective"),
            Map.entry("devourer", "a devourer of lesser gods"));

    private static List<Item> items(String cat, String[][] rows) {
        List<Item> l = new ArrayList<>();
        for (String[] r : rows) l.add(new Item(cat, r[0], r[1], null));
        return Collections.unmodifiableList(l);
    }
}
