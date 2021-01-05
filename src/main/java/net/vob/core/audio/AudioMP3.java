package net.vob.core.audio;

import java.util.logging.Logger;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.util.Duration;
import net.vob.VoidOfBlue;
import net.vob.util.Closable;
import net.vob.util.Identity;
import net.vob.util.logging.Level;
import net.vob.util.logging.LocaleUtils;

public class AudioMP3 extends Closable implements AudioSource {
    private static final Logger LOG = VoidOfBlue.getLogger(AudioMP3.class);
    private final MediaPlayer player;
    
    private Duration loopStart = Duration.ZERO, loopEnd;
    private boolean looping = false;
    
    public AudioMP3(Identity id) {
        Media media = new Media(id.getLastFile("mp3", "audio").toURI().toString());
        media.setOnError(() -> {
            LOG.log(Level.WARNING, LocaleUtils.format("AudioMP3.PlaybackError"), media.getError());
        });
        
        this.player = new MediaPlayer(media);
        player.setOnError(() -> {
            LOG.log(Level.WARNING, LocaleUtils.format("AudioMP3.PlaybackError"), player.getError());
        });
        
        this.loopEnd = media.getDuration();
    }

    @Override
    public void start() {
        if (isClosed())
            throw new IllegalStateException(LocaleUtils.format("global.Exception.Closed", "AudioMP3"));
        
        player.play();
        if (looping)
            player.setCycleCount(MediaPlayer.INDEFINITE);
    }
    
    @Override
    public void pause() {
        if (isClosed())
            throw new IllegalStateException(LocaleUtils.format("global.Exception.Closed", "AudioMP3"));
        
        player.pause();
    }

    @Override
    public void stop() {
        if (isClosed())
            throw new IllegalStateException(LocaleUtils.format("global.Exception.Closed", "AudioMP3"));
        
        player.stop();
    }
    
    @Override
    public long getMSLength() {
        if (isClosed())
            throw new IllegalStateException(LocaleUtils.format("global.Exception.Closed", "AudioMP3"));
        
        return (long) player.getMedia().getDuration().toMillis();
    }

    @Override
    public long getMSTime() {
        if (isClosed())
            throw new IllegalStateException(LocaleUtils.format("global.Exception.Closed", "AudioMP3"));
        
        return (long) player.getCurrentTime().toMillis();
    }

    @Override
    public void setMSTime(long time) {
        if (isClosed())
            throw new IllegalStateException(LocaleUtils.format("global.Exception.Closed", "AudioMP3"));
        
        player.seek(Duration.millis(time));
    }

    @Override
    public double getVolume() {
        if (isClosed())
            throw new IllegalStateException(LocaleUtils.format("global.Exception.Closed", "AudioMP3"));
        
        return player.getVolume();
    }

    @Override
    public void setVolume(double volume) {
        if (isClosed())
            throw new IllegalStateException(LocaleUtils.format("global.Exception.Closed", "AudioMP3"));
        
        player.setVolume(volume);
    }

    @Override
    public double getBalance() {
        if (isClosed())
            throw new IllegalStateException(LocaleUtils.format("global.Exception.Closed", "AudioMP3"));
        
        return player.getBalance();
    }

    @Override
    public void setBalance(double balance) {
        if (isClosed())
            throw new IllegalStateException(LocaleUtils.format("global.Exception.Closed", "AudioMP3"));
        
        player.setBalance(balance);
    }
    
    @Override
    public void setLoopPointsMS(long start, long end) {
        if (isClosed())
            throw new IllegalStateException(LocaleUtils.format("global.Exception.Closed", "AudioMP3"));
        if (end < start)
            throw new IllegalArgumentException(LocaleUtils.format("global.Exception.OutOfRange.x>=", "end", end, start));
        
        if (start < 0)
            loopStart = Duration.ZERO;
        else
            loopStart = Duration.millis(start);
        
        Duration duration = player.getMedia().getDuration();
        if (end > duration.toMillis())
            loopEnd = duration;
        else
            loopEnd = Duration.millis(end);
    }

    @Override
    public boolean isMute() {
        if (isClosed())
            throw new IllegalStateException(LocaleUtils.format("global.Exception.Closed", "AudioMP3"));
        
        return player.isMute();
    }

    @Override
    public void setMute(boolean mute) {
        if (isClosed())
            throw new IllegalStateException(LocaleUtils.format("global.Exception.Closed", "AudioMP3"));
        
        player.setMute(mute);
    }
    
    @Override
    public boolean isLoop() {
        if (isClosed())
            throw new IllegalStateException(LocaleUtils.format("global.Exception.Closed", "AudioMP3"));
        
        return looping;
    }
    
    @Override
    public void setLoop(boolean loop) {
        if (isClosed())
            throw new IllegalStateException(LocaleUtils.format("global.Exception.Closed", "AudioMP3"));
        
        looping = loop;
        
        if (loop) {
            player.setCycleCount(MediaPlayer.INDEFINITE);
            player.setStartTime(loopStart);
            player.setStopTime(loopEnd);
        } else {
            player.setCycleCount(1);
            player.setStartTime(Duration.ZERO);
            player.setStopTime(player.getMedia().getDuration());
        }
    }

    @Override
    protected boolean doClose() {
        player.stop();
        player.dispose();
        
        return true;
    }
}
