package net.vob.core.audio;

import java.io.IOException;
import java.util.logging.Logger;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.BooleanControl;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;
import net.vob.VoidOfBlue;
import net.vob.util.Closable;
import net.vob.util.Identity;
import net.vob.util.logging.Level;
import net.vob.util.logging.LocaleUtils;
import net.vob.util.math.Maths;

public class AudioWAV extends Closable implements AudioSource {
    private static final Logger LOG = VoidOfBlue.getLogger(AudioWAV.class);
    
    private final AudioInputStream ais;
    private final Clip clip;
    private int loopStart = 0, loopEnd = -1;
    private boolean looping = false;
    
    public AudioWAV(Identity id) 
            throws LineUnavailableException, UnsupportedAudioFileException, IOException
    {
        this.ais = AudioSystem.getAudioInputStream(id.getLastURL("wav", "audio"));
        this.clip = AudioSystem.getClip();
        
        this.clip.open(this.ais);
    }
    
    @Override
    public void start() {
        if (isClosed())
            throw new IllegalStateException(LocaleUtils.format("global.Exception.Closed", "AudioClip"));
        
        clip.start();
        if (looping)
            clip.loop(Clip.LOOP_CONTINUOUSLY);
    }
    
    @Override
    public void pause() {
        if (isClosed())
            throw new IllegalStateException(LocaleUtils.format("global.Exception.Closed", "AudioClip"));
        
        clip.stop();
        clip.flush();
    }
    
    @Override
    public void stop() {
        if (isClosed())
            throw new IllegalStateException(LocaleUtils.format("global.Exception.Closed", "AudioClip"));
        
        clip.stop();
        clip.flush();
        clip.setFramePosition(0);
        looping = false;
    }
    
    @Override
    public long getMSLength() {
        if (isClosed())
            throw new IllegalStateException(LocaleUtils.format("global.Exception.Closed", "AudioClip"));
        
        return clip.getMicrosecondLength();
    }
    
    @Override
    public long getMSTime() {
        if (isClosed())
            throw new IllegalStateException(LocaleUtils.format("global.Exception.Closed", "AudioClip"));
        
        return clip.getMicrosecondPosition();
    }
    
    @Override
    public void setMSTime(long time) {
        if (isClosed())
            throw new IllegalStateException(LocaleUtils.format("global.Exception.Closed", "AudioClip"));
        
        clip.setMicrosecondPosition(time);
    }
    
    @Override
    public double getVolume() {
        if (isClosed())
            throw new IllegalStateException(LocaleUtils.format("global.Exception.Closed", "AudioClip"));
        
        if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            FloatControl control = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
            return control.getValue();
            
        } else {
            LOG.log(Level.WARNING, "AudioWAV.InvalidControl", "gain");
            return Double.NaN;
        }
    }
    
    @Override
    public void setVolume(double volume) {
        if (isClosed())
            throw new IllegalStateException(LocaleUtils.format("global.Exception.Closed", "AudioClip"));
        
        if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            FloatControl control = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
            control.setValue(Maths.clamp(control.getMinimum(), (float)volume, control.getMaximum()));
            
        } else
            LOG.log(Level.WARNING, "AudioWAV.InvalidControl", "gain");
    }
    
    @Override
    public double getBalance() {
        if (isClosed())
            throw new IllegalStateException(LocaleUtils.format("global.Exception.Closed", "AudioClip"));
        
        if (clip.isControlSupported(FloatControl.Type.BALANCE)) {
            FloatControl control = (FloatControl) clip.getControl(FloatControl.Type.BALANCE);
            return control.getValue();
            
        } else {
            LOG.log(Level.WARNING, "AudioWAV.InvalidControl", "balance");
            return Double.NaN;
        }
    }
    
    @Override
    public void setBalance(double balance) {
        if (isClosed())
            throw new IllegalStateException(LocaleUtils.format("global.Exception.Closed", "AudioClip"));
        
        if (clip.isControlSupported(FloatControl.Type.BALANCE)) {
            FloatControl control = (FloatControl) clip.getControl(FloatControl.Type.BALANCE);
            control.setValue(Maths.clamp(control.getMinimum(), (float)balance, control.getMaximum()));
            
        } else
            LOG.log(Level.WARNING, "AudioWAV.InvalidControl", "balance");
    }
    
    @Override
    public void setLoopPointsMS(long start, long end) {
        if (isClosed())
            throw new IllegalStateException(LocaleUtils.format("global.Exception.Closed", "AudioClip"));
        if (end < start)
            throw new IllegalArgumentException(LocaleUtils.format("global.Exception.OutOfRange.x>=", "end", end, start));
        
        float framerate = clip.getFormat().getFrameRate();
        
        if      (start <= 0)                           loopStart = 0;
        else if (start >= clip.getMicrosecondLength()) loopStart = -1;
        else
            loopStart = (int)Math.floor(start * framerate / 1000d);
        
        if      (end <= 0)                             loopEnd = 0;
        else if (end >= clip.getMicrosecondLength())   loopEnd = -1;
        else
            loopEnd = (int)Math.floor(end * framerate / 1000d);
        
        clip.setLoopPoints(loopStart, loopEnd);
    }
    
    @Override
    public boolean isMute() {
        if (isClosed())
            throw new IllegalStateException(LocaleUtils.format("global.Exception.Closed", "AudioClip"));
        
        if (clip.isControlSupported(BooleanControl.Type.MUTE)) {
            BooleanControl control = (BooleanControl) clip.getControl(BooleanControl.Type.MUTE);
            return control.getValue();
            
        } else {
            LOG.log(Level.WARNING, "AudioWAV.InvalidControl", "mute");
            return false;
        }
    }
    
    @Override
    public void setMute(boolean mute) {
        if (isClosed())
            throw new IllegalStateException(LocaleUtils.format("global.Exception.Closed", "AudioClip"));
        
        if (clip.isControlSupported(BooleanControl.Type.MUTE)) {
            BooleanControl control = (BooleanControl) clip.getControl(BooleanControl.Type.MUTE);
            control.setValue(mute);
            
        } else
            LOG.log(Level.WARNING, "AudioWAV.InvalidControl", "mute");
    }
    
    @Override
    public boolean isLoop() {
        if (isClosed())
            throw new IllegalStateException(LocaleUtils.format("global.Exception.Closed", "AudioClip"));
        
        return looping;
    }
    
    @Override
    public void setLoop(boolean loop) {
        if (isClosed())
            throw new IllegalStateException(LocaleUtils.format("global.Exception.Closed", "AudioClip"));
        
        if (looping == loop)
            return;
        
        looping = loop;
        clip.loop(loop ? Clip.LOOP_CONTINUOUSLY : 0);
    }
    
    @Override
    protected boolean doClose() {
        try {
            clip.stop();
            
            clip.close();
            ais.close();
        } catch (IOException e) {
            LOG.log(Level.WARNING, LocaleUtils.format("global.Exception.IOException", "AudioClip"), e);
        }
        
        return true;
    }
}
