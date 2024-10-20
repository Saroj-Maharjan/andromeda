package com.kylecorry.andromeda.gpx

import android.graphics.Color
import android.text.TextUtils
import com.kylecorry.andromeda.core.toDoubleCompat
import com.kylecorry.andromeda.core.toFloatCompat
import com.kylecorry.andromeda.core.toLongCompat
import com.kylecorry.andromeda.core.tryOrDefault
import com.kylecorry.andromeda.core.ui.Colors
import com.kylecorry.andromeda.xml.XMLConvert
import com.kylecorry.andromeda.xml.XMLNode
import com.kylecorry.sol.units.Coordinate
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant

object GPXParser {

    private val validWaypointTags = arrayOf("wpt", "trkpt", "rtept")

    fun toGPX(data: GPXData, creator: String, stream: OutputStream) {
        val children = mutableListOf<XMLNode>()
        for (waypoint in data.waypoints) {
            children.add(toXML(waypoint, "wpt"))
        }

        for (track in data.tracks) {
            children.add(toXML(track))
        }

        for (route in data.routes) {
            children.add(toXML(route))
        }

        val gpx = XMLNode(
            "gpx", mapOf(
                "version" to "1.1",
                "creator" to creator,
                "xmlns:xsi" to "http://www.w3.org/2001/XMLSchema-instance",
                "xmlns" to "http://www.topografix.com/GPX/1/1",
                "xmlns:trailsense" to "https://kylecorry.com/Trail-Sense",
                "xsi:schemaLocation" to "http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd https://kylecorry.com/Trail-Sense https://kylecorry.com/Trail-Sense/trailsense.xsd"
            ), null, children
        )

        XMLConvert.write(gpx, stream, true)
    }

    fun toGPX(data: GPXData, creator: String): String {
        ByteArrayOutputStream().use { stream ->
            toGPX(data, creator, stream)
            return stream.toString()
        }
    }

    fun parse(gpx: InputStream): GPXData {
        val tree = try {
            XMLConvert.parse(gpx)
        } catch (e: Exception) {
            return GPXData(emptyList(), emptyList(), emptyList())
        }
        return parseXML(tree)
    }

    fun parse(gpx: String): GPXData {
        val tree = try {
            XMLConvert.parse(gpx)
        } catch (e: Exception) {
            return GPXData(emptyList(), emptyList(), emptyList())
        }
        return parseXML(tree)
    }

    private fun parseXML(root: XMLNode): GPXData {

        val waypoints = mutableListOf<GPXWaypoint>()
        val tracks = mutableListOf<GPXTrack>()
        val routes = mutableListOf<GPXRoute>()

        root.children.forEach {
            when (it.tag.lowercase()) {
                "wpt" -> parseWaypoint(it)?.let { waypoint -> waypoints.add(waypoint) }
                "trk" -> parseTrack(it)?.let { track -> tracks.add(track) }
                "rte" -> parseRoute(it)?.let { route -> routes.add(route) }
            }
        }

        return GPXData(waypoints, tracks, routes)
    }

    private fun parseTrack(node: XMLNode): GPXTrack? {
        if (node.tag.lowercase() != "trk") {
            return null
        }

        val segments = node.children.filter { it.tag.lowercase() == "trkseg" }.mapNotNull {
            parseSegment(it)
        }

        val name = node.children.firstOrNull { it.tag.lowercase() == "name" }?.text
        val comment = node.children.firstOrNull { it.tag.lowercase() == "cmt" }?.text
        val description = node.children.firstOrNull { it.tag.lowercase() == "desc" }?.text
        val id = node.children.firstOrNull { it.tag.lowercase() == "number" }?.text?.toLongCompat()
        val type = node.children.firstOrNull { it.tag.lowercase() == "type" }?.text
        val extensions = node.children.firstOrNull { it.tag.lowercase() == "extensions" }
        val colorHex = extensions?.children?.firstOrNull { it.tag.lowercase() == "color" }?.text
        val colorInt = tryOrDefault(null) { colorHex?.let { Color.parseColor(it) } }
        val lineStyle =
            extensions?.children?.firstOrNull { it.tag.lowercase() == "trailsense:linestyle" }?.text
        val group =
            extensions?.children?.firstOrNull { it.tag.lowercase() == "trailsense:group" }?.text

        return GPXTrack(name, type, id, description, comment, colorInt, lineStyle, group, segments)
    }

