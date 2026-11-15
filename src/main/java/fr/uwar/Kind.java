package fr.uwar;

public enum Kind {
    HELI("attack_heli", "Helicoptere d'attaque AH-64", 2.4f, 2.6f, 2, 180f, 1400f, 0.9f, 3,
            W.ROCKET.bit() | W.MISSILE.bit(),
            new int[]{600, 38, 8, 0, 4, 4, 0, 0, 4, 0}),
    JET("fighter_jet", "Avion de chasse F-16", 2.6f, 1.6f, 1, 120f, 1600f, 1.0f, 4,
            W.ROCKET.bit() | W.MISSILE.bit() | W.BOMB.bit(),
            new int[]{900, 14, 6, 4, 6, 6, 2, 2, 4, 12});

    public final String id, name;
    public final float w, h, maxHp, fuelCap, fuelRate;
    public final int seats, slots, defaultLoadout;
    public final int[] cap;

    Kind(String id, String name, float w, float h, int seats, float hp, float fuel, float rate, int slots, int def, int[] cap) {
        this.id = id;
        this.name = name;
        this.w = w;
        this.h = h;
        this.seats = seats;
        this.maxHp = hp;
        this.fuelCap = fuel;
        this.fuelRate = rate;
        this.slots = slots;
        this.defaultLoadout = def | W.CANNON.bit();
        this.cap = cap;
    }

    public boolean heli() {
        return this == HELI;
    }

    public boolean canCarry(W w) {
        return cap[w.ordinal()] > 0;
    }
}
