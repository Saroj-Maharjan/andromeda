package com.kylecorry.andromeda.bitmaps.operations

import android.graphics.Bitmap

fun Bitmap.applyOperations(
    vararg operations: BitmapOperation,
    recycleOriginal: Boolean = true,
    forceGarbageCollection: Boolean = false
): Bitmap {
    return applyOperations(
        operations.toList(),
        recycleOriginal,
        forceGarbageCollection
    )
}

fun Bitmap.applyOperationsOrNull(
    vararg operations: BitmapOperation,
    recycleOriginal: Boolean = true,
    recycleOriginalOnError: Boolean = true,
    forceGarbageCollection: Boolean = false,
): Bitmap? {
    return try {
        applyOperations(operations.toList(), recycleOriginal, forceGarbageCollection)
    } catch (e: Exception) {
        e.printStackTrace()
        if (recycleOriginalOnError && recycleOriginal) {
            recycle()
        }
        null
    }
}

fun Bitmap.applyOperations(
    operations: List<BitmapOperation>,
    recycleOriginal: Boolean = true,
    forceGarbageCollection: Boolean = false
): Bitmap {
    var current = this
    var last = this
    operations.forEach {
        current = it.execute(current)
        if ((recycleOriginal || last != this) && current != last) {
            last.recycle()
        }
        last = current
    }

    if (forceGarbageCollection) {
        System.gc()
    }
    return current
}