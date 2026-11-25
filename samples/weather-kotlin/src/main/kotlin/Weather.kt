package org.acme

import io.quarkiverse.mcp.server.Tool
import io.quarkiverse.mcp.server.ToolArg
import io.quarkus.qute.Qute
import io.quarkus.rest.client.reactive.Url
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient
import org.eclipse.microprofile.rest.client.inject.RestClient
import org.jboss.resteasy.reactive.RestPath

class Weather {
    @RestClient
    lateinit var weatherClient: WeatherClient

    @Tool(description = "Get weather alerts for a US state.")
    fun getAlerts(@ToolArg(description = "Two-letter US state code (e.g. CA, NY)") state: String): String {
        return formatAlerts(weatherClient.getAlerts(state))
    }

    @Tool(description = "Get weather forecast for a location.")
    fun getForecast(
        @ToolArg(description = "Latitude of the location") latitude: Double,
        @ToolArg(description = "Longitude of the location") longitude: Double
    ): String {
        val points = weatherClient.getPoints(latitude, longitude)
        val url = Qute.fmt("{p.properties.forecast}", mapOf("p" to points))
        return formatForecast(weatherClient.getForecast(url))
    }

    private fun formatForecast(forecast: Forecast): String {
        return forecast.properties.periods.joinToString("\n---\n") { period ->
            Qute.fmt(
                """
                Temperature: {p.temperature}°{p.temperatureUnit}
                Wind: {p.windSpeed} {p.windDirection}
                Forecast: {p.detailedForecast}
                """.trimIndent(),
                mapOf("p" to period)
            )
        }
    }

    private fun formatAlerts(alerts: Alerts): String {
        return alerts.features.joinToString("\n---\n") { feature ->
            Qute.fmt(
                """
                Event: {p.event}
                Area: {p.areaDesc}
                Severity: {p.severity}
                Description: {p.description}
                Instructions: {p.instruction}
                """.trimIndent(),
                mapOf("p" to feature.properties)
            )
        }
    }

    @RegisterRestClient(baseUri = "https://api.weather.gov")
    interface WeatherClient {
        @GET
        @Path("/alerts/active/area/{state}")
        fun getAlerts(@RestPath state: String): Alerts

        @GET
        @Path("/points/{latitude},{longitude}")
        fun getPoints(
            @RestPath latitude: Double,
            @RestPath longitude: Double
        ): Map<String, Any>

        @GET
        @Path("/")
        fun getForecast(@Url url: String): Forecast
    }

    data class Properties(
        val id: String,
        val areaDesc: String,
        val event: String,
        val severity: String,
        val description: String,
        val instruction: String
    )

    data class Feature(
        val id: String,
        val type: String,
        val geometry: Any,
        val properties: Properties
    )

    data class Alerts(
        val context: List<String>,
        val type: String,
        val features: List<Feature>,
        val title: String,
        val updated: String
    )

    data class Period(
        val name: String,
        val temperature: Int,
        val temperatureUnit: String,
        val windSpeed: String,
        val windDirection: String,
        val detailedForecast: String
    )

    data class ForecastProperties(
        val periods: List<Period>
    )

    data class Forecast(
        val properties: ForecastProperties
    )
}