    private fun parseRoute(node: XMLNode): GPXRoute? {
        if (node.tag.lowercase() != "rte") {
            return null
        }

        val points = node.children.filter { it.tag.lowercase() == "rtept" }.mapNotNull {
            parseWaypoint(it)
        }

        val name = node.children.firstOrNull { it.tag.lowercase() == "name" }?.text
        val comment = node.children.firstOrNull { it.tag.lowercase() == "cmt" }?.text
        val description = node.children.firstOrNull { it.tag.lowercase() == "desc" }?.text
        val number =
            node.children.firstOrNull { it.tag.lowercase() == "number" }?.text?.toLongCompat()
        val type = node.children.firstOrNull { it.tag.lowercase() == "type" }?.text
        val source = node.children.firstOrNull { it.tag.lowercase() == "src" }?.text

        return GPXRoute(name, description, comment, source, number, type, points)
    }

    private fun parseSegment(node: XMLNode): GPXTrackSegment? {
        if (node.tag.lowercase() != "trkseg") {
            return null
        }

        return GPXTrackSegment(node.children.mapNotNull { parseWaypoint(it) })
    }

    private fun parseWaypoint(node: XMLNode): GPXWaypoint? {
        if (!validWaypointTags.contains(node.tag.lowercase())) {
            return null
        }
        val lat =
            if (node.attributes.containsKey("lat")) node.attributes["lat"]?.toDoubleCompat() else null
        val lon =
            if (node.attributes.containsKey("lon")) node.attributes["lon"]?.toDoubleCompat() else null
        val name = node.children.firstOrNull { it.tag.lowercase() == "name" }?.text
        val desc = node.children.firstOrNull { it.tag.lowercase() == "desc" }?.text
        val comment = node.children.firstOrNull { it.tag.lowercase() == "cmt" }?.text
        val type = node.children.firstOrNull { it.tag.lowercase() == "type" }?.text
        val time = node.children.firstOrNull { it.tag.lowercase() == "time" }?.text
        var ele = node.children.firstOrNull { it.tag.lowercase() == "ele" }?.text?.toFloatCompat()
        val symbol = node.children.firstOrNull { it.tag.lowercase() == "sym" }?.text
        val extensions = node.children.firstOrNull { it.tag.lowercase() == "extensions" }
        val group =
            extensions?.children?.firstOrNull { it.tag.lowercase() == "trailsense:group" }?.text
        val colorHex = extensions?.children?.firstOrNull { it.tag.lowercase() == "color" }?.text
        val colorInt = tryOrDefault(null) { colorHex?.let { Color.parseColor(it) } }

        if (ele == null && desc != null) {
            // Try to parse the elevation from the description
            val elevationRegex = Regex("ele=([0-9.]+)")
            val elevationMatch = elevationRegex.find(desc)
            if (elevationMatch != null) {
                ele = elevationMatch.groupValues[1].toFloatCompat()
            }
        }

        if (lat == null || lon == null) {
            return null
        }

        val instant = try {
            if (time == null) {
                null
            } else {
                Instant.parse(time)
            }
        } catch (e: Exception) {
            null
        }

        return GPXWaypoint(
            Coordinate(lat, lon),
            name,
            ele,
            type,
            desc,
            comment,
            instant,
            group,
            symbol,
            colorInt
        )
    }

