package com.xiaoc.warlock.Core.detector;

import android.content.Context;
import android.os.Debug;

import com.xiaoc.warlock.Core.BaseDetector;
import com.xiaoc.warlock.Util.WarningBuilder;
import com.xiaoc.warlock.Util.XLog;
import com.xiaoc.warlock.ui.adapter.InfoItem;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class HookDetector extends BaseDetector {
    private static final String MAPS_FILE = "/proc/self/maps";
    private static final String RWX_PERMISSION = "rwx";  // 可读可写可执行权限标记
    @Override
    public void detect() {
        isPtraceAttached();
        hasTracerPid();
        isDebuggerConnected();
        hasRWXSegments();
    }
    public static class SuspiciousSegment {
        public String address;    // 内存地址范围
        public String permission; // 权限
        public String path;       // 映射文件路径

        public SuspiciousSegment(String address, String permission, String path) {
            this.address = address;
            this.permission = permission;
            this.path = path;
        }

        @Override
        public String toString() {
            return String.format("Address: %s, Permission: %s, Path: %s",
                    address, permission, path);
        }
    }

    public HookDetector(Context context, EnvironmentCallback callback) {
        super(context, callback);
    }


    public void isPtraceAttached() {
        try {
            // 尝试自我附加ptrace
            Process process = Runtime.getRuntime().exec("ptrace -p " + android.os.Process.myPid());
            int exitValue = process.waitFor();
            if (exitValue != 0){
                InfoItem warning = new WarningBuilder("checkPtrace", null)
                        .addDetail("check", String.valueOf(true))
                        .addDetail("level", "high")
                        .build();
                reportAbnormal(warning);
            }

        } catch (Exception e) {

        }
    }
    public void hasTracerPid() {
        try {
            BufferedReader reader = new BufferedReader(new FileReader("/proc/self/status"));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("TracerPid:")) {
                    int tracerPid = Integer.parseInt(line.substring(10).trim());
                    if (tracerPid != 0){
                        InfoItem warning = new WarningBuilder("checkHasTracerPid", null)
                                .addDetail("check", String.valueOf(true))
                                .addDetail("level", "high")
                                .build();
                        reportAbnormal(warning);
                    }

                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public void isDebuggerConnected() {
        boolean result =Debug.isDebuggerConnected();
        if (result){
            InfoItem warning = new WarningBuilder("checkDebuggerConnected", null)
                    .addDetail("check", String.valueOf(true))
                    .addDetail("level", "high")
                    .build();
            reportAbnormal(warning);
        }

    }
    /**
     * 检查是否存在可读可写可执行的内存段
     * @return 如果发现RWX段返回true
     */
    public  void hasRWXSegments() {
        try {
            List<SuspiciousSegment> segments = findRWXSegments();
            XLog.i("not data?");
            if (!segments.isEmpty()){
                InfoItem warning = new WarningBuilder("checkRWXSegments", null)
                        .addDetail("check", String.valueOf(true))
                        .addDetail("level", "high")
                        .build();
                reportAbnormal(warning);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 查找所有可读可写可执行的内存段
     * @return 可疑段列表
     */
    public static List<SuspiciousSegment> findRWXSegments() {
        List<SuspiciousSegment> suspiciousSegments = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(MAPS_FILE))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // maps文件的每一行格式：
                // address           perms offset  dev   inode      pathname
                // 00400000-00452000 r-xp 00000000 08:02 173521      /usr/bin/dbus-daemon

                String[] parts = line.split("\\s+");
                if (parts.length < 2) continue;

                String address = parts[0];
                String permission = parts[1];

                // 获取映射文件路径（如果存在）
                String path = parts.length > 5 ? parts[parts.length - 1] : "anonymous";

                // 检查是否具有rwx权限
                if (permission.equals(RWX_PERMISSION)) {
                    suspiciousSegments.add(new SuspiciousSegment(address, permission, path));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return suspiciousSegments;
    }

}
