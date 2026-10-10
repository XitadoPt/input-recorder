package pt.inputrecorder;

final class PlaybackCursor {
    private final int frameCount;
    private int index;
    private long completedCycles;

    PlaybackCursor(int frameCount) {
        if (frameCount <= 0) throw new IllegalArgumentException("Playback needs at least one frame.");
        this.frameCount = frameCount;
    }

    boolean prepare(boolean looping) {
        if (index < frameCount) return true;
        if (!looping) return false;
        index = 0;
        return true;
    }

    void advance() {
        if (index >= frameCount) throw new IllegalStateException("Playback cycle already finished.");
        index++;
        if (index == frameCount) completedCycles++;
    }

    int frameIndex() { return index; }

    int previewIndex(boolean looping) {
        return index < frameCount ? index : (looping ? 0 : frameCount - 1);
    }
    
    long cycleNumber() { return completedCycles + (index < frameCount ? 1 : 0); }
}