    private fun toXML(waypoint: GPXWaypoint, tag: String): XMLNode {
        val children = mutableListOf<XMLNode>()
        if (waypoint.elevation != null) {
            children.add(XMLNode.text("ele", waypoint.elevation.toString()))
        }
        if (waypoint.time != null) {
            children.add(XMLNode.text("time", waypoint.time.toString()))
        }
        if (waypoint.name != null) {
            children.add(XMLNode.text("name", TextUtils.htmlEncode(waypoint.name)))
        }
        if (waypoint.type != null) {
            children.add(XMLNode.text("type", TextUtils.htmlEncode(waypoint.type)))
        }
        if (waypoint.description != null) {
            children.add(XMLNode.text("desc", TextUtils.htmlEncode(waypoint.description)))
        }
        if (waypoint.comment != null) {
            children.add(XMLNode.text("cmt", TextUtils.htmlEncode(waypoint.comment)))
        }
        if (waypoint.symbol != null) {
            children.add(XMLNode.text("sym", TextUtils.htmlEncode(waypoint.symbol)))
        }

        val extensions = mutableListOf<XMLNode>()

        if (waypoint.group != null) {
            extensions.add(XMLNode.text("trailsense:group", TextUtils.htmlEncode(waypoint.group)))
        }

        if (waypoint.color != null) {
            extensions.add(XMLNode.text("color", Colors.toHex(waypoint.color)))
        }

        if (extensions.isNotEmpty()) {
            children.add(XMLNode("extensions", mapOf(), null, extensions))
        }

        return XMLNode(
            tag, mapOf(
                "lat" to waypoint.coordinate.latitude.toString(),
                "lon" to waypoint.coordinate.longitude.toString()
            ), null, children
        )
    }

    private fun toXML(track: GPXTrack): XMLNode {
        val children = mutableListOf<XMLNode>()
        if (track.name != null) {
            children.add(XMLNode.text("name", TextUtils.htmlEncode(track.name)))
        }

        if (track.description != null) {
            children.add(XMLNode.text("desc", TextUtils.htmlEncode(track.description)))
        }

        if (track.comment != null) {
            children.add(XMLNode.text("cmt", TextUtils.htmlEncode(track.comment)))
        }

        if (track.id != null) {
            children.add(XMLNode.text("number", track.id.toString()))
        }

        if (track.type != null) {
            children.add(XMLNode.text("type", TextUtils.htmlEncode(track.type)))
        }

        val extensions = mutableListOf<XMLNode>()

        if (track.group != null) {
            extensions.add(XMLNode.text("trailsense:group", TextUtils.htmlEncode(track.group)))
        }

        if (track.color != null) {
            extensions.add(XMLNode.text("color", Colors.toHex(track.color)))
        }

        if (track.lineStyle != null) {
            extensions.add(XMLNode.text("trailsense:lineStyle", track.lineStyle.toString()))
        }

        if (extensions.isNotEmpty()) {
            children.add(XMLNode("extensions", mapOf(), null, extensions))
        }

        for (segment in track.segments) {
            children.add(toXML(segment))
        }

        return XMLNode("trk", emptyMap(), null, children)
    }

    private fun toXML(trackSegment: GPXTrackSegment): XMLNode {
        val children = mutableListOf<XMLNode>()
        for (waypoint in trackSegment.points) {
            children.add(toXML(waypoint, "trkpt"))
        }

        return XMLNode("trkseg", emptyMap(), null, children)
    }

    private fun toXML(route: GPXRoute): XMLNode {
        val children = mutableListOf<XMLNode>()
        if (route.name != null) {
            children.add(XMLNode.text("name", TextUtils.htmlEncode(route.name)))
        }

        if (route.comment != null) {
            children.add(XMLNode.text("cmt", TextUtils.htmlEncode(route.comment)))
        }

        if (route.description != null) {
            children.add(XMLNode.text("desc", TextUtils.htmlEncode(route.description)))
        }

        if (route.number != null) {
            children.add(XMLNode.text("number", route.number.toString()))
        }

        if (route.type != null) {
            children.add(XMLNode.text("type", TextUtils.htmlEncode(route.type)))
        }

        if (route.source != null) {
            children.add(XMLNode.text("src", TextUtils.htmlEncode(route.source)))
        }

        for (point in route.points) {
            children.add(toXML(point, "rtept"))
        }

        return XMLNode("rte", emptyMap(), null, children)
    }
}