package com.kylecorry.andromeda.pdf

import com.kylecorry.andromeda.core.units.PixelCoordinate
import com.kylecorry.sol.units.Coordinate
import java.io.InputStream
import kotlin.math.absoluteValue

class GeospatialPDFParser {

    private val pdfParser = PDFParser()

    fun parse(pdf: InputStream): GeospatialPDFMetadata? {
        val objects = pdfParser.parse(pdf, ignoreStreams = true)

        // TODO: Support multiple pages - pages are linked to viewports through the VP array
        val page = getPage(objects) ?: return null
        val viewports = getViewports(objects)

        for (viewport in viewports) {
            val measure = getObject(viewport["/measure"] ?: "", objects) ?: continue
            if (!isGeoMeasure(measure)) {
                return null
            }
            val gcs = getObject(measure["/gcs"] ?: "", objects)
            return getMetadata(page, viewport, measure, gcs) ?: continue
        }

        return null
    }

    private fun getArray(property: String): List<String> {
        val matches = arrayRegex.find(property) ?: return emptyList()
        if (matches.groupValues.size < 2) {
            return emptyList()
        }

        return matches.groupValues[1].split(" ")
    }


    private fun getMetadata(
        page: PDFObject,
        viewport: PDFObject,
        measure: PDFObject,
        gcs: PDFObject?
    ): GeospatialPDFMetadata? {

        val mediabox = getArray(page["/mediabox"] ?: "").map { it.toDouble() }
        val bbox = getArray(viewport["/bbox"] ?: "").map { it.toDouble() }
        var lpts = getArray(measure["/lpts"] ?: "").map { it.toDouble() }
        val gpts = getArray(measure["/gpts"] ?: "").map { it.toDouble() }

        if (mediabox.size < 4 || bbox.size < 4) {
            return null
        }

        if (lpts.size < gpts.size) {
            lpts = listOf(0.0, 1.0, 0.0, 0.0, 1.0, 0.0, 1.0, 1.0)
        }
        val gcsValue = gcs?.get("/wkt") ?: ""

        val pageHeight = mediabox[3]

        // left, top, right, left
        val left = bbox[0]
        val top = pageHeight - bbox[1]
        val width = (bbox[2] - bbox[0]).absoluteValue
        val height = (bbox[3] - bbox[1]).absoluteValue

        val coordinates = mutableListOf<Pair<PixelCoordinate, Coordinate>>()

        for (i in 1 until gpts.size step 2) {
            val latitude = gpts[i - 1]
            val longitude = gpts[i]

            val pctY = lpts[i]
            val pctX = lpts[i - 1]

            val y = pctY * height + top
            val x = pctX * width + left

            coordinates.add(
                PixelCoordinate(x.toFloat(), y.toFloat()) to Coordinate(
                    latitude,
                    longitude
                )
            )
        }

        return GeospatialPDFMetadata(coordinates, getProjection(gcsValue))
    }

    private fun getProjection(gcs: String): ProjectedCoordinateSystem? {
        val datumName = getMatch(datumNameRegex, gcs) ?: return null
        val spheroidMatches = spheroidRegex.find(gcs)?.groupValues ?: return null
        val spheroid =
            Spheroid(spheroidMatches[1], spheroidMatches[2].toFloat(), spheroidMatches[3].toFloat())
        val datum = Datum(datumName, spheroid)

        val primeMeridian = getMatch(primeMeridianRegex, gcs)?.toDouble() ?: return null
        val projection = getMatch(projectionRegex, gcs) ?: return null

        val geog = GeographicCoordinateSystem(datum, primeMeridian)
        return ProjectedCoordinateSystem(geog, projection)
    }

    private fun getMatch(regex: Regex, text: String): String? {
        val matches = regex.find(text) ?: return null
        return matches.groupValues[1]
    }

    private fun getObject(id: String, objects: List<PDFObject>): PDFObject? {
        val matches = idRegex.find(id) ?: return null
        if (matches.groupValues.size >= 2) {
            return objects.firstOrNull { it.id == matches.groupValues[1] }
        }
        return null
    }

    private fun getViewports(objects: List<PDFObject>): List<PDFObject> {
        return objects.filter {
            "/viewport".contentEquals(it["/type"], ignoreCase = true)
        }
    }

    private fun getPage(objects: List<PDFObject>): PDFObject? {
        return objects.firstOrNull {
            "/page".contentEquals(it["/type"], ignoreCase = true)
        }
    }

    private fun isGeoMeasure(measure: PDFObject): Boolean {
        return measure["/subtype"]?.contentEquals("/geo", ignoreCase = true) ?: false
    }

    companion object {
        private val idRegex = Regex("(\\d+ \\d+)")
        private val arrayRegex = Regex("\\[(.*)]")

        private val datumNameRegex = Regex("DATUM\\[\"([\\w\\s/\\d]+)\"")
        private val spheroidRegex =
            Regex("SPHEROID\\[\"([\\w\\s/\\d]+)\",(-?\\d+(?:[.,]\\d+)?),(-?\\d+(?:[.,]\\d+)?)")
        private val primeMeridianRegex = Regex("PRIMEM\\[\"[\\w\\s/\\d]+\",(-?\\d+(?:[.,]\\d+)?)")
        private val projectionRegex = Regex("PROJECTION\\[\"([\\w\\s/\\d]+)\"")

    }


}