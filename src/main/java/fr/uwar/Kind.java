package fr.uwar;

public enum Kind {
    HELI("attack_heli", "Helicoptere d'attaque AH-64", 2.4f, 2.6f, 2, 180f, 1400f, 0.9f, 3,
            W.ROCKET.bit() | W.MISSILE.bit(),
            new int[]{600, 38, 8, 0, 4, 4, 0, 0, 4, 0, 1, 2, 2, 0, 0, 0, 0, 0, 0, 0}, 1f, 1f, 0.6f, 0.25f, 1f),
    JET("fighter_jet", "Avion de chasse F-16", 2.6f, 1.6f, 1, 120f, 1600f, 1.0f, 4,
            W.ROCKET.bit() | W.MISSILE.bit() | W.BOMB.bit(),
            new int[]{900, 14, 6, 4, 6, 6, 2, 2, 4, 12, 1, 4, 2, 4, 4, 4, 2, 2, 2, 2}, 1f, 1f, 0.6f, 0.25f, 1f),
    MINI("mini_fighter", "Mini chasseur", 1.5f, 1.0f, 1, 70f, 1000f, 0.8f, 2,
            W.ROCKET.bit() | W.MISSILE.bit(),
            new int[]{500, 8, 4, 2, 4, 4, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}, 0.62f, 1.9f, 0.35f, 0.12f, 0.9f),
    B17("b17", "Bombardier B-17 Forteresse volante", 6.0f, 3.0f, 2, 420f, 4200f, 1.2f, 9,
            W.BOMB.bit(),
            new int[]{800, 0, 0, 40, 0, 0, 0, 0, 0, 24, 1, 0, 0, 24, 24, 24, 16, 16, 16, 16}, 1f, 0.38f, 0.5f, 0.16f, 0.62f);

    public final String id, name;
    public final float w, h, maxHp, fuelCap, fuelRate;
    public final int seats, slots, defaultLoadout;
    /** sc = echelle du modele, turn = agilite (taux de rotation), liftoff = vitesse mini pour lever le nez, stall = vitesse de decrochage, speedMul = vitesse max. */
    public final float sc, turn, liftoff, stall, speedMul;
    public final int[] cap;

    Kind(String id, String name, float w, float h, int seats, float hp, float fuel, float rate, int slots, int def, int[] cap, float sc, float turn, float liftoff, float stall, float speedMul) {
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
        this.sc = sc;
        this.turn = turn;
        this.liftoff = liftoff;
        this.stall = stall;
        this.speedMul = speedMul;
    }

    public boolean bomber() {
        return this == B17;
    }

    /** Multiplicateur de distance de la camera. */
    public float camMul() {
        return this == B17 ? 2.3f : 1f;
    }

    public boolean heli() {
        return this == HELI;
    }

    public boolean canCarry(W w) {
        return cap[w.ordinal()] > 0;
    }
}
