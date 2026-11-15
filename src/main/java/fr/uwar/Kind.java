package fr.uwar;

public enum Kind {
    HELI("attack_heli", "Helicoptere d'attaque AH-64", 2.4f, 2.6f, 2, 180f, 1400f, 0.9f,
            new W[]{W.CANNON, W.ROCKET, W.MISSILE}, new int[]{600, 38, 8, 0}),
    JET("fighter_jet", "Avion de chasse F-16", 2.6f, 1.6f, 1, 120f, 1600f, 1.0f,
            new W[]{W.CANNON, W.ROCKET, W.MISSILE, W.BOMB}, new int[]{900, 14, 6, 4});

    public final String id, name;
    public final float w, h, maxHp, fuelCap, fuelRate;
    public final int seats;
    public final W[] weapons;
    public final int[] cap;

    Kind(String id, String name, float w, float h, int seats, float hp, float fuel, float rate, W[] wp, int[] cap) {
        this.id = id;
        this.name = name;
        this.w = w;
        this.h = h;
        this.seats = seats;
        this.maxHp = hp;
        this.fuelCap = fuel;
        this.fuelRate = rate;
        this.weapons = wp;
        this.cap = cap;
    }

    public boolean heli() {
        return this == HELI;
    }
}
