package com.ghostly.apk.shizuku;

interface IPerformanceService {
    void destroy() = 16777114;
    String setFixedPerformance(boolean enabled) = 1;
    String setPerformance(boolean enabled, int fps) = 2;
}
