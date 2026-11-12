package fr.ugta;

/** Les differents vehicules. */
public enum Kind {
    //            nom            larg  haut  places  vmax  acc    virage cat
    SEDAN("sedan", 2.0f, 1.5f, 2, 0.85, 0.035, 4.0f, 0),
    SPORTS("sports_car", 2.0f, 1.3f, 2, 1.25, 0.055, 4.6f, 0),
    POLICE("police_car", 2.0f, 1.5f, 2, 1.05, 0.045, 4.4f, 0),
    TRUCK("truck", 2.6f, 3.0f, 2, 0.6, 0.022, 2.4f, 0),
    PLANE("plane", 3.0f, 1.8f, 1, 1.6, 0.0, 0f, 1),
    HELI("helicopter", 3.0f, 2.2f, 1, 0.9, 0.0, 0f, 2),
    POLICE_HELI("police_helicopter", 3.0f, 2.2f, 1, 0.95, 0.0, 0f, 2);

    public final String id;
    public final float w, h;
    public final int seats;
    public final double max, acc;
    public final float turn;
    /** 0 = route, 1 = avion, 2 = helicoptere */
    public final int cat;

    Kind(String id, float w, float h, int seats, double max, double acc, float turn, int cat) {
        this.id = id;
        this.w = w;
        this.h = h;
        this.seats = seats;
        this.max = max;
        this.acc = acc;
        this.turn = turn;
        this.cat = cat;
    }
}
