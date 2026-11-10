package fr.ustruct;

/** Parcourt un volume par cellules de 16x16x16 (x le plus rapide, puis z, puis y). */
public class Walker {
    public final int sx, sy, sz;
    public int x, y, z;
    private int cx, cy, cz, x0, x1, y0, y1, z0, z1;
    public long index = 0;

    public Walker(int sx, int sy, int sz) {
        this.sx = sx;
        this.sy = sy;
        this.sz = sz;
        startCell();
    }

    private void startCell() {
        x0 = cx;
        x1 = Math.min(cx + 16, sx);
        y0 = cy;
        y1 = Math.min(cy + 16, sy);
        z0 = cz;
        z1 = Math.min(cz + 16, sz);
        x = x0;
        y = y0;
        z = z0;
    }

    public long total() {
        return (long) sx * sy * sz;
    }

    /** Avance d'un bloc. Renvoie false quand le volume est termine. */
    public boolean next() {
        index++;
        x++;
        if (x >= x1) {
            x = x0;
            z++;
            if (z >= z1) {
                z = z0;
                y++;
                if (y >= y1) {
                    cy += 16;
                    if (cy >= sy) {
                        cy = 0;
                        cx += 16;
                        if (cx >= sx) {
                            cx = 0;
                            cz += 16;
                            if (cz >= sz) return false;
                        }
                    }
                    startCell();
                }
            }
        }
        return true;
    }
}
