package fr.udrone;

/** Types de drones. Vitesses en blocs/tick. */
public enum Kind {
    //   id                   titre                                 w     h     aile   vmax  acc   vvert  batt   portee hp   armes                             munitions
    MINI("mini_drone", "Mini-drone de reconnaissance", 0.40f, 0.22f, false, 0.42, 0.14, 0.22, 6000, 80, 8f, new W[]{}, new int[]{}),
    QUAD("quad_drone", "Drone quadricoptère tactique", 0.70f, 0.30f, false, 0.50, 0.12, 0.28, 8400, 110, 14f, new W[]{W.MG}, new int[]{150}),
    FAST("fast_drone", "Drone rapide", 0.55f, 0.25f, false, 1.05, 0.20, 0.40, 3600, 110, 8f, new W[]{W.CHARGE}, new int[]{1}),
    SURVEIL("surveillance_drone", "Drone de surveillance", 0.90f, 0.40f, false, 0.28, 0.08, 0.18, 24000, 120, 12f, new W[]{}, new int[]{}),
    HEAVY("heavy_drone", "Drone lourd de combat", 1.20f, 0.50f, false, 0.36, 0.08, 0.20, 9600, 100, 45f, new W[]{W.MG, W.SHELL, W.BOMB}, new int[]{200, 8, 4}),
    REAPER("mq9_reaper", "MQ-9 Reaper", 2.20f, 1.30f, true, 1.45, 0.02, 0.0, 36000, 150, 120f, new W[]{W.MISSILE, W.BOMB, W.MG}, new int[]{4, 4, 150});

    public final String id, title;
    public final float w, h, hp;
    public final boolean wing;
    public final double vmax, acc, vvert;
    public final int battery, range;
    public final W[] weapons;
    public final int[] ammo;

    Kind(String id, String title, float w, float h, boolean wing, double vmax, double acc, double vvert,
         int battery, int range, float hp, W[] weapons, int[] ammo) {
        this.id = id;
        this.title = title;
        this.w = w;
        this.h = h;
        this.wing = wing;
        this.vmax = vmax;
        this.acc = acc;
        this.vvert = vvert;
        this.battery = battery;
        this.range = range;
        this.hp = hp;
        this.weapons = weapons;
        this.ammo = ammo;
    }

    public int packedAmmo() {
        int p = 0;
        for (int i = 0; i < ammo.length && i < 3; i++) p |= (ammo[i] & 255) << (8 * i);
        return p;
    }
}
