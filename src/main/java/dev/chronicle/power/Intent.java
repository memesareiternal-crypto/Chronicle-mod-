package dev.chronicle.power;

public record Intent(int buttons, int wheel) {
    public static final int GRIP = 1, ACT = 2, SNEAK = 4, JUMP = 8, SPRINT = 16, BARRIER = 32, FORWARD=64, BACK=128, LEFT=256, RIGHT=512, ATTACK=1024;
    public static final Intent IDLE = new Intent(0, 0);
    public Intent { buttons &= 2047; wheel = Integer.compare(wheel, 0); }
    public boolean has(int bit) { return (buttons & bit) != 0; }
    public enum Edge { SWEEP, LINE, PLANE, PIERCE, WHIRL, VOLLEY, SCISSOR, TRACE;
        public Edge next(int d) { return values()[Math.floorMod(ordinal() + d, values().length)]; }
    }
}
