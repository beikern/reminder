package com.valsea.reminder.http.reminder

import akka.http.scaladsl.model.StatusCode

object httpErrors {
  sealed trait HttpErrors extends RuntimeException {
    def error: String
  }

  case class InvalidServiceEndpointError(invalidEndpoint: String, error: String) extends HttpErrors {
    override def getMessage: String = {
      s"Service endpoint $invalidEndpoint couldn't be parsed to a valid Uri. Details: $error"
    }
  }
  case class ReminderRequestFailedError(statusCode: StatusCode, endpoint: String, error: String) extends HttpErrors {
    override def getMessage: String = {
      s"Reminder request failed with status code $statusCode. Details: $error"
    }
  }
  case class ReminderRequestTimedOutError(timeoutInMins: Int) extends HttpErrors {
    val error = s"Reminder request timed out. Time elapsed in mins: $timeoutInMins"
    override def getMessage: String = {
      s"Reminder request timed out. Time elapsed in mins: $timeoutInMins"
    }
  }

}
