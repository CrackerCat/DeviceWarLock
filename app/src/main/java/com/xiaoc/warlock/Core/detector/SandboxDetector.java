package com.xiaoc.warlock.Core.detector;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.RequiresApi;

import com.xiaoc.warlock.Core.BaseDetector;
import com.xiaoc.warlock.MainActivity;
import com.xiaoc.warlock.Util.WarningBuilder;
import com.xiaoc.warlock.Util.XCommandUtil;
import com.xiaoc.warlock.Util.XLog;
import com.xiaoc.warlock.manager.ServerManager;
import com.xiaoc.warlock.ui.adapter.InfoItem;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class SandboxDetector extends BaseDetector  {
    private static final String TAG = "SandboxDetector";

    public SandboxDetector(Context context, EnvironmentCallback callback) {
        super(context, callback);
    }
    @Override
    public void detect() {
        checkSandbox();
        checkSandboxByActivityCount();
        checkActivityManagerProxy();

    }
    public static ArrayList<Object> choose(Class<?> targetClass, boolean checkInherit) {
        ArrayList<Object> results = new ArrayList<>();
        try {
            // 获取 ActivityThread 实例
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            Method currentActivityThreadMethod = activityThreadClass.getDeclaredMethod("currentActivityThread");
            currentActivityThreadMethod.setAccessible(true);
            Object currentActivityThread = currentActivityThreadMethod.invoke(null);

            // 获取 mActivities
            Field mActivitiesField = activityThreadClass.getDeclaredField("mActivities");
            mActivitiesField.setAccessible(true);
            Map<Object, Object> activities = (Map<Object, Object>) mActivitiesField.get(currentActivityThread);

            // 遍历所有 Activity
            for (Object record : activities.values()) {
                Field activityField = record.getClass().getDeclaredField("activity");
                activityField.setAccessible(true);
                Object activity = activityField.get(record);

                if (activity != null) {
                    if (checkInherit) {
                        // 检查继承关系
                        if (targetClass.isAssignableFrom(activity.getClass())) {
                            results.add(activity);
                        }
                    } else {
                        // 严格检查类型
                        if (activity.getClass() == targetClass) {
                            results.add(activity);
                        }
                    }
                }
            }
        } catch (Exception e) {
            XLog.e(TAG, "Error during memory scan", e);
        }
        return results;
    }
    private void checkSandbox() {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
            XCommandUtil.CommandResult result = XCommandUtil.execute("ps -ef");

            if (!result.isSuccess()) {
                XLog.e(TAG, "ps check error: " + result.getErrorMsg());
                return;
            }

            String output = result.getSuccessMsg();
            //XLog.d("tttg",output);
            String[] lines = output.split("\n");
            int processCount = 0;
            StringBuilder processDetails = new StringBuilder();

            for (String line : lines) {
                if (!line.contains("warlock")) {
                    processCount++;
                    processDetails.append(line).append("\n");
                    XLog.i(TAG, "ps -ef match -> " + line);
                }
            }

            if (processCount > 2) {
                InfoItem warning = new WarningBuilder("checkSandbox", null)
                        .addDetail("check", processDetails.toString().trim())
                        .addDetail("level", "high")
                        .build();
                reportAbnormal(warning);
            } else {
                XLog.d(TAG, "No sandbox detected in process check");
            }
        }
    }
    private void checkSandboxByActivityCount() {
        try {
            ArrayList<Object> activities = choose(Activity.class, true);
            if (activities != null) {
                ArrayList<Object> suspiciousActivities = new ArrayList<>();
                XLog.e("choose", String.valueOf(activities));
                // 过滤掉我们自己的 Activity
                for (Object activity : activities) {
                    String name = activity.getClass().getName();
                    if (!name.equals(MainActivity.class.getName())) {
                        suspiciousActivities.add(activity);
                    }
                }

                // 如果发现可疑的 Activity
                if (suspiciousActivities.size() >= 1) {
                    StringBuilder details = new StringBuilder();
                    for (Object obj : suspiciousActivities) {
                        details.append(obj.getClass().getName()).append("\n");
                    }

                    InfoItem warning = new WarningBuilder("checkSandboxMemory", null)
                            .addDetail("check", "checkSandboxMemory")
                            .addDetail("details", details.toString().trim())
                            .addDetail("level", "high")
                            .build();

                    reportAbnormal(warning);
                }
            }
        } catch (Exception e) {
            XLog.e(TAG, "Error during sandbox memory check", e);
        }
    }
    private void checkActivityManagerProxy() {
        try {
            List<String> abnormalDetails = new ArrayList<>();

            // 方法1: 检查ActivityManagerNative代理
            checkAMNProxy(abnormalDetails);

            // 方法2: 检查ActivityTaskManager代理 (Android 10及以上)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                checkATMProxy(abnormalDetails);
            }

            // 方法3: 检查ActivityManagerService代理
            checkAMSProxy(abnormalDetails);

            if (!abnormalDetails.isEmpty()) {
                StringBuilder details = new StringBuilder();
                for (String detail : abnormalDetails) {
                    details.append(detail).append("\n");
                }

                InfoItem warning = new WarningBuilder("checkActivityManagerProxy", null)
                        .addDetail("check", details.toString().trim())
                        .addDetail("level", "high")
                        .build();

                reportAbnormal(warning);
            }
        } catch (Exception e) {
            XLog.e(TAG, "Activity manager proxy detection failed", e);
        }
    }

    /**
     * 检查是否为系统正常的AIDL代理类
     */
    private boolean isSystemStubProxy(String className) {
        return className.contains("$Stub$Proxy") || // 系统AIDL代理
                className.contains(".Stub$Proxy") ||  // 系统AIDL代理的另一种形式
                className.endsWith("Stub");          // 系统AIDL存根
    }

    /**
     * 检查ActivityManagerNative代理
     */
    private void checkAMNProxy(List<String> abnormalDetails) {
        try {
            Class<?> amnClass = Class.forName("android.app.ActivityManagerNative");
            Method getDefaultMethod = amnClass.getDeclaredMethod("getDefault");
            getDefaultMethod.setAccessible(true);

            Object activityManager = getDefaultMethod.invoke(null);

            if (activityManager != null) {
                String className = activityManager.getClass().getName();

                // 排除系统正常的AIDL代理
                if (!isSystemStubProxy(className)) {
                    boolean isProxy = Proxy.isProxyClass(activityManager.getClass());

                    if (isProxy || className.contains("Proxy") ||
                            className.contains("proxy")) {
                        abnormalDetails.add(String.format("Suspicious AMN Proxy: %s", className));

                        if (isProxy) {
                            Object handler = Proxy.getInvocationHandler(activityManager);
                            if (handler != null) {
                                String handlerClass = handler.getClass().getName();
                                // 检查是否为可疑的代理处理器
                                if (!handlerClass.startsWith("android.") &&
                                        !handlerClass.startsWith("com.android.")) {
                                    abnormalDetails.add(String.format("Suspicious Handler: %s",
                                            handlerClass));
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * 检查ActivityTaskManager代理 (Android 10+)
     */
    @RequiresApi(api = Build.VERSION_CODES.Q)
    private void checkATMProxy(List<String> abnormalDetails) {
        try {
            Class<?> atmClass = Class.forName("android.app.ActivityTaskManager");
            @SuppressLint("BlockedPrivateApi")
            Method getServiceMethod = atmClass.getDeclaredMethod("getService");
            getServiceMethod.setAccessible(true);

            Object taskManager = getServiceMethod.invoke(null);

            if (taskManager != null) {
                String className = taskManager.getClass().getName();

                // 排除系统正常的AIDL代理
                if (!isSystemStubProxy(className)) {
                    boolean isProxy = Proxy.isProxyClass(taskManager.getClass());

                    if (isProxy || className.contains("Proxy") ||
                            className.contains("proxy")) {
                        abnormalDetails.add(String.format("Suspicious ATM Proxy: %s", className));
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * 检查ActivityManagerService代理
     */
    private void checkAMSProxy(List<String> abnormalDetails) {
        try {
            Class<?> activityManagerClass = Class.forName("android.app.ActivityManager");
            Method getServiceMethod = activityManagerClass.getDeclaredMethod("getService");
            getServiceMethod.setAccessible(true);

            Object ams = getServiceMethod.invoke(null);

            if (ams != null) {
                String className = ams.getClass().getName();

                // 排除系统正常的AIDL代理
                if (!isSystemStubProxy(className)) {
                    boolean isProxy = Proxy.isProxyClass(ams.getClass());

                    if (isProxy || className.contains("Proxy") ||
                            className.contains("proxy")) {
                        abnormalDetails.add(String.format("Suspicious AMS Proxy: %s", className));

                        if (isProxy) {
                            Object handler = Proxy.getInvocationHandler(ams);
                            if (handler != null) {
                                String handlerClass = handler.getClass().getName();
                                // 检查是否为可疑的代理处理器
                                if (!handlerClass.startsWith("android.") &&
                                        !handlerClass.startsWith("com.android.")) {
                                    if (handlerClass.contains("parallel") ||
                                            handlerClass.contains("multiple") ||
                                            handlerClass.contains("multi") ||
                                            handlerClass.contains("clone")) {
                                        abnormalDetails.add(String.format("Multi-App Handler: %s",
                                                handlerClass));
                                    } else {
                                        abnormalDetails.add(String.format("Suspicious Handler: %s",
                                                handlerClass));
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

}