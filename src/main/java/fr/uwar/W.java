package fr.uwar;

/** Armes embarquees. L'ordre est important (indices sauvegardes). */
public enum W {
    CANNON("Canon 20 mm", "Canon 20mm", 5.5f, 0f, 0f, 2, 75, "cannon_ammo", false,
            "Tir en rafale, trace jaune. Arme interne : toujours disponible."),
    ROCKET("Roquettes", "Roquettes", 16f, 3.2f, 2.3f, 6, 6, "rocket_pod", false,
            "Salves de roquettes non guidees, explosion de zone."),
    MISSILE("Missile guide laser", "Missile laser", 34f, 4.2f, 2.0f, 20, 1, "air_missile", false,
            "Verrouille une cible dans le viseur (20 degres, 135 blocs) puis la poursuit."),
    BOMB("Bombe", "Bombe", 46f, 5.5f, 0f, 14, 1, "aerial_bomb", false,
            "Bombe lachee sous l'appareil. Degats de zone, ne casse pas les blocs."),
    IR("Missile infrarouge", "Missile IR", 30f, 3.5f, 2.4f, 24, 1, "ir_missile", false,
            "Tire et oublie : cone large (40 degres), cherche seul les sources de chaleur (moteurs d'avions en priorite)."),
    FLAK("Missile a fragmentation (200 m)", "Fragmentation", 40f, 11f, 2.2f, 26, 1, "flak_missile", false,
            "Combat aerien : explose en l'air a 200 blocs ou a l'approche d'une cible, grand rayon."),
    CRUISE("Missile de croisiere", "Croisiere", 70f, 7f, 1.7f, 60, 1, "cruise_missile", true,
            "Vole haut vers le point vise (170 blocs max) puis pique sur la cible. Explosion destructrice."),
    BALLISTIC("Missile balistique", "Balistique", 90f, 9f, 0f, 80, 1, "ballistic_missile", true,
            "Tir en cloche sur le point vise (170 blocs max). Explosion enorme, gros cratere."),
    BUSTER("Missile anti-bunker", "Anti-bunker", 55f, 7.5f, 2.1f, 40, 1, "buster_missile", true,
            "Enorme explosion qui arrache beaucoup de blocs la ou il touche."),
    BAY("Soute a bombes lourdes", "Soute lourde", 60f, 5.5f, 0f, 6, 1, "heavy_bomb", true,
            "Tapis de bombes : maintenir le tir pour larguer. Detruit les blocs."),
    NUKE("Bombe nucleaire", "NUCLEAIRE", 0f, 0f, 0f, 200, 1, "nuclear_bomb", true,
            "500 m : cratere. 0,5-2 km : carbonise + poison permanent. 2-5 km : souffle qui detruit tout. 5-10 km : arbres sans feuilles, animaux morts.");

    public final String label, shortName, desc, item;
    public final float dmg, radius, speed;
    public final int cooldown, perItem;
    /** Arme lourde : ses explosions detruisent les blocs (config heavyBlockDamage). */
    public final boolean heavy;

    W(String label, String shortName, float dmg, float radius, float speed, int cooldown, int perItem, String item, boolean heavy, String desc) {
        this.label = label;
        this.shortName = shortName;
        this.dmg = dmg;
        this.radius = radius;
        this.speed = speed;
        this.cooldown = cooldown;
        this.perItem = perItem;
        this.item = item;
        this.heavy = heavy;
        this.desc = desc;
    }

    public int bit() {
        return 1 << ordinal();
    }

    public boolean missileLike() {
        return this == MISSILE || this == IR || this == FLAK || this == CRUISE || this == BALLISTIC || this == BUSTER;
    }

    public boolean bombLike() {
        return this == BOMB || this == BAY || this == NUKE;
    }

    /** Vitesse du modele a l'ecran. */
    public float visScale() {
        return switch (this) {
            case CRUISE -> 1.9f;
            case BALLISTIC -> 2.4f;
            case BUSTER -> 1.5f;
            case BAY -> 1.6f;
            case NUKE -> 2.4f;
            case FLAK -> 1.25f;
            default -> 1.2f;
        };
    }
}
