package de.maxhenkel.voicechat.gui.widgets;

import com.mojang.blaze3d.matrix.MatrixStack;
import de.maxhenkel.voicechat.Voicechat;
import de.maxhenkel.voicechat.VoicechatClient;
import de.maxhenkel.voicechat.debug.VoicechatUncaughtExceptionHandler;
import de.maxhenkel.voicechat.voice.client.*;
import de.maxhenkel.voicechat.voice.client.speaker.Speaker;
import de.maxhenkel.voicechat.voice.client.speaker.SpeakerException;
import de.maxhenkel.voicechat.voice.client.speaker.SpeakerManager;
import de.maxhenkel.voicechat.voice.common.Utils;
import net.minecraft.client.gui.widget.button.AbstractButton;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;

import javax.annotation.Nullable;

public class MicTestButton extends AbstractButton {

    private static final ITextComponent TEST = new TranslationTextComponent("message.voicechat.mic_test");
    private static final ITextComponent DISABLE_TEST = new StringTextComponent("X");
    private static final ITextComponent TEST_UNAVAILABLE = new TranslationTextComponent("message.voicechat.mic_test_unavailable").withStyle(ChatFormatting.RED);

    private boolean micActive;
    @Nullable
    private VoiceThread voiceThread;
    private final MicListener micListener;
    @Nullable
    private final ClientVoicechat client;

    public MicTestButton(int xIn, int yIn, int widthIn, int heightIn, MicListener micListener) {
        super(xIn, yIn, widthIn, heightIn, TEST);
        this.micListener = micListener;
        this.client = ClientManager.getClient();
        active = client == null || client.getSoundManager() != null;
    }

    @Override
    public void render(MatrixStack matrixStack, int x, int y, float partialTicks) {
        super.render(matrixStack, x, y, partialTicks);
        if (visible && voiceThread != null) {
            voiceThread.updateLastRender();
        }
    }

    public void setMicActive(boolean micActive) {
        this.micActive = micActive;
        updateText();
    }

    private void updateText() {
        if (micActive) {
            setMessage(DISABLE_TEST);
        } else {
            setMessage(TEST);
        }
    }

    @Nullable
    public ITextComponent getHoverText() {
        if (!active) {
            return TEST_UNAVAILABLE;
        }
        return null;
    }

    @Override
    public void onPress() {
        setMicActive(!micActive);
        if (micActive) {
            close();
            try {
                voiceThread = new VoiceThread();
                voiceThread.start();
            } catch (Exception e) {
                setMicActive(false);
                active = false;
                Voicechat.LOGGER.error("Microphone error", e);
            }
        } else {
            close();
        }
        updateText();
    }

    private void close() {
        if (voiceThread != null) {
            voiceThread.close();
            voiceThread = null;
        }
    }

    public void stop() {
        close();
        setMicActive(false);
    }

    private class VoiceThread extends Thread {

        private final Speaker speaker;
        private boolean running;
        private long lastRender;
        private MicThread micThread;
        private boolean usesOwnMicThread;
        @Nullable
        private SoundManager ownSoundManager;

        public VoiceThread() throws SpeakerException, MicrophoneException {
            this.running = true;
            setDaemon(true);
            setName("VoiceTestingThread");
            setUncaughtExceptionHandler(new VoicechatUncaughtExceptionHandler());

            micThread = client != null ? client.getMicThread() : null;
            if (micThread == null) {
                micThread = new MicThread(client, null);
                usesOwnMicThread = true;
            } else {
                micThread.getError(onMicError);
            }

            SoundManager soundManager;
            if (client == null) {
                soundManager = new SoundManager(VoicechatClient.CLIENT_CONFIG.speaker.get());
                ownSoundManager = soundManager;
            } else {
                soundManager = client.getSoundManager();
            }

            if (soundManager == null) {
                throw new SpeakerException("No sound manager");
            }

            speaker = SpeakerManager.createSpeaker(soundManager, null);

            updateLastRender();
            setMicLocked(true);
        }

        @Override
        public void run() {
            while (running) {
                if (System.currentTimeMillis() - lastRender > 500L) {
                    break;
                }
                short[] buff = micThread.pollMic();
                if (buff == null) {
                    continue;
                }

                micListener.onMicValue(Utils.dbToPerc(Utils.getHighestAudioLevel(buff)));

                speaker.play(buff, VoicechatClient.CLIENT_CONFIG.voiceChatVolume.get().floatValue(), null);
            }
            speaker.close();
            setMicLocked(false);
            micListener.onMicValue(0D);
            if (usesOwnMicThread) {
                micThread.close();
            }
            if (ownSoundManager != null) {
                ownSoundManager.close();
            }
            setMicActive(false);
            Voicechat.LOGGER.info("Mic test audio channel closed");
        }

        public void updateLastRender() {
            lastRender = System.currentTimeMillis();
        }

        private void setMicLocked(boolean locked) {
            micThread.setMicrophoneLocked(locked);
        }

        public void close() {
            if (!running) {
                return;
            }
            Voicechat.LOGGER.info("Stopping mic test audio channel");
            running = false;
            try {
                join();
            } catch (InterruptedException e) {
                Voicechat.LOGGER.warn("Failed to close microphone", e);
            }
        }
    }

    public interface MicListener {
        void onMicValue(double percentage);
    }
}
