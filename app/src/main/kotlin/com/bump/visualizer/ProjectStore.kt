package com.bump.visualizer

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class BumpProject(val words: List<LyricWord>, val mode: VisualMode)

object ProjectStore {
    private const val PREFS = "bump_project"
    private const val KEY = "project_json"

    fun save(context: Context, words: List<LyricWord>, mode: VisualMode) {
        val root = JSONObject()
        root.put("version", 1)
        root.put("mode", mode.name)
        val array = JSONArray()
        words.forEach { w ->
            array.put(JSONObject().apply {
                put("text", w.text)
                put("startMs", w.startMs)
                put("endMs", w.endMs)
                put("x", w.x)
                put("y", w.y)
                put("z", w.z)
                put("rotationX", w.rotationX)
                put("rotationY", w.rotationY)
                put("scale", w.scale)
                val frames = JSONArray()
                w.keyframes.forEach { k ->
                    frames.put(JSONObject().apply {
                        put("timeMs", k.timeMs)
                        put("x", k.x); put("y", k.y); put("z", k.z)
                        put("rotationX", k.rotationX); put("rotationY", k.rotationY)
                        put("scale", k.scale); put("opacity", k.opacity)
                    })
                }
                put("keyframes", frames)
            })
        }
        root.put("words", array)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, root.toString()).apply()
    }

    fun load(context: Context): BumpProject? {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return null
        return runCatching {
            val root = JSONObject(raw)
            val mode = runCatching {
                VisualMode.valueOf(root.optString("mode", "SHIP"))
            }.getOrDefault(VisualMode.SHIP)
            val array = root.optJSONArray("words") ?: JSONArray()
            val words = buildList {
                for (i in 0 until array.length()) {
                    val o = array.getJSONObject(i)
                    add(LyricWord(
                        text = o.optString("text"),
                        startMs = o.optLong("startMs"),
                        endMs = o.optLong("endMs"),
                        x = o.optDouble("x").toFloat(),
                        y = o.optDouble("y").toFloat(),
                        z = o.optDouble("z").toFloat(),
                        rotationX = o.optDouble("rotationX").toFloat(),
                        rotationY = o.optDouble("rotationY").toFloat(),
                        scale = o.optDouble("scale", 1.0).toFloat(),
                        keyframes = mutableListOf<LyricKeyframe>().apply {
                            val frames = o.optJSONArray("keyframes") ?: JSONArray()
                            for (j in 0 until frames.length()) {
                                val k = frames.getJSONObject(j)
                                add(LyricKeyframe(
                                    k.optLong("timeMs"),
                                    k.optDouble("x").toFloat(),
                                    k.optDouble("y").toFloat(),
                                    k.optDouble("z").toFloat(),
                                    k.optDouble("rotationX").toFloat(),
                                    k.optDouble("rotationY").toFloat(),
                                    k.optDouble("scale", 1.0).toFloat(),
                                    k.optDouble("opacity", 1.0).toFloat()
                                ))
                            }
                        }
                    ))
                }
            }
            BumpProject(words, mode)
        }.getOrNull()
    }
}
