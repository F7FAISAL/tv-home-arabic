package com.gathin.tvhome;

/** What the screensavers (ClockDream, ClockActivity) drive: the drifting clock, or the clean view's scene. */
interface DreamScene {
    void start();

    void stop();
}
