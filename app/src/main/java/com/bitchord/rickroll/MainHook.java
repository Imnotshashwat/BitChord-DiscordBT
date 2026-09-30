package com.bitchord.rickroll;

import android.util.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

public final class MainHook extends XposedModule {
    private static final String TAG = "BitChordRickroll";

    private static final String FAKE_DISCORD_QUALITY = "Hi-Res Lossless \u2022 FLAC \u2022 24-bit \u2022 96 kHz \u2022 2458 kbps \u2022 Stereo";

    @Override
    public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param) {
        log(Log.INFO, TAG, "BitChord Rickroll loaded for " + param.getProcessName());
    }

    @Override
    public void onPackageLoaded(XposedModuleInterface.PackageLoadedParam param) {
        if (!"com.music.bitchord".equals(param.getPackageName())) {
            return;
        }

        ClassLoader classLoader = param.getDefaultClassLoader();

        // 1. Hook Playing Screen Badges (Shows Hi-Res Lossless on lossy tracks, strictly preserves addon FLAC/Lossless)
        hookPlayerScreenBadges(classLoader);

        // 2. Hook Discord Rich Presence Audio Quality (Shows Hi-Res on lossy tracks, strictly preserves addon FLAC/Lossless)
        hookDiscordQuality(classLoader);
    }

    /**
     * Checks whether a Snapshot represents authentic FLAC/ALAC/WAV or genuine Dolby Atmos from an addon.
     * If true, Rickroll strictly preserves it and NEVER touches it!
     */
    private static boolean isRealLosslessOrDolby(Object snapshot) {
        if (snapshot == null) return false;

        try {
            // 1. Check native isLossless() on the snapshot
            Method isLosslessMeth = snapshot.getClass().getDeclaredMethod("isLossless");
            isLosslessMeth.setAccessible(true);
            Object isLosslessVal = isLosslessMeth.invoke(snapshot);
            if (Boolean.TRUE.equals(isLosslessVal)) {
                return true;
            }
        } catch (Throwable ignored) {}

        try {
            // 2. Check native mimeType for FLAC / ALAC / WAV / Dolby Atmos
            Field mimeField = snapshot.getClass().getDeclaredField("mimeType");
            mimeField.setAccessible(true);
            Object mimeVal = mimeField.get(snapshot);
            if (mimeVal instanceof String) {
                String m = ((String) mimeVal).toLowerCase().trim();
                // Lossless codecs
                if (m.contains("flac") || m.contains("alac") || m.contains("wav")
                    || m.contains("aiff") || m.contains("dsf") || m.contains("dff")
                    || m.contains("ape") || m.contains("wv")) {
                    return true;
                }
                // Dolby Atmos codecs
                if (m.contains("eac3") || m.contains("ec-3") || m.contains("ec3")
                    || m.contains("atmos") || m.contains("joc")) {
                    return true;
                }
            }
        } catch (Throwable ignored) {}

        return false;
    }

    /**
     * Constructs a synthetic NerdStats$Snapshot instance representing 24-bit 96 kHz Hi-Res Lossless FLAC.
     * Does NOT mutate the original snapshot object.
     */
    private static Object createFakeSnapshot(ClassLoader cl, Object origSnapshot) {
        try {
            Class<?> snapshotClass = cl.loadClass("com.music.bitchord.data.NerdStats$Snapshot");
            Class<?> streamFormatClass = cl.loadClass("com.music.bitchord.data.sources.StreamFormat");

            Constructor<?> ctor = snapshotClass.getDeclaredConstructor(
                String.class,
                Integer.class,
                Integer.class,
                Integer.class,
                Integer.class,
                streamFormatClass,
                String.class
            );
            ctor.setAccessible(true);

            Object claimed = null;
            String sourceName = null;
            if (origSnapshot != null) {
                try {
                    Field fClaimed = origSnapshot.getClass().getDeclaredField("claimed");
                    fClaimed.setAccessible(true);
                    claimed = fClaimed.get(origSnapshot);
                } catch (Throwable ignored) {}
                try {
                    Field fSource = origSnapshot.getClass().getDeclaredField("sourceName");
                    fSource.setAccessible(true);
                    sourceName = (String) fSource.get(origSnapshot);
                } catch (Throwable ignored) {}
            }

            return ctor.newInstance(
                "audio/flac",            // mimeType (Lossless)
                Integer.valueOf(2458),   // bitrateKbps
                Integer.valueOf(96000),  // sampleRateHz (> 44.1 kHz -> Hi-Res)
                Integer.valueOf(2),      // channels (Stereo)
                Integer.valueOf(24),     // bitDepth (24-bit -> Hi-Res)
                claimed,
                sourceName
            );
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Hooks player screen badges:
     * - If the track is from an addon (genuine FLAC/Lossless), keep genuine badges (e.g. 16-bit Lossless, 24-bit Hi-Res).
     * - If an addon is actively racing in the background (searching for lossless), let "Upgrading Quality" display.
     * - If the track is ordinary lossy (YouTube Opus/AAC), supply a fake Hi-Res snapshot to display "Hi-Res Lossless".
     * NOTE: We NEVER touch StreamFormat, and we never mutate original NerdStats objects!
     */
    private void hookPlayerScreenBadges(ClassLoader cl) {
        if (cl == null) return;

        try {
            Class<?> playerControls = cl.loadClass("com.music.bitchord.ui.player.PlayerControlsKt");
            for (Method m : playerControls.getDeclaredMethods()) {
                if ("LosslessOrStats".equals(m.getName())) {
                    hook(m)
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            Object[] args = chain.getArgs().toArray();
                            // args: [isLoading (Z), stillRacing (Z), losslessRequested (Z), effectiveQuality, nerdStats, modifier, composer, changed]
                            if (args.length > 4 && args[4] != null) {
                                Object origSnapshot = args[4];
                                boolean stillRacing = Boolean.TRUE.equals(args[1]);

                                // If an addon is actively racing in the background, don't spoof yet so "Upgrading Quality" shows
                                if (!stillRacing) {
                                    // If the track is real lossless (FLAC, ALAC, WAV) or Dolby Atmos, DO NOT TOUCH IT!
                                    if (!isRealLosslessOrDolby(origSnapshot)) {
                                        // Lossy track: provide synthetic Hi-Res snapshot without mutating original
                                        Object fakeSnapshot = createFakeSnapshot(cl, origSnapshot);
                                        if (fakeSnapshot != null) {
                                            args[4] = fakeSnapshot;
                                            return chain.proceed(args);
                                        }
                                    }
                                }
                            }
                            return chain.proceed();
                        });
                    log(Log.INFO, TAG, "Hooked PlayerControlsKt.LosslessOrStats");
                    break;
                }
            }
        } catch (Throwable t) {
            log(Log.WARN, TAG, "Failed to hook PlayerControlsKt: " + t);
        }
    }

    /**
     * Hooks Discord Rich Presence Audio Quality:
     * - If the track is a real FLAC/Lossless from an addon, Discord shows the genuine specs (e.g. Lossless • FLAC • 341 kbps • 44.1 kHz • Stereo).
     * - If the track is ordinary lossy (Opus/AAC), Discord shows "Hi-Res Lossless • FLAC • 24-bit • 96 kHz • Stereo".
     */
    private void hookDiscordQuality(ClassLoader cl) {
        if (cl == null) return;

        // 1. Hook discordAudioQualityLine(NerdStats.Snapshot)
        String[] candidateClasses = new String[] {
            "coil.util.-Collections",
            "com.music.bitchord.data.discord.DiscordAudioQualityKt",
            "com.music.bitchord.data.discord.DiscordAudioQuality"
        };
        for (String cName : candidateClasses) {
            try {
                Class<?> clazz = cl.loadClass(cName);
                for (Method m : clazz.getDeclaredMethods()) {
                    if ("discordAudioQualityLine".equals(m.getName())) {
                        hook(m)
                            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                            .intercept(chain -> {
                                Object orig = chain.proceed();
                                // If the track is genuine Lossless/FLAC from an addon, DO NOT touch it!
                                if (orig instanceof String && !((String) orig).isEmpty()) {
                                    return orig;
                                }
                                // Lossy track: return fake Hi-Res line only if a song snapshot is active
                                Object[] args = chain.getArgs().toArray();
                                if (args.length > 0 && args[0] != null) {
                                    return FAKE_DISCORD_QUALITY;
                                }
                                return orig;
                            });
                        log(Log.INFO, TAG, "Hooked discordAudioQualityLine on " + cName);
                        break;
                    }
                }
            } catch (Throwable ignored) {}
        }

        // 2. Hook DiscordWebSocket.sendActivity as a secondary guarantee
        try {
            Class<?> wsClass = cl.loadClass("com.my.kizzy.gateway.DiscordWebSocket");
            for (Method m : wsClass.getDeclaredMethods()) {
                if ("sendActivity".equals(m.getName())) {
                    hook(m)
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            try {
                                Object[] args = chain.getArgs().toArray();
                                if (args.length > 0 && args[0] != null) {
                                    Object presence = args[0];
                                    Field actField = presence.getClass().getDeclaredField("activities");
                                    actField.setAccessible(true);
                                    Object activities = actField.get(presence);
                                    if (activities instanceof List) {
                                        for (Object act : (List<?>) activities) {
                                            if (act == null) continue;
                                            Field assetsField = act.getClass().getDeclaredField("assets");
                                            assetsField.setAccessible(true);
                                            Object assets = assetsField.get(act);
                                            if (assets != null) {
                                                Field largeTextField = assets.getClass().getDeclaredField("largeText");
                                                largeTextField.setAccessible(true);
                                                Object currentLt = largeTextField.get(assets);
                                                // Only inject fake quality if largeText is currently empty/null (lossy stream)
                                                // Real FLAC/Lossless tracks already have genuine largeText from discordAudioQualityLine!
                                                if (currentLt == null || ((String) currentLt).isEmpty()) {
                                                    largeTextField.set(assets, FAKE_DISCORD_QUALITY);
                                                }
                                            }
                                        }
                                    }
                                }
                            } catch (Throwable ignored) {}
                            return chain.proceed();
                        });
                    log(Log.INFO, TAG, "Hooked DiscordWebSocket.sendActivity");
                    break;
                }
            }
        } catch (Throwable ignored) {}
    }
}
