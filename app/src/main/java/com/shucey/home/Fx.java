package com.shucey.home;

import android.annotation.TargetApi;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.os.Build;
import android.os.SystemClock;

/**
 * GPU シェーダー（AGSL, Android 13+）と、アニメーションの省電力制御。
 * 触ってから数秒間だけ動かし、放置中は静止させる（ホーム画面で電池を使い続けないため）。
 */
final class Fx {
    static final boolean SHADERS = Build.VERSION.SDK_INT >= 33;
    private static final long AWAKE_MS = 9000;
    private static final long T0 = SystemClock.uptimeMillis();
    private static long awakeUntil;
    private static boolean resumed;

    static void setResumed(boolean r) { resumed = r; if (r) wake(); }
    static void wake() { awakeUntil = SystemClock.uptimeMillis() + AWAKE_MS; }
    static boolean awake() { return resumed && SystemClock.uptimeMillis() < awakeUntil; }
    static float time() { return (SystemClock.uptimeMillis() - T0) / 1000f; }

    /** コンパイルできない端末・Android 12 以前では null（呼び出し側は表示を省く）。 */
    static Shader shader(String src) {
        if (!SHADERS) return null;
        try {
            return create(src);
        } catch (Throwable t) {
            return null;
        }
    }

    @TargetApi(33)
    private static Shader create(String src) { return new RuntimeShader(src); }

    /** uniform を設定する（配列形式に統一。float / float2 / float3 いずれも可）。 */
    @TargetApi(33)
    static void set(Shader sh, String name, float... v) { ((RuntimeShader) sh).setFloatUniform(name, v); }

    /** 色を float3 の uniform として渡す。 */
    static void color(Shader sh, String name, int c) {
        set(sh, name, ((c >> 16) & 0xFF) / 255f, ((c >> 8) & 0xFF) / 255f, (c & 0xFF) / 255f);
    }

    // ---------------------------------------------------------------- 背景のエネルギー場
    static final String FIELD =
            "uniform float2 uRes;\n" +
            "uniform float uTime;\n" +
            "uniform float2 uTap;\n" +
            "uniform float uWave;\n" +      // 衝撃波の進行 0..1（負なら無し）
            "uniform float uEnergy;\n" +
            "uniform float3 uColor;\n" +
            "half4 main(float2 fc) {\n" +
            "  float2 uv = fc / uRes.y;\n" +
            "  float t = uTime * 0.22;\n" +
            "  float n = sin(uv.x * 3.1 + t) + sin(uv.y * 4.3 - t * 1.3) + sin((uv.x + uv.y) * 5.2 + t * 0.7)\n" +
            "          + sin(length(uv - float2(0.3, 0.6)) * 7.0 - t * 1.7);\n" +
            "  n = n / 8.0 + 0.5;\n" +
            "  float fog = smoothstep(0.38, 0.95, n) * (0.07 + 0.17 * uEnergy);\n" +
            "  float ring = 0.0;\n" +
            "  if (uWave >= 0.0) {\n" +
            "    float d = distance(fc, uTap) / uRes.y;\n" +
            "    float x = (d - uWave * 1.5) * 16.0;\n" +
            "    ring = exp(-x * x) * (1.0 - uWave) * 0.75;\n" +
            "  }\n" +
            "  float a = clamp(fog + ring, 0.0, 1.0);\n" +
            "  float3 c = mix(uColor, float3(1.0), ring * 0.5);\n" +
            "  return half4(half3(c * a), half(a));\n" +
            "}\n";

