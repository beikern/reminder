package com.valsea.reminder.aggregates.service

import com.valsea.reminder.aggregates.service.entities._

object state {
  final case class State(service: Service, isRegistered: Boolean, reminders: Set[ReminderId]) {
    def registerService(service: Service): State = State(service = service, isRegistered = true, reminders.empty)
    def updateService(service: Service): State = this.copy(service = service)
    def hasReminder(id: ReminderId): Boolean = this.reminders.contains(id)
    def addReminder(id: ReminderId): State = this.copy(reminders = this.reminders + id)
  }

  object State {
    val empty = State(service = Service("", 0), isRegistered = false, reminders = Set.empty)
  }
}
