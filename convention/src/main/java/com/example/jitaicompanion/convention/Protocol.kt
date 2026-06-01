package com.example.jitaicompanion.convention

object Protocol {
    const val PATH_INTERVENTION = "/intervention/trigger"
    const val PATH_INTERVENTION_RESPONSE = "/intervention/response"
    const val PATH_WATCH_DATA = "/watch/data"
    const val PATH_PING = "/ping"
    const val PATH_PONG = "/pong"
    const val PATH_GAME_RESULT = "/game/result"
    const val PATH_PHONE_TASK = "/phone/task"
    const val PATH_EXIT = "/exit"
    const val KEY_INTERVENTION = "intervention"
    const val HTTP_PORT = 8080

    const val PATH_EXPERIMENT = "/experiment"
    const val PATH_LOGS = "/logs"
    const val PATH_EXPERIMENT_CONTROL = "/experiment/control"
    const val PATH_TRIGGER_FIRE = "/trigger/fire"
    const val PATH_EVENTS_WS = "/events"
    const val ETAG_HEADER = "X-Experiment-Etag"
}
