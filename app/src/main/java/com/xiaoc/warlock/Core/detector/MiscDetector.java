package com.xiaoc.warlock.Core.detector;

import android.Manifest;
import android.app.KeyguardManager;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.provider.Settings;
import com.xiaoc.warlock.Core.detector.BootloaderStateChecker;
import android.telephony.TelephonyManager;

import com.xiaoc.warlock.App;
import com.xiaoc.warlock.BuildConfig;
import com.xiaoc.warlock.Core.BaseDetector;
import com.xiaoc.warlock.Util.AppChecker;
import com.xiaoc.warlock.Util.MiscUtil;
import com.xiaoc.warlock.Util.WarningBuilder;

import com.xiaoc.warlock.Util.XFile;
import com.xiaoc.warlock.Util.XLog;
import com.xiaoc.warlock.ui.adapter.InfoItem;

import java.net.NetworkInterface;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MiscDetector extends BaseDetector {
    private final TelephonyManager telephonyManager;
    private String TAG = "MiscDetector";

    public MiscDetector(Context context, EnvironmentCallback callback) {
        super(context, callback);
        telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);

    }

    /**
     * 检测当前设备是否能够正常调用反射
     */
    private void checkReflectionAvailable(){
        try {
            boolean supported =AppChecker.isReflectionSupported();
                if (supported && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P){{
                    InfoItem warning = new WarningBuilder("checkHideApi", null)
                            .addDetail("check", String.valueOf(supported))
                            .addDetail("level", "medium")
                            .build();

                    reportAbnormal(warning);
                }}

        } catch (Exception e) {
        }
    }
    /**
     * 检测App启动时是否会有异常堆栈
     */
    private void checkException(){
        try {
            boolean StackTraceBbnormal =AppChecker.isStackTraceBbnormal();
            if (StackTraceBbnormal){{
                StringBuilder details = new StringBuilder();
                for (String line : AppChecker.getStackTraceBbnormal()) {
                    details.append(line).append("\n");
                }
                InfoItem warning = new WarningBuilder("checkStackTrace", null)
                        .addDetail("check", details.toString().trim())
                        .addDetail("level", "high")
                        .build();

                reportAbnormal(warning);
            }}

        } catch (Exception e) {
        }
    }

    /**
     * 检测当前手机是否有手机卡
     */
    private void checkSimCard() {
        try {
            if (telephonyManager == null) {
                XLog.e(TAG, "TelephonyManager is null");
                return;
            }

            boolean hasActiveSim = false;

            // 对于Android Q及以上版本，检查所有SIM卡槽
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                int phoneCount = telephonyManager.getPhoneCount();
                for (int i = 0; i < phoneCount; i++) {
                    if (telephonyManager.getSimState(i) == TelephonyManager.SIM_STATE_READY) {
                        hasActiveSim = true;
                        break;
                    }
                }
            } else {
                // 对于低版本Android，只检查主卡槽
                hasActiveSim = telephonyManager.getSimState() == TelephonyManager.SIM_STATE_READY;
            }

            // 如果没有可用的SIM卡，报告警告
            if (!hasActiveSim) {
                InfoItem warning = new WarningBuilder("checkSimCard", null)
                        .addDetail("check", "Not Found Sim")
                        .addDetail("level", "medium")
                        .build();

                reportAbnormal(warning);
                XLog.d(TAG, "未检测到可用的SIM卡");
            }

        } catch (SecurityException e) {
            XLog.e(TAG, "缺少必要权限", e);
        } catch (Exception e) {
            XLog.e(TAG, "检查SIM卡状态失败", e);
        }
    }

    /**
     * 检测当前设备是否存在shizuku
     */
    private void checkShizukuFiles() {
        try {
            List<String> foundFiles = new ArrayList<>();

            for (String path : BuildConfig.SHIZUKU_FILES) {
                if (XFile.exists(path)) {
                    foundFiles.add(path);
                    XLog.d(TAG, "发现Shizuku文件: " + path);
                }
            }

            if (!foundFiles.isEmpty()) {
                StringBuilder details = new StringBuilder();
                for (String file : foundFiles) {
                    details.append(file).append("\n");
                }

                InfoItem warning = new WarningBuilder("checkShizuku", null)
                        .addDetail("check", details.toString().trim())
                        .addDetail("level", "low")
                        .build();

                reportAbnormal(warning);
            }
        } catch (Exception e) {
            XLog.e(TAG, "检查Shizuku文件失败", e);
        }
    }
    /**
     * 检测系统代理设置
     * 检查是否设置了HTTP代理，这可能表明设备正在被用于抓包分析
     */
    private void checkProxy() {
        try {
            String host = System.getProperty("http.proxyHost");
            String port = System.getProperty("http.proxyPort");

            if (host != null && !host.isEmpty()) {
                String proxyInfo = host + (port != null ? ":" + port : "");

                InfoItem warning = new WarningBuilder("checkProxy", null)
                        .addDetail("check", proxyInfo)
                        .addDetail("level", "medium")
                        .build();

                reportAbnormal(warning);
                XLog.d(TAG, "检测到代理设置: " + proxyInfo);
            }
        } catch (Exception e) {
            XLog.e(TAG, "检查代理设置失败", e);
        }
    }
    /**
     * 检测USB调试状态
     * 检查ADB调试是否启用，这是一个潜在的安全风险
     */
    private void checkAdbDebug() {
        try {
            int adbEnabled = Settings.Global.getInt(
                    context.getContentResolver(),
                    Settings.Global.ADB_ENABLED,
                    0);

            if (adbEnabled == 1) {
                InfoItem warning = new WarningBuilder("checkAdbDebug", null)
                        .addDetail("check", String.valueOf(true))
                        .addDetail("level", "low")
                        .build();

                reportAbnormal(warning);
                XLog.d(TAG, "USB调试已启用");
            }
        } catch (Exception e) {
            XLog.e(TAG, "检查USB调试状态失败", e);
        }
    }

    /**
     * 检测开发者选项状态
     * 检查开发者选项是否启用，这可能表明设备处于开发测试状态
     */
    private void checkDevelopmentSettings() {
        try {
            int developmentSettingsEnabled = Settings.Global.getInt(
                    context.getContentResolver(),
                    Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
                    0);

            if (developmentSettingsEnabled == 1) {
                InfoItem warning = new WarningBuilder("checkDevelopmentSettings", null)
                        .addDetail("check", String.valueOf(true))
                        .addDetail("level", "low")
                        .build();

                reportAbnormal(warning);
                XLog.d(TAG, "开发者选项已启用");
            }
        } catch (Exception e) {
            XLog.e(TAG, "检查开发者选项状态失败", e);
        }
    }
    /**
     * 检测VPN连接状态
     * 检查设备是否正在使用VPN连接，这可能表明流量正在被重定向或监控
     */
    private void checkVpnConnection() {
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) {
                XLog.e(TAG, "ConnectivityManager is null");
                return;
            }

            // 方法1：通过网络接口检测
            boolean vpnInUse = false;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Network activeNetwork = cm.getActiveNetwork();
                if (activeNetwork != null) {
                    NetworkCapabilities caps = cm.getNetworkCapabilities(activeNetwork);
                    if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                        vpnInUse = true;
                    }
                }
            }

            // 方法2：通过检查VPN接口
            try {
                List<NetworkInterface> networks = Collections.list(NetworkInterface.getNetworkInterfaces());
                for (NetworkInterface network : networks) {
                    if (!vpnInUse && network.getName().startsWith("tun") || network.getName().startsWith("ppp")) {
                        vpnInUse = true;
                        break;
                    }
                }
            } catch (Exception e) {
                XLog.e(TAG, "检查网络接口失败", e);
            }

            if (vpnInUse) {
                InfoItem warning = new WarningBuilder("checkVpn", null)
                        .addDetail("check", String.valueOf(true))
                        .addDetail("level", "medium")
                        .build();

                reportAbnormal(warning);
                XLog.d(TAG, "检测到VPN连接");
            }

        } catch (Exception e) {
            XLog.e(TAG, "检查VPN状态失败", e);
        }
    }
    private void checkLineageOS() {
        try {
            List<String> abnormalDetails = new ArrayList<>();

            // 方法1: 检查系统属性
            String[] lineageProps = {
                    "ro.build.display.id",
                    "ro.lineage.version",
                    "ro.modversion",
                    "ro.lineage.releasetype",
                    "ro.lineage.build.version",
                    "ro.lineage.device",
                    "ro.lineage.release.type"
            };

            for (String prop : lineageProps) {
                String value = MiscUtil.getSystemProperty(prop);
                if (value != null && value.toLowerCase().contains("lineage")) {
                    abnormalDetails.add("Prop: " +  value);
                }
            }

            // 方法2: 检查特征文件
            String[] lineageFiles = {
                    "/system/addon.d",
                    "/system/etc/init/lineage.rc",
                    "/system/etc/permissions/org.lineageos.platform.xml",
                    "/system/framework/lineage-framework.jar",
                    "/system/etc/permissions/org.lineageos.hardware.xml",
                    "/system/etc/permissions/org.lineageos.weather.xml"
            };

            for (String path : lineageFiles) {
                if (XFile.exists(path)) {
                    abnormalDetails.add("File: " + path);
                }
            }

            // 方法3: 检查系统应用
            String[] lineageApps = {
                    "org.lineageos.updater",              // Lineage更新器
                    "org.lineageos.settings",             // Lineage设置
                    "lineageos.platform",                 // Lineage平台
                    "org.lineageos.recorder",             // Lineage录音机
                    "org.lineageos.profiles",             // Lineage配置文件
                    "org.lineageos.setupwizard",          // Lineage设置向导
                    "org.lineageos.snap",                 // Lineage相机
                    "org.lineageos.weather.provider",     // Lineage天气提供者
                    "org.lineageos.audiofx"               // Lineage音效
            };

            PackageManager pm = context.getPackageManager();
            int lineageAppCount = 0;

            for (String packageName : lineageApps) {
                try {
                    pm.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES);
                    lineageAppCount++;
                    if (lineageAppCount <= 3) { // 只记录前3个发现的应用
                        abnormalDetails.add("LineageOS App: " + packageName);
                    }
                } catch (PackageManager.NameNotFoundException ignored) {
                    // 包未安装，继续检查下一个
                }
            }


            // 方法4: 检查系统指纹
            String fingerprint = Build.FINGERPRINT.toLowerCase();
            if (fingerprint.contains("lineage")) {
                abnormalDetails.add("fingerprint: " + fingerprint);
            }

            // 如果发现任何LineageOS特征
            if (!abnormalDetails.isEmpty()) {
                StringBuilder details = new StringBuilder();
                for (String detail : abnormalDetails) {
                    details.append(detail).append("\n");
                }

                InfoItem warning = new WarningBuilder("checkLineageOS", null)
                        .addDetail("check", details.toString().trim())
                        .addDetail("level", "low")
                        .build();

                reportAbnormal(warning);
              //  XLog.d(TAG, "检测到LineageOS系统: " + details);
            }
        } catch (Exception e) {
            XLog.e(TAG, "LineageOS检测失败", e);
        }
    }
    private void checkGoogleDevice() {
        try {
            List<String> abnormalDetails = new ArrayList<>();

            // 方法1: 检查制造商和品牌
            String manufacturer = Build.MANUFACTURER.toLowerCase();
            String brand = Build.BRAND.toLowerCase();
            String model = Build.MODEL.toLowerCase();

            if (manufacturer.contains("google") || brand.contains("google") ||
                    model.contains("pixel") || model.contains("nexus")) {
                abnormalDetails.add(String.format("DeviceInfo: manufacturer=%s, brand=%s, model=%s",
                        manufacturer, brand, model));
            }

            // 方法2: 检查系统属性
            String[] googleProps = {
                    "ro.product.manufacturer",
                    "ro.product.brand",
                    "ro.product.name",
                    "ro.product.device",
                    "ro.build.flavor",
                    "ro.vendor.build.fingerprint",
                    "ro.bootloader"
            };

            for (String prop : googleProps) {
                String value = MiscUtil.getSystemProperty(prop);
                if (value != null && (value.toLowerCase().contains("google") ||
                        value.toLowerCase().contains("pixel") ||
                        value.toLowerCase().contains("nexus"))) {
                    abnormalDetails.add("Prop: "  + value);
                }
            }

            // 方法3: 检查Google特有应用
            String[] googleApps = {
                    "com.google.android.apps.pixelmigrate",// Pixel迁移工具
                    "com.google.android.apps.restore",     // Pixel数据恢复
                    "com.google.android.apps.wellbeing",   // Digital Wellbeing
                    "com.google.android.apps.safetyhub",   // Personal Safety
                    "com.google.android.apps.turbo",       // Device Health Services
                    "com.google.android.as",               // Device Personalization Services
                    "com.google.android.apps.subscriptions.red" // Google One
            };

            PackageManager pm = context.getPackageManager();
            int googleAppCount = 0;

            for (String packageName : googleApps) {
                try {
                    pm.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES);
                    googleAppCount++;
                    if (googleAppCount <= 3) {
                        abnormalDetails.add("Google App: " + packageName);
                    }
                } catch (PackageManager.NameNotFoundException ignored) {
                    // 包未安装，继续检查下一个
                }
            }


            // 方法4: 检查特征文件
            String[] googleFiles = {
                    "/system/etc/sysconfig/pixel.xml",
                    "/system/etc/sysconfig/pixel_experience_2020.xml",
                    "/system/etc/sysconfig/google.xml",
                    "/system/etc/sysconfig/google_build.xml",
                    "/vendor/etc/sensors/sensor_def_google.xml"
            };

            for (String path : googleFiles) {
                if (XFile.exists(path)) {
                    abnormalDetails.add("File: " + path);
                }
            }

            if (!abnormalDetails.isEmpty()) {
                StringBuilder details = new StringBuilder();
                for (String detail : abnormalDetails) {
                    details.append(detail).append("\n");
                }

                InfoItem warning = new WarningBuilder("checkGoogleDevice", null)
                        .addDetail("check", details.toString().trim())
                        .addDetail("level", "low")
                        .build();

                reportAbnormal(warning);
         //       XLog.d(TAG, "检测到Google设备: " + details);
            }
        } catch (Exception e) {
            XLog.e(TAG, "Google设备检测失败", e);
        }
    }
    /**
     * 检测当前位置是否被模拟
     */
    private void checkMockLocation() {
        try {
            LocationManager locationManager =
                    (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);

            if (locationManager != null) {
                // 先检查GPS位置
                Location gpsLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (gpsLocation != null && gpsLocation.isFromMockProvider()) {
                    reportMockLocation("GPS", gpsLocation);
                    return;
                }

                // 再检查网络位置
                Location networkLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                if (networkLocation != null && networkLocation.isFromMockProvider()) {
                    reportMockLocation("Network", networkLocation);
                    return;
                }

                // 如果都没有，检查最后一个已知位置
                Location lastLocation = locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER);
                if (lastLocation != null && lastLocation.isFromMockProvider()) {
                    reportMockLocation("Passive", lastLocation);

                }
            }
        } catch (Exception e) {
            XLog.e(TAG, "Failed to check mock location", e);
        }
    }
    private void reportMockLocation(String provider, Location location) {
        String details = String.format(
                "检测到模拟位置\n提供者: %s\n经度: %f\n纬度: %f",
                provider,
                location.getLongitude(),
                location.getLatitude()
        );

        InfoItem warning = new WarningBuilder("checkMockLocation", null)
                .addDetail("check", details.trim())
                .addDetail("level", "high")
                .build();

        reportAbnormal(warning);
    }
    /**
     * 检测是否安装了具有模拟位置权限的应用
     */
    private void checkAllowMockLocation() {
        try {
            if (!Settings.Secure.getString(context.getContentResolver(),
                    Settings.Secure.ALLOW_MOCK_LOCATION).equals("0")){

                InfoItem warning = new WarningBuilder("checkAllowMockLocation", null)
                        .addDetail("check", "Allow Mock Location")
                        .addDetail("level", "low")
                        .build();

                reportAbnormal(warning);
            }
        } catch (Exception e) {
            XLog.e(TAG, "Failed to check allow mock location setting", e);
        }
    }
    /**
     * 检测是否安装了具有模拟位置权限的应用
     */
    private void checkMockLocationApps() {
        try {
            PackageManager pm = context.getPackageManager();
            List<PackageInfo> packages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS);
            List<String> mockApps = new ArrayList<>();

            for (PackageInfo packageInfo : packages) {
                if (packageInfo.requestedPermissions != null) {
                    for (String permission : packageInfo.requestedPermissions) {
                        if (permission.equals("android.permission.ACCESS_MOCK_LOCATION")) {
                            try {
                                ApplicationInfo appInfo =
                                        pm.getApplicationInfo(packageInfo.packageName, 0);
                                String appName = pm.getApplicationLabel(appInfo).toString();
                                mockApps.add(appName + " (" + packageInfo.packageName + ")");
                            } catch (PackageManager.NameNotFoundException e) {
                                mockApps.add(packageInfo.packageName);
                            }
                            break;
                        }
                    }
                }
            }

            if (!mockApps.isEmpty()) {
                String details = String.join("\n", mockApps);

                InfoItem warning = new WarningBuilder("checkMockLocationApps", null)
                        .addDetail("check", details.trim())
                        .addDetail("level", "low")
                        .build();

                reportAbnormal(warning);
            }

        } catch (Exception e) {
            XLog.e(TAG, "Failed to check mock location apps", e);
        }
    }

    /**
     * 检测设备是否设置了解锁屏幕的密码、PIN、图案或生物识别
     * false 表示未设置
     */
    private void checkScreenLock() {
        KeyguardManager keyguardManager = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        if (keyguardManager != null && !keyguardManager.isKeyguardSecure()) {
            InfoItem warning = new WarningBuilder("checkScreenLock", null)
                    .addDetail("check", String.valueOf(false))
                    .addDetail("level", "low")
                    .build();
            reportAbnormal(warning);
        }
    }

    /**
     * 检测Dalvik虚拟机dex2oat编译标志
     * 检查dalvik.vm.dex2oat-flags属性，如果包含--inline-max-code-units=0可能表明性能优化被禁用
     */
    private void checkDex2OatFlags() {
        try {
            String dex2oatFlags = MiscUtil.getSystemProperty("dalvik.vm.dex2oat-flags");
            if (dex2oatFlags != null && dex2oatFlags.contains("--inline-max-code-units=0")) {
                InfoItem warning = new WarningBuilder("checkDex2OatFlags", null)
                        .addDetail("check", dex2oatFlags)
                        .addDetail("level", "medium")
                        .build();

                reportAbnormal(warning);
               // XLog.d(TAG, "检测到异常的dex2oat编译标志: " + dex2oatFlags);
            }
        } catch (Exception e) {
            XLog.e(TAG, "检查dex2oat标志失败", e);
        }
    }

    @Override
    public void detect() {
        checkReflectionAvailable();
        checkException();
        checkSimCard();
        checkShizukuFiles();
        checkProxy();
        checkAdbDebug();
        checkDevelopmentSettings();
        checkVpnConnection();
        checkGoogleDevice();
        checkLineageOS();
        checkMockLocation();
        checkAllowMockLocation();
        checkMockLocationApps();
        checkScreenLock();
        checkDex2OatFlags();
    }
}
