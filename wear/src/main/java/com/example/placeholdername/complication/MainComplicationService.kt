package com.example.jitaicompanion.complication

import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.example.jitaicompanion.R
import java.util.Calendar

/**
 * Skeleton for complication data source that returns short text.
 */
class MainComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        if (type != ComplicationType.SHORT_TEXT) {
            return null
        }
        return createComplicationData(
            getString(R.string.complication_day_short_mon),
            getString(R.string.complication_day_full_mon)
        )
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
        return when (Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) {
            Calendar.SUNDAY -> createComplicationData(
                getString(R.string.complication_day_short_sun),
                getString(R.string.complication_day_full_sun)
            )
            Calendar.MONDAY -> createComplicationData(
                getString(R.string.complication_day_short_mon),
                getString(R.string.complication_day_full_mon)
            )
            Calendar.TUESDAY -> createComplicationData(
                getString(R.string.complication_day_short_tue),
                getString(R.string.complication_day_full_tue)
            )
            Calendar.WEDNESDAY -> createComplicationData(
                getString(R.string.complication_day_short_wed),
                getString(R.string.complication_day_full_wed)
            )
            Calendar.THURSDAY -> createComplicationData(
                getString(R.string.complication_day_short_thu),
                getString(R.string.complication_day_full_thu)
            )
            Calendar.FRIDAY -> createComplicationData(
                getString(R.string.complication_day_short_fri),
                getString(R.string.complication_day_full_fri)
            )
            Calendar.SATURDAY -> createComplicationData(
                getString(R.string.complication_day_short_sat),
                getString(R.string.complication_day_full_sat)
            )
            else -> throw IllegalArgumentException("too many days")
        }
    }

    private fun createComplicationData(text: String, contentDescription: String) =
        ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder(text).build(),
            contentDescription = PlainComplicationText.Builder(contentDescription).build()
        ).build()
}