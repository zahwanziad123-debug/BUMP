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

        half4 main(float2 coord) {
            float2 uv = coord / resolution;
            float2 p = uv * 2.0 - 1.0;
            float r2 = dot(p, p);
            float k = 1.0 + strength * r2;
            float2 warped = p * k;
            float2 sampleCoord = (warped + 1.0) * 0.5 * resolution;
            if (sampleCoord.x < 0.0 || sampleCoord.x > resolution.x ||
                sampleCoord.y < 0.0 || sampleCoord.y > resolution.y) {
                return half4(0.0);
            }
            return inputShader.eval(sampleCoord);
        }
    """

    fun apply(view: View, strength: Float = 0.16f) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val shader = RuntimeShader(AGSL)
            shader.setFloatUniform("resolution", view.width.toFloat(), view.height.toFloat())
            shader.setFloatUniform("strength", strength)
            view.setRenderEffect(RenderEffect.createRuntimeShaderEffect(shader, "inputShader"))
        }
    }

    fun clear(view: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            view.setRenderEffect(null)
        }
    }
}
