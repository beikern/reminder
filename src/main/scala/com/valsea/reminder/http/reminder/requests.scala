package com.valsea.reminder.http.reminder

object requests {
  case class ReminderRequest(reminder_id: String, sent_at: Long, data: Option[Array[Byte]], attempt: Int = 1)
}
