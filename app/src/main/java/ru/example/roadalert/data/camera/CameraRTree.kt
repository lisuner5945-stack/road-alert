package ru.example.roadalert.data.camera

import ru.example.roadalert.detection.BoundingBox
import ru.example.roadalert.domain.model.CameraPoint

/**
 * Статический packed R-tree (STR bulk loading) над точками камер.
 *
 * База камер меняется только при обновлении, поэтому дерево строится один раз
 * и дальше работает только на чтение — это быстрее и экономнее по батарее,
 * чем SQL-запрос на каждый GPS-fix (раз в секунду).
 */
class CameraRTree private constructor(
    private val points: List<CameraPoint>,
    private val nodes: List<Node>,
    private val rootIndex: Int,
) {

    private class Node(
        val bounds: BoundingBox,
        /** Индексы дочерних узлов или, для листа, диапазон точек [from, to). */
        val children: IntArray?,
        val from: Int,
        val to: Int,
    ) {
        val isLeaf: Boolean get() = children == null
    }

    val size: Int get() = points.size

    /** Все камеры, попадающие в bounding box. */
    fun search(box: BoundingBox): List<CameraPoint> {
        if (points.isEmpty()) return emptyList()
        val result = ArrayList<CameraPoint>()
        val stack = ArrayDeque<Int>()
        stack.addLast(rootIndex)
        while (stack.isNotEmpty()) {
            val node = nodes[stack.removeLast()]
            if (!node.bounds.intersects(box)) continue
            if (node.isLeaf) {
                for (i in node.from until node.to) {
                    val point = points[i]
                    if (box.contains(point.latitude, point.longitude)) result += point
                }
            } else {
                node.children!!.forEach { stack.addLast(it) }
            }
        }
        return result
    }

    companion object {

        private const val NODE_CAPACITY = 16

        fun build(cameras: List<CameraPoint>): CameraRTree {
            if (cameras.isEmpty()) {
                return CameraRTree(emptyList(), listOf(Node(EMPTY_BOX, null, 0, 0)), 0)
            }

            // STR: сортируем по широте, режем на вертикальные полосы, внутри сортируем по долготе.
            val sliceCount = Math.max(1, Math.ceil(Math.sqrt(cameras.size.toDouble() / NODE_CAPACITY)).toInt())
            val sliceSize = Math.max(1, Math.ceil(cameras.size.toDouble() / sliceCount).toInt())
            val ordered = ArrayList<CameraPoint>(cameras.size)
            cameras.sortedBy { it.latitude }
                .chunked(sliceSize)
                .forEach { slice -> ordered += slice.sortedBy { it.longitude } }

            val nodes = ArrayList<Node>()
            var levelIndices = ArrayList<Int>()

            var index = 0
            while (index < ordered.size) {
                val to = Math.min(index + NODE_CAPACITY, ordered.size)
                nodes += Node(boundsOf(ordered, index, to), null, index, to)
                levelIndices += nodes.lastIndex
                index = to
            }

            while (levelIndices.size > 1) {
                val parents = ArrayList<Int>()
                var i = 0
                while (i < levelIndices.size) {
                    val to = Math.min(i + NODE_CAPACITY, levelIndices.size)
                    val children = levelIndices.subList(i, to).toIntArray()
                    val bounds = children
                        .map { nodes[it].bounds }
                        .reduce(::union)
                    nodes += Node(bounds, children, 0, 0)
                    parents += nodes.lastIndex
                    i = to
                }
                levelIndices = parents
            }

            return CameraRTree(ordered, nodes, levelIndices.first())
        }

        private val EMPTY_BOX = BoundingBox(0.0, 0.0, 0.0, 0.0)

        private fun boundsOf(points: List<CameraPoint>, from: Int, to: Int): BoundingBox {
            var minLat = Double.MAX_VALUE
            var minLon = Double.MAX_VALUE
            var maxLat = -Double.MAX_VALUE
            var maxLon = -Double.MAX_VALUE
            for (i in from until to) {
                val p = points[i]
                if (p.latitude < minLat) minLat = p.latitude
                if (p.latitude > maxLat) maxLat = p.latitude
                if (p.longitude < minLon) minLon = p.longitude
                if (p.longitude > maxLon) maxLon = p.longitude
            }
            return BoundingBox(minLat, minLon, maxLat, maxLon)
        }

        private fun union(a: BoundingBox, b: BoundingBox) = BoundingBox(
            minLatitude = Math.min(a.minLatitude, b.minLatitude),
            minLongitude = Math.min(a.minLongitude, b.minLongitude),
            maxLatitude = Math.max(a.maxLatitude, b.maxLatitude),
            maxLongitude = Math.max(a.maxLongitude, b.maxLongitude),
        )
    }
}
