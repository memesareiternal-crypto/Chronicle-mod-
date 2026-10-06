package dev.chronicle.power;

public record Intent(int buttons, int wheel) {
    public static final int GRIP = 1, ACT = 2, SNEAK = 4, JUMP = 8, SPRINT = 16;
    public static final Intent IDLE = new Intent(0, 0);
    public Intent { buttons &= 31; wheel = Integer.compare(wheel, 0); }
    public boolean has(int bit) { return (buttons & bit) != 0; }
    public enum Domain {
        MATTER("Matter"), EDGE("Cut / sculpt"), VOLUME("Region / terrain"), WARD("Force field"), BUILD("Remote building"), TEND("Harvest / replant"), SENSE("Radar"), FLIGHT("Flight"), BURST("Psionic explosion");
        public final String label;
        Domain(String label) { this.label = label; }
        public Domain next(int d) { return values()[Math.floorMod(ordinal() + d, values().length)]; }
    }
    public enum Edge { SWEEP, LINE, PLANE, PIERCE, WHIRL, VOLLEY, SCISSOR, TRACE;
        public Edge next(int d) { return values()[Math.floorMod(ordinal() + d, values().length)]; }
    }
}
