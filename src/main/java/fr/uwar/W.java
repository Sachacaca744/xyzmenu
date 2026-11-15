package fr.uwar;

/** Armes embarquees. */
public enum W {
    CANNON("Canon 20 mm", 5.5f, 0f, 0f, 2, 75, "cannon_ammo"),
    ROCKET("Roquettes", 16f, 3.2f, 2.3f, 6, 6, "rocket_pod"),
    MISSILE("Missile guide", 34f, 4.2f, 2.0f, 20, 1, "air_missile"),
    BOMB("Bombe", 46f, 5.5f, 0f, 14, 1, "aerial_bomb");

    public final String label;
    public final float dmg, radius, speed;
    public final int cooldown, perItem;
    public final String item;

    W(String label, float dmg, float radius, float speed, int cooldown, int perItem, String item) {
        this.label = label;
        this.dmg = dmg;
        this.radius = radius;
        this.speed = speed;
        this.cooldown = cooldown;
        this.perItem = perItem;
        this.item = item;
    }
}
