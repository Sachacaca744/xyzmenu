package fr.udrone;

/** Armes virtuelles (mecaniques de jeu fictives). */
public enum W {
    MG("mitrailleuse", 1.5f, 0f, 2, 0f),
    SHELL("obus", 11f, 2.6f, 40, 2.0f),
    BOMB("bombe", 18f, 3.8f, 24, 0f),
    MISSILE("missile", 20f, 3.2f, 45, 0.5f),
    CHARGE("charge explosive", 26f, 4.5f, 10, 0f);

    public final String title;
    public final float dmg, radius, speed;
    public final int cooldown;

    W(String title, float dmg, float radius, int cooldown, float speed) {
        this.title = title;
        this.dmg = dmg;
        this.radius = radius;
        this.cooldown = cooldown;
        this.speed = speed;
    }
}
