package fr.uking;

public enum UType {
    PEASANT("peasant", 10, 2, 10, false, 0.25),
    SPEARMAN("spearman", 26, 6, 25, false, 0.29),
    ARCHER("archer", 18, 2, 30, true, 0.28),
    CROSSBOW("crossbow", 22, 3, 40, true, 0.27),
    KNIGHT("knight", 44, 9, 80, false, 0.28),
    LORD("lord", 90, 12, 0, false, 0.30);

    public final String id;
    public final int hp, dmg, cost;
    public final boolean ranged;
    public final double speed;

    UType(String id, int hp, int dmg, int cost, boolean ranged, double speed) {
        this.id = id; this.hp = hp; this.dmg = dmg; this.cost = cost; this.ranged = ranged; this.speed = speed;
    }

    public boolean fighter() { return this != PEASANT; }

    public static UType of(int i) { return values()[Math.max(0, Math.min(values().length - 1, i))]; }
}
