package com.qinxin.cookie;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class HookInit implements IXposedHookLoadPackage {

    private static final String TARGET_PACKAGE = "com.netease.x19";

    public static volatile Context appContext;
    public static volatile Activity currentActivity;
    public static volatile ClassLoader gameClassLoader;

    private static volatile boolean uiHooked = false;
    private static volatile boolean autoLoginStarted = false;

    private static CookieWindow cookieWindow;
    private static FloatingBall floatingBall;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!TARGET_PACKAGE.equals(lpparam.packageName)) return;
        if (lpparam.appInfo == null) return;
        if (TextUtils.isEmpty(lpparam.appInfo.nativeLibraryDir)) return;
        if (!TextUtils.equals(lpparam.packageName, lpparam.processName)) return;

        gameClassLoader = lpparam.classLoader;
        hookApplicationAttach(lpparam);
    }

    private void hookApplicationAttach(final XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> clazz = lpparam.classLoader.loadClass("com.netease.android.protect.StubApp");
            XposedHelpers.findAndHookMethod(clazz, "attachBaseContext", Context.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Context ctx = (Context) param.args[0];
                        if (ctx == null) return;
                        appContext = ctx.getApplicationContext();
                        if (appContext == null) appContext = ctx;
                        hookMainActivityAndHotfix(lpparam);
                    }
                });
        } catch (Throwable ignored) {
        }
    }

    private void hookMainActivityAndHotfix(XC_LoadPackage.LoadPackageParam lpparam) {
        if (uiHooked) return;
        uiHooked = true;

        try {
            XposedHelpers.findAndHookMethod(
                "com.mojang.minecraftpe.MainActivity", lpparam.classLoader,
                "onCreate", Bundle.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        final Activity activity = (Activity) param.thisObject;
                        appContext = activity.getApplicationContext();
                        currentActivity = activity;

                        activity.getWindow().getDecorView().post(new Runnable() {
                                @Override
                                public void run() {
                                    onGameReady(activity);
                                }
                            });
                    }
                });

            XposedHelpers.findAndHookMethod(
                "com.mojang.minecraftpe.MainActivity", lpparam.classLoader,
                "onResume", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        final Activity activity = (Activity) param.thisObject;
                        if (activity == null) return;
                        appContext = activity.getApplicationContext();
                        currentActivity = activity;
                        activity.getWindow().getDecorView().post(new Runnable() {
                                @Override
                                public void run() {
                                    onGameReady(activity);
                                }
                            });
                    }
                });
        } catch (Throwable ignored) {
        }
    }

    private void onGameReady(final Activity activity) {
        if (activity == null || activity.isFinishing()) return;

        if (cookieWindow == null || cookieWindow.getActivity() != activity) {
            cookieWindow = new CookieWindow(activity);
        }

        if (floatingBall == null || floatingBall.getActivity() != activity) {
            floatingBall = new FloatingBall(activity, new Runnable() {
                    @Override
                    public void run() {
                        if (cookieWindow == null) return;
                        if (cookieWindow.isShowing()) {
                            cookieWindow.hide();
                        } else {
                            cookieWindow.show();
                        }
                    }
                });
        }
        floatingBall.attach();

        if (!autoLoginStarted) {
            autoLoginStarted = true;
            tryAutoLogin();
        }
    }

    private void tryAutoLogin() {
        if (appContext == null) return;
        if (!CookieStore.isAutoLoginEnabled(appContext)) return;
        final String cookie = CookieStore.readCookie(appContext);
        if (cookie == null || cookie.isEmpty()) return;

        new Thread(new Runnable() {
                @Override
                public void run() {
                    for (int i = 0; i < 40; i++) {
                        if (isSdkReady()) {
                            CookieLogin.login(cookie);
                            return;
                        }
                        try {
                            Thread.sleep(1500);
                        } catch (InterruptedException e) {
                            return;
                        }
                    }
                }
            }, "QinAutoLogin").start();
    }

    private boolean isSdkReady() {
        try {
            ClassLoader cl = gameClassLoader;
            if (cl == null) return false;
            Class<?> cls = Class.forName("com.netease.ntunisdk.SdkNetease", false, cl);
            Object inst = cls.getMethod("getInst").invoke(null);
            return inst != null;
        } catch (Throwable t) {
            return false;
        }
    }
}
