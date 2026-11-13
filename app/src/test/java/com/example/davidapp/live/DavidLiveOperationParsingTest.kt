package com.example.davidapp.live

import com.example.utils.VoiceParserHelper
import org.junit.Assert.*
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class DavidLiveOperationParsingTest {
    @Test fun onlyExplicitCommandsAuthorizeWriting() {
        assertEquals(DavidLiveOperationCommand.CONFIRM, DavidLiveOperationCommand.parse("Подтверждаю запись."))
        assertEquals(DavidLiveOperationCommand.CONFIRM, DavidLiveOperationCommand.parse("Да"))
        assertEquals(DavidLiveOperationCommand.CANCEL, DavidLiveOperationCommand.parse("Нет"))
        assertEquals(DavidLiveOperationCommand.CANCEL, DavidLiveOperationCommand.parse("Отмени запись!"))
        listOf("хорошо", "не подтверждаю запись", "он сказал подтверждаю запись", "записать?")
            .forEach { assertNull(DavidLiveOperationCommand.parse(it)) }
    }

    @Test fun dayBeforeYesterdayDoesNotMatchYesterday() {
        val expected = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -2) }
        val operation = VoiceParserHelper.parseHeuristically(
            "Позавчера потратил 450 рублей на продукты", listOf("Продукты"), listOf("Доходы")
        ).single()
        assertEquals(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(expected.time), operation.date)
    }
}
