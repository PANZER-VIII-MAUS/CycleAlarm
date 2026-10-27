package com.cyclealarm.app.core

import com.cyclealarm.app.data.AlarmEntity
import com.cyclealarm.app.data.DateAlarmEntity
import com.cyclealarm.app.data.HolidayEntity

object DeleteUndoPolicy {
    fun alarmForRestore(snapshot: AlarmEntity): AlarmEntity =
        snapshot.copy()

    fun dateAlarmForRestore(snapshot: DateAlarmEntity): DateAlarmEntity =
        snapshot.copy()

    fun holidayForRestore(snapshot: HolidayEntity): HolidayEntity =
        snapshot.copy()
}
