package com.github.kr328.clash.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ConnectionMetadata(
    @SerialName("network") val network: String = "",
    @SerialName("type") val type: String = "",
    @SerialName("sourceIP") val sourceIP: String = "",
    @SerialName("destinationIP") val destinationIP: String = "",
    @SerialName("sourceGeoIP") val sourceGeoIP: List<String> = emptyList(),
    @SerialName("destinationGeoIP") val destinationGeoIP: List<String> = emptyList(),
    @SerialName("sourceIPASN") val sourceIPASN: String = "",
    @SerialName("destinationIPASN") val destinationIPASN: String = "",
    @SerialName("sourcePort") val sourcePort: String = "",
    @SerialName("destinationPort") val destinationPort: String = "",
    @SerialName("inboundIP") val inboundIP: String = "",
    @SerialName("inboundPort") val inboundPort: String = "",
    @SerialName("inboundName") val inboundName: String = "",
    @SerialName("inboundUser") val inboundUser: String = "",
    @SerialName("host") val host: String = "",
    @SerialName("sniffHost") val sniffHost: String = "",
    @SerialName("dnsMode") val dnsMode: String = "",
    @SerialName("uid") val uid: Int = 0,
    @SerialName("process") val process: String = "",
    @SerialName("processPath") val processPath: String = "",
    @SerialName("specialProxy") val specialProxy: String = "",
    @SerialName("specialRules") val specialRules: String = "",
    @SerialName("remoteDestination") val remoteDestination: String = "",
    @SerialName("dscp") val dscp: Int = 0,
)

@Serializable
data class Connection(
    @SerialName("id") val id: String,
    @SerialName("metadata") val metadata: ConnectionMetadata = ConnectionMetadata(),
    @SerialName("upload") val upload: Long = 0,
    @SerialName("download") val download: Long = 0,
    @SerialName("start") val start: String = "",
    @SerialName("chains") val chains: List<String> = emptyList(),
    @SerialName("providerChains") val providerChains: List<String> = emptyList(),
    @SerialName("rule") val rule: String = "",
    @SerialName("rulePayload") val rulePayload: String = "",
)

@Serializable
data class ConnectionSnapshot(
    @SerialName("downloadTotal") val downloadTotal: Long = 0,
    @SerialName("uploadTotal") val uploadTotal: Long = 0,
    @SerialName("connections") val connections: List<Connection> = emptyList(),
    @SerialName("memory") val memory: Long = 0,
)
