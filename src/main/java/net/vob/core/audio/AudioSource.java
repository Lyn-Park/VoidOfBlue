package net.vob.core.audio;

public interface AudioSource {
    void start();
    void pause();
    void stop();
    
    long getMSLength();
    long getMSTime();
    void setMSTime(long time);
    
    double getVolume();
    void setVolume(double volume);
    
    double getBalance();
    void setBalance(double balance);
    
    void setLoopPointsMS(long start, long end);
    
    boolean isMute();
    void setMute(boolean mute);
    
    boolean isLoop();
    void setLoop(boolean loop);
}
