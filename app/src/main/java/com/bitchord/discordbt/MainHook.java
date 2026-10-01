package com.bitchord.discordbt;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.media.AudioDeviceInfo;
import android.os.Build;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

public final class MainHook extends XposedModule {
    private static final String TAG = "BitChordDiscordBT";

    private static final String FAKE_DISCORD_QUALITY = "Hi-Res Lossless \u2022 FLAC \u2022 24-bit \u2022 96 kHz \u2022 2458 kbps \u2022 Stereo";

    @Override
    public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param) {
        log(Log.INFO, TAG, "BitChord Discord+BT loaded for " + param.getProcessName());
    }

    @Override
    public void onPackageLoaded(XposedModuleInterface.PackageLoadedParam param) {
        if (!"com.music.bitchord".equals(param.getPackageName())) {
            return;
        }

        ClassLoader classLoader = param.getDefaultClassLoader();

        // 1. Show user's Bluetooth rename alias in the output device name
        hookBluetoothAlias();

        // 2. Fake Hi-Res quality line in Discord Rich Presence
        hookDiscordQuality(classLoader);
    }

    private void hookBluetoothAlias() {
        try {
            Method productName = AudioDeviceInfo.class.getDeclaredMethod("getProductName");

            hook(productName)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object original = chain.proceed();
                    try {
                        Object thisObj = chain.getThisObject();
                        if (thisObj instanceof AudioDeviceInfo) {
                            String alias = findBluetoothAlias((AudioDeviceInfo) thisObj, (CharSequence) original);
                            if (alias != null && !alias.isEmpty()) {
                                return alias;
                            }
                        }
                    } catch (Throwable ignored) {}
                    return original;
                });

            log(Log.INFO, TAG, "Hooked AudioDeviceInfo.getProductName()");
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "Failed to hook AudioDeviceInfo.getProductName(): " + t);
        }
    }

    private String findBluetoothAlias(AudioDeviceInfo info, CharSequence original) {
        if (info == null) return null;

        int type = info.getType();
        // A2DP=8, SCO=7, Hearing Aid=23, BLE Headset=26, BLE Speaker=27, BLE Broadcast=30
        boolean isBt = (type == 7 || type == 8 || type == 23 || type == 26 || type == 27 || type == 30);
        if (!isBt) return null;

        BluetoothAdapter adapter;
        try {
            adapter = BluetoothAdapter.getDefaultAdapter();
        } catch (Throwable t) {
            return null;
        }
        if (adapter == null) return null;

        String address = null;
        try {
            Method getAddress = AudioDeviceInfo.class.getDeclaredMethod("getAddress");
            getAddress.setAccessible(true);
            Object value = getAddress.invoke(info);
            address = value == null ? "" : value.toString().trim();
        } catch (Throwable ignored) {}

        if (address != null && !address.isEmpty() && !"02:00:00:00:00:00".equals(address)) {
            try {
                BluetoothDevice device = adapter.getRemoteDevice(address);
                String alias = getDeviceAlias(device);
                if (alias != null && !alias.isEmpty()) return alias;
            } catch (Throwable ignored) {}
        }

        try {
            Set<BluetoothDevice> bonded = adapter.getBondedDevices();
            if (bonded != null) {
                // Match by MAC
                if (address != null && !address.isEmpty() && !"02:00:00:00:00:00".equals(address)) {
                    for (BluetoothDevice dev : bonded) {
                        if (dev != null && address.equalsIgnoreCase(dev.getAddress())) {
                            String alias = getDeviceAlias(dev);
                            if (alias != null && !alias.isEmpty()) return alias;
                        }
                    }
                }
                // Match by hardware name
                if (original != null) {
                    String origStr = original.toString().trim();
                    for (BluetoothDevice dev : bonded) {
                        if (dev != null) {
                            String devName = dev.getName();
                            if (devName != null && origStr.equalsIgnoreCase(devName.trim())) {
                                String alias = getDeviceAlias(dev);
                                if (alias != null && !alias.isEmpty()) return alias;
                            }
                        }
                    }
                }
                // Fall back to first connected device
                for (BluetoothDevice dev : bonded) {
                    if (dev != null && isDeviceConnected(dev)) {
                        String alias = getDeviceAlias(dev);
                        if (alias != null && !alias.isEmpty()) return alias;
                    }
                }
            }
        } catch (Throwable ignored) {}

        return null;
    }

    private static String getDeviceAlias(BluetoothDevice device) {
        if (device == null) return null;

        try {
            if (Build.VERSION.SDK_INT >= 31) {
                String alias = device.getAlias();
                if (alias != null && !alias.trim().isEmpty()) return alias.trim();
            }
        } catch (Throwable ignored) {}

        try {
            Method m = BluetoothDevice.class.getDeclaredMethod("getAlias");
            m.setAccessible(true);
            Object val = m.invoke(device);
            if (val != null) {
                String s = val.toString().trim();
                if (!s.isEmpty()) return s;
            }
        } catch (Throwable ignored) {}

        try {
            Method m = BluetoothDevice.class.getDeclaredMethod("getAliasName");
            m.setAccessible(true);
            Object val = m.invoke(device);
            if (val != null) {
                String s = val.toString().trim();
                if (!s.isEmpty()) return s;
            }
        } catch (Throwable ignored) {}

        return null;
    }

    private static boolean isDeviceConnected(BluetoothDevice device) {
        if (device == null) return false;
        try {
            Method m = BluetoothDevice.class.getDeclaredMethod("isConnected");
            m.setAccessible(true);
            return Boolean.TRUE.equals(m.invoke(device));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void hookDiscordQuality(ClassLoader cl) {
        if (cl == null) return;

        // Hook discordAudioQualityLine
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
                                if (orig instanceof String && !((String) orig).isEmpty()) {
                                    return orig;
                                }
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

        // Hook DiscordWebSocket.sendActivity as a secondary guarantee
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