    // ---------------------------------------------------------------- 炎
    static final String FLAME =
            "uniform float2 uRes;\n" +
            "uniform float uTime;\n" +
            "uniform float uSeed;\n" +
            "uniform float3 uColor;\n" +
            "float hash(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }\n" +
            "float noise(float2 p) {\n" +
            "  float2 i = floor(p); float2 f = fract(p);\n" +
            "  f = f * f * (3.0 - 2.0 * f);\n" +
            "  return mix(mix(hash(i), hash(i + float2(1.0, 0.0)), f.x),\n" +
            "             mix(hash(i + float2(0.0, 1.0)), hash(i + float2(1.0, 1.0)), f.x), f.y);\n" +
            "}\n" +
            "float fbm(float2 p) {\n" +
            "  float v = 0.0; float a = 0.5;\n" +
            "  for (int i = 0; i < 4; i++) { v += a * noise(p); p = p * 2.03 + 11.7; a *= 0.5; }\n" +
            "  return v;\n" +
            "}\n" +
            "half4 main(float2 fc) {\n" +
            "  float2 uv = fc / uRes;\n" +
            "  float y = 1.0 - uv.y;\n" +                    // 0 = 根元
            "  float x = (uv.x - 0.5) * 2.0;\n" +
            "  float n = fbm(float2(x * 2.2 + uSeed, y * 2.6 - uTime * 2.4));\n" +
            "  float sway = (fbm(float2(uSeed, uTime * 0.9 + y)) - 0.5) * 0.5 * y;\n" +
            "  float body = abs(x - sway) * (1.35 + y * 1.5) + y * 1.2 - n * 0.95;\n" +
            "  float shape = clamp(1.0 - smoothstep(0.0, 0.85, body), 0.0, 1.0);\n" +
            "  float core = pow(shape, 2.6);\n" +
            "  float3 c = mix(uColor, float3(1.0, 1.0, 0.96), core * 0.8);\n" +
            "  float glow = exp(-(x * x * 3.0 + (y - 0.25) * (y - 0.25) * 5.0)) * 0.22;\n" +
            "  float a = clamp(shape + glow, 0.0, 1.0);\n" +
            "  return half4(half3(c * a), half(a));\n" +
            "}\n";

    // ---------------------------------------------------------------- 電子軌道（確率密度の雲）
    // 水素型軌道の角度依存 × 簡略化した動径分布を、視線方向に積分して描く。
    static final String ORBITAL =
            "uniform float2 uRes;\n" +
            "uniform float uL;\n" +          // 0 = s, 1 = p, 2 = d, 3 = f
            "uniform float2 uRot;\n" +       // yaw, pitch
            "uniform float3 uColor;\n" +
            "uniform float3 uColor2;\n" +
            "float psi(float3 q) {\n" +
            "  float r = length(q) + 0.0001;\n" +
            "  float3 d = q / r;\n" +
            "  if (uL < 0.5) return 1.9 * exp(-1.6 * r);\n" +
            "  if (uL < 1.5) return 5.2 * r * exp(-2.0 * r) * d.y;\n" +
            "  if (uL < 2.5) return 16.0 * r * r * exp(-2.4 * r) * (d.x * d.x - d.y * d.y);\n" +
            "  return 150.0 * r * r * r * exp(-2.9 * r) * d.x * d.y * d.z;\n" +
            "}\n" +
            "half4 main(float2 fc) {\n" +
            "  float2 p = (fc - 0.5 * uRes) / (0.5 * min(uRes.x, uRes.y)) * 2.3;\n" +
            "  float cy = cos(uRot.x); float sy = sin(uRot.x);\n" +
            "  float cp = cos(uRot.y); float sp = sin(uRot.y);\n" +
            "  float pos = 0.0; float neg = 0.0;\n" +
            "  for (int i = 0; i < 30; i++) {\n" +
            "    float z = -2.3 + 4.6 * (float(i) + 0.5) / 30.0;\n" +
            "    float3 q = float3(p.x, p.y * cp - z * sp, p.y * sp + z * cp);\n" +
            "    q = float3(q.x * cy + q.z * sy, q.y, -q.x * sy + q.z * cy);\n" +
            "    float v = psi(q);\n" +
            "    float dens = v * v * (4.6 / 30.0);\n" +
            "    if (v >= 0.0) pos += dens; else neg += dens;\n" +
            "  }\n" +
            "  float a = 1.0 - exp(-(pos + neg) * 1.9);\n" +
            "  float3 c = (uColor * pos + uColor2 * neg) / (pos + neg + 0.0001);\n" +
            "  c = mix(c, float3(1.0), a * a * 0.35);\n" +
            "  return half4(half3(c * a), half(a));\n" +
            "}\n";
}
