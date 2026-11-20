package com.qinxin.cookie;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.lang.reflect.Method;

public class CookieLogin {

    private static final String TAG = "PyroX";
    private static final String SDK_CLASS = "com.netease.ntunisdk.SdkNetease";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private CookieLogin() {
    }

    public static String check(String cookie) {
        if (cookie == null || cookie.trim().isEmpty()) {
            return "请输入 Cookie";
        }
        try {
            JSONObject outer = new JSONObject(cookie.trim());
            String sauthJson = outer.optString("sauth_json", "");
            if (sauthJson.isEmpty()) return "Cookie 缺少 sauth_json";
            JSONObject sauth = new JSONObject(sauthJson);
            String sdkuid = sauth.optString("sdkuid", "");
            if (sdkuid.isEmpty()) return "sauth_json 缺少 sdkuid";
        } catch (Throwable t) {
            return "Cookie 格式不正确";
        }
        return null;
    }

    public static void login(String rawCookie) {
        if (rawCookie == null) return;
        final String cookie = rawCookie.trim();
        if (cookie.isEmpty()) {
            toast("请先粘贴 Cookie", false);
            return;
        }

        String sauthJson;
        try {
            sauthJson = new JSONObject(cookie).optString("sauth_json", "");
        } catch (Throwable t) {
            toast("Cookie 解析失败", false);
            return;
        }
        if (sauthJson == null || sauthJson.isEmpty()) {
            toast("Cookie 里没有 sauth_json", false);
            return;
        }

        final String sauth = sauthJson;

        new Thread(new Runnable() {
                @Override
                public void run() {
                    JSONObject parsed;
                    try {
                        parsed = new JSONObject(sauth);
                    } catch (Throwable t) {
                        toast("sauth_json 不是合法 JSON", false);
                        return;
                    }

                    final JSONObject finalSauth = parsed;
                    MAIN.post(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    applyToSdk(finalSauth);
                                } catch (Throwable t) {
                                    Log.i(TAG, "Cookie 登录失败", t);
                                    toast("登录失败：" + brief(t), false);
                                }
                            }
                        });
                }
            }, "QinCookieLogin").start();
    }

    private static void applyToSdk(JSONObject sauth) throws Exception {
        ClassLoader cl = HookInit.gameClassLoader;
        if (cl == null) throw new IllegalStateException("ClassLoader 未就绪");

        Object sdk = Class.forName(SDK_CLASS, false, cl).getMethod("getInst").invoke(null);
        if (sdk == null) {
            throw new IllegalStateException("SDK 还没初始化，请先进一次游戏主界面再试");
        }

        Method setJFSauth = findMethod(sdk, "setJFSauth", String.class, String.class, boolean.class);
        if (setJFSauth != null) {
            setJFSauth.setAccessible(true);
            java.util.Iterator<String> keys = sauth.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                Object value = sauth.opt(key);
                if (value != null) {
                    setJFSauth.invoke(sdk, key, String.valueOf(value), Boolean.FALSE);
                }
            }
        }

        Method setPropStr = findMethod(sdk, "setPropStr", String.class, String.class);
        if (setPropStr != null) {
            setPropStr.setAccessible(true);
            setPropStr.invoke(sdk, "UNISDK_LOGIN_JSON", sauth.toString());

            String accessToken = sauth.optString("access_token", "");
            if (!accessToken.isEmpty()) {
                setPropStr.invoke(sdk, "UNISDK_JF_ACCESS_TOKEN", accessToken);
            }
        }

        try {
            sdk.getClass().getMethod("login").invoke(sdk);
        } catch (Throwable ignored) {
        }
    }

    private static Method findMethod(Object target, String name, Class<?>... params) {
        Class<?> cls = target.getClass();
        while (cls != null && cls != Object.class) {
            try {
                return cls.getDeclaredMethod(name, params);
            } catch (NoSuchMethodException ignored) {
            }
            cls = cls.getSuperclass();
        }
        return null;
    }

    private static void toast(final String msg, final boolean success) {
        Activity act = HookInit.currentActivity;
        if (act != null && !act.isFinishing()) {
            CookieWindow.showToast(act, msg, success);
        }
    }

    private static String brief(Throwable error) {
        Throwable cause = error.getCause() != null ? error.getCause() : error;
        String msg = cause.getMessage();
        return cause.getClass().getSimpleName() + (msg == null ? "" : (" " + msg));
    }
}
