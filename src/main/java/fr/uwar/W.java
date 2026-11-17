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
    FLAK("Missile a fragmentation (200 m)", "Frag 200 m", 40f, 11f, 2.2f, 26, 1, "flak_missile", false,
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
            "500 m : cratere. 0,5-2 km : carbonise + poison permanent. 2-5 km : souffle qui detruit tout. 5-10 km : arbres sans feuilles, animaux morts."),
    FLAK5("Missile a fragmentation (500 m)", "Frag 500 m", 46f, 14f, 2.8f, 30, 1, "flak500_missile", false,
            "Combat aerien longue distance : explose en l'air a 500 blocs ou a l'approche d'une cible, tres grand rayon."),
    LRM("Missile longue portee (900 m)", "Longue portee", 80f, 8f, 3.4f, 70, 1, "longrange_missile", true,
            "Vole jusqu'a 900 blocs en ligne droite (suit une cible verrouillee). Explose des qu'il touche un obstacle, en arrachant les blocs."),
    INCEN("Bombe incendiaire", "Incendiaire", 20f, 5f, 0f, 16, 1, "incendiary_bomb", false,
            "Met le feu sur 14 blocs : flammes sur le terrain, creatures enflammees, brasier qui dure."),
    ACID("Bombe a acide", "Acide", 10f, 4f, 0f, 16, 1, "acid_bomb", true,
            "Flaque d'acide qui ronge les blocs en profondeur pendant 10 s et empoisonne ceux qui y sont."),
    CLUSTER("Bombe a sous-munitions", "Sous-munitions", 40f, 5f, 0f, 16, 1, "cluster_bomb", false,
            "Disperse 24 petites bombes sur 22 blocs autour de l'impact."),
    THERMO("Bombe thermobarique", "Thermobarique", 120f, 18f, 0f, 24, 1, "thermobaric_bomb", true,
            "Aspire l'air (les creatures sont tirees vers le centre) puis explose : souffle enorme."),
    GAS("Bombe a gaz toxique", "Gaz", 0f, 0f, 0f, 20, 1, "gas_bomb", false,
            "Nuage toxique (rayon 12, 30 s) : poison, nausee, faiblesse."),
    SEISMIC("Bombe sismique", "Sismique", 0f, 0f, 0f, 24, 1, "seismic_bomb", true,
            "Tremblement de terre sur 40 blocs pendant 4 s : le sol se fissure, les creatures sont secouees."),
    LIGHT("Bombe foudre", "Foudre", 0f, 0f, 0f, 20, 1, "lightning_bomb", false,
            "Dechaine une tempete d'eclairs sur 30 blocs pendant 3 s, ils visent les creatures.");

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
        return this == MISSILE || this == IR || this == FLAK || this == CRUISE || this == BALLISTIC || this == BUSTER || this == FLAK5 || this == LRM;
    }

    public boolean bombLike() {
        return this == BOMB || this == BAY || this == NUKE || special();
    }

    /** Bombes speciales (effets dans Payload). */
    public boolean special() {
        return this == INCEN || this == ACID || this == CLUSTER || this == THERMO || this == GAS || this == SEISMIC || this == LIGHT;
    }

    public boolean flak() {
        return this == FLAK || this == FLAK5;
    }

    /** Portee d'explosion automatique (0 = aucune). */
    public double range() {
        return switch (this) {
            case FLAK -> 200;
            case FLAK5 -> 500;
            case LRM -> 900;
            default -> 0;
        };
    }

    /** Teinte du modele 3D (RGB). */
    public int tint() {
        return switch (this) {
            case INCEN -> 0xFF8A3C;
            case ACID -> 0x7CFF4A;
            case CLUSTER -> 0x8FA8D8;
            case THERMO -> 0xD04040;
            case GAS -> 0xD8E040;
            case SEISMIC -> 0xB48A58;
            case LIGHT -> 0x7AD8FF;
            case NUKE -> 0xFFE040;
            case FLAK5 -> 0xA0C0FF;
            case LRM -> 0xFFC080;
            default -> 0xFFFFFF;
        };
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
            case FLAK5 -> 1.5f;
            case LRM -> 2.1f;
            default -> 1.2f;
        };
    }
}
