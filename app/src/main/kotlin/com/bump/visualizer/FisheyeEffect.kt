package com.bump.visualizer

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import android.view.View

object FisheyeEffect {
    private const val AGSL = """
        uniform shader inputShader;
        uniform float2 resolution;
        uniform float strength;
        uniform float2 focus;
        uniform float vignette;
        uniform float edgeFade;

        half4 main(float2 coord) {
            float2 uv = coord / resolution;
            float2 p = uv - focus;
            float aspect = resolution.x / max(resolution.y, 1.0);
            p.x *= aspect;
            float r2 = dot(p, p);
            float k = 1.0 + strength * r2;
            float2 warped = p * k;
            warped.x /= aspect;
            float2 sampleCoord = (warped + focus) * resolution;

            if (sampleCoord.x < 0.0 || sampleCoord.x > resolution.x ||
                sampleCoord.y < 0.0 || sampleCoord.y > resolution.y) {
                return half4(0.0);
            }

            half4 color = inputShader.eval(sampleCoord);
            float edge = min(min(uv.x, 1.0 - uv.x), min(uv.y, 1.0 - uv.y));
            float fade = smoothstep(0.0, max(edgeFade, 0.001), edge);
            float dist = distance(uv, focus);
            float vig = 1.0 - vignette * smoothstep(0.25, 0.85, dist);
            color.rgb *= vig;
            color.a *= fade;
            return color;
        }
    """

    fun apply(
        view: View,
        strength: Float = 0.16f,
        focusX: Float = 0.5f,
        focusY: Float = 0.5f,
        vignette: Float = 0.34f,
        edgeFade: Float = 0.08f
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val shader = RuntimeShader(AGSL)
            shader.setFloatUniform("resolution", view.width.toFloat(), view.height.toFloat())
            shader.setFloatUniform("strength", strength)
            shader.setFloatUniform("focus", focusX, focusY)
            shader.setFloatUniform("vignette", vignette)
            shader.setFloatUniform("edgeFade", edgeFade)
            view.setRenderEffect(RenderEffect.createRuntimeShaderEffect(shader, "inputShader"))
        }
    }

    fun clear(view: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) view.setRenderEffect(null)
    }
}
