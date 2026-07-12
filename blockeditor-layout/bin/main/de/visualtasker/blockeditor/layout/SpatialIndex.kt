package de.visualtasker.blockeditor.layout

import de.visualtasker.blockeditor.domain.Rect

class SpatialIndex<T>(
    private val cellSize: Float = 128f,
) {
    private val buckets = mutableMapOf<Long, MutableList<T>>()
    private val itemBounds = mutableMapOf<T, Rect>()
    private val queryBuffer = ArrayList<T>(32)

    fun clear() {
        buckets.clear()
        itemBounds.clear()
        queryBuffer.clear()
    }

    fun insert(item: T, bounds: Rect) {
        itemBounds[item] = bounds
        forEachCell(bounds) { key ->
            buckets.getOrPut(key) { mutableListOf() }.add(item)
        }
    }

    fun query(bounds: Rect, out: MutableList<T> = queryBuffer): List<T> {
        out.clear()
        val seen = mutableSetOf<T>()
        forEachCell(bounds) { key ->
            buckets[key]?.forEach { item ->
                if (seen.add(item)) out.add(item)
            }
        }
        return out
    }

    fun queryPoint(x: Float, y: Float, radius: Float = 0f): List<T> =
        query(Rect(x - radius, y - radius, radius * 2f, radius * 2f))

    private inline fun forEachCell(bounds: Rect, action: (Long) -> Unit) {
        val minCx = cellX(bounds.x)
        val maxCx = cellX(bounds.right)
        val minCy = cellY(bounds.y)
        val maxCy = cellY(bounds.bottom)
        var cy = minCy
        while (cy <= maxCy) {
            var cx = minCx
            while (cx <= maxCx) {
                action(pack(cx, cy))
                cx++
            }
            cy++
        }
    }

    private fun cellX(x: Float): Int = kotlin.math.floor(x / cellSize).toInt()
    private fun cellY(y: Float): Int = kotlin.math.floor(y / cellSize).toInt()
    private fun pack(cx: Int, cy: Int): Long = (cx.toLong() shl 32) xor (cy.toLong() and 0xFFFFFFFFL)
}
