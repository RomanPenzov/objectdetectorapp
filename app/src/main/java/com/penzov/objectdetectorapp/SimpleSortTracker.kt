package com.penzov.objectdetectorapp

import kotlin.math.max
import kotlin.math.min

// Моя реализация простого SORT-трекера (без Kalman), с поддержкой IoU и переназначения ID
class SimpleSortTracker {

    private var nextTrackId = 1 // инкремент ID для новых треков
    private val tracks = mutableListOf<TrackedBox>()

    fun update(detections: List<BoundingBox>): List<TrackedBox> {
        val updatedTracks = mutableListOf<TrackedBox>()
        val used = BooleanArray(detections.size)

        // Сопоставляю старые треки с новыми детекциями по IoU
        for (old in tracks) {
            var bestIoU = 0f
            var bestIdx = -1
            for ((i, det) in detections.withIndex()) {
                if (used[i]) continue
                val iou = iou(old, det)
                if (iou > bestIoU) {
                    bestIoU = iou
                    bestIdx = i
                }
            }

            if (bestIdx != -1 && bestIoU > 0.3f) { // порог IoU
                val matched = detections[bestIdx]
                used[bestIdx] = true
                updatedTracks.add(
                    TrackedBox(
                        trackId = old.trackId,
                        x = matched.x,
                        y = matched.y,
                        width = matched.width,
                        height = matched.height,
                        confidence = matched.confidence,
                        classId = matched.classId,
                        label = matched.label
                    )
                )
            }
        }

        // Добавляю новые треки для несопоставленных детекций
        for ((i, det) in detections.withIndex()) {
            if (!used[i]) {
                updatedTracks.add(
                    TrackedBox(
                        trackId = nextTrackId++,
                        x = det.x,
                        y = det.y,
                        width = det.width,
                        height = det.height,
                        confidence = det.confidence,
                        classId = det.classId,
                        label = det.label
                    )
                )
            }
        }

        // Обновляю список активных треков
        tracks.clear()
        tracks.addAll(updatedTracks)

        return updatedTracks
    }

    private fun iou(a: TrackedBox, b: BoundingBox): Float {
        val x1 = max(a.x, b.x)
        val y1 = max(a.y, b.y)
        val x2 = min(a.x2, b.x + b.width)
        val y2 = min(a.y2, b.y + b.height)

        val interArea = max(0f, x2 - x1) * max(0f, y2 - y1)
        val areaA = a.width * a.height
        val areaB = b.width * b.height

        return interArea / (areaA + areaB - interArea + 1e-6f) // чтобы избежать деления на 0
    }
}
