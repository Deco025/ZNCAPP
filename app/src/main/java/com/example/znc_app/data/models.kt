package com.example.znc_app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents the "data" object within the main command packet.
 * Based on the final "Data Dictionary" in PROTOCOL_REFACTOR_PLAN.md.
 * Note: The C++ side will ignore `network_delay`, but we send it for completeness.
 */
@Serializable
data class CommandData(
    @SerialName("crossroad_turns")
    val crossroadTurns: List<Int>,

    @SerialName("b_star")
    val bStar: Int,
    
    @SerialName("a_star")
    val aStar: Int,
    
    @SerialName("global_speed")
    val globalSpeed: Int,
    
    @SerialName("turn_speed")
    val turnSpeed: Int,
    
    @SerialName("image_mode")
    val imageMode: Int,

    @SerialName("action_button_hold")
    val actionButtonHold: Int,

    @SerialName("network_delay")
    val networkDelay: Int
)

/**
 * Represents the top-level JSON object for updating parameters.
 */
@Serializable
data class UpdateParamsCommand(
    val cmd: String = "update_params",
    val data: CommandData
)

/**
 * Represents the top-level JSON object for requesting status.
 */
@Serializable
data class GetStatusCommand(
    val cmd: String = "get_status"
